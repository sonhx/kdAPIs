package com.surveys;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.security.MessageDigest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SurveySyncScheduler {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${slink.api-key}")
    private String slinkApiKey;

    // Helper method to generate globally unique IDs for shared blocks/questions
    private String generateUniqueId(String surveyId, String originalId) {
        if (originalId == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest((surveyId + "_" + originalId).getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 24); // MongoDB ObjectId is 24 chars
        } catch (Exception e) {
            return originalId;
        }
    }

    @Scheduled(cron = "0 0 5 * * *")
    public void syncSurveys() {
        System.out.println("Starting Slink Survey Sync...");
        try {
            int page = 1;
            int limit = 200;
            boolean hasMore = true;
            
            String cleanApiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "";
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10000);
            requestFactory.setReadTimeout(10000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            while (hasMore) {
                String url = "https://gw.aisoftech.vn/ptit/slink/internal/khao-sat/page?page=" + page + "&limit=" + limit;
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
                String responseBody = response.getBody() != null ? new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8) : "{}";
                
                JSONObject jRes = new JSONObject(responseBody);
                if (jRes.has("success") && jRes.getBoolean("success")) {
                    JSONObject data = jRes.getJSONObject("data");
                    JSONArray items = data.getJSONArray("result");
                    
                    if (items.length() == 0) {
                        hasMore = false;
                        break;
                    }
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject survey = items.getJSONObject(i);
                        updateSurveyMetadata(survey);
                    }
                    
                    int total = data.getInt("total");
                    if (page * limit >= total) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                } else {
                    hasMore = false;
                }
            }
            System.out.println("Slink Survey Sync Completed successfully.");
        } catch (Exception e) {
            System.out.println("Error in syncSurveys: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateSurveyMetadata(JSONObject survey) {
        try {
            String id = survey.getString("_id");
            String parentId = survey.optString("khaoSatChaId", null);
            String loai = survey.optString("loai", "");
            String tieuDe = survey.optString("tieuDe", "");
            String moTa = survey.optString("moTa", null);
            boolean kichHoat = survey.optBoolean("kichHoat", true);
            String courseSemesterCode = survey.optString("maHocPhanHocKy", null);
            int maxResponses = survey.optInt("soLuotTraLoiToiDa", 1);
            boolean coCamKet = survey.optBoolean("coCamKet", false);
            
            String createdBy = null;
            if (survey.has("thongTinNguoiTao") && !survey.isNull("thongTinNguoiTao")) {
                createdBy = survey.getJSONObject("thongTinNguoiTao").optString("_id", null);
            }
            
            Timestamp createdAt = parseDate(survey.optString("createdAt"));
            Timestamp updatedAt = parseDate(survey.optString("updatedAt"));

            final String finalId = id;
            final String finalParentId = parentId;
            final String finalLoai = loai;
            final String finalTieuDe = tieuDe;
            final String finalMoTa = moTa;
            final boolean finalKichHoat = kichHoat;
            final String finalCourseSemesterCode = courseSemesterCode;
            final int finalMaxResponses = maxResponses;
            final boolean finalCoCamKet = coCamKet;
            final String finalCreatedBy = createdBy;
            final Timestamp finalCreatedAt = createdAt;
            final Timestamp finalUpdatedAt = updatedAt;

            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                boolean exists = false;
                try {
                    jdbcTemplate.queryForObject("SELECT 1 FROM surveys WHERE id = ?", Integer.class, finalId);
                    exists = true;
                } catch (EmptyResultDataAccessException e) {}

                if (exists) {
                    String updateSql = "UPDATE surveys SET parent_id=?, survey_type=?, title=?, description=?, is_active=?, course_semester_code=?, max_responses=?, has_commitment=?, updated_at=? WHERE id=?";
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement(updateSql);
                        ps.setString(1, finalParentId);
                        ps.setNString(2, finalLoai);
                        ps.setNString(3, finalTieuDe);
                        ps.setNString(4, finalMoTa);
                        ps.setBoolean(5, finalKichHoat);
                        ps.setString(6, finalCourseSemesterCode);
                        ps.setInt(7, finalMaxResponses);
                        ps.setBoolean(8, finalCoCamKet);
                        ps.setTimestamp(9, finalUpdatedAt);
                        ps.setString(10, finalId);
                        return ps;
                    });
                } else {
                    String insertSql = "INSERT INTO surveys (id, parent_id, survey_type, title, description, is_active, course_semester_code, max_responses, has_commitment, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement(insertSql);
                        ps.setString(1, finalId);
                        ps.setString(2, finalParentId);
                        ps.setNString(3, finalLoai);
                        ps.setNString(4, finalTieuDe);
                        ps.setNString(5, finalMoTa);
                        ps.setBoolean(6, finalKichHoat);
                        ps.setString(7, finalCourseSemesterCode);
                        ps.setInt(8, finalMaxResponses);
                        ps.setBoolean(9, finalCoCamKet);
                        ps.setString(10, finalCreatedBy);
                        ps.setTimestamp(11, finalCreatedAt);
                        ps.setTimestamp(12, finalUpdatedAt);
                        return ps;
                    });
                    
                    // Insert blocks
                    if (survey.has("danhSachKhoi")) {
                        JSONArray blocks = survey.getJSONArray("danhSachKhoi");
                        for (int i = 0; i < blocks.length(); i++) {
                            insertBlock(finalId, blocks.getJSONObject(i));
                        }
                    }
                }
            });
            
            // Sync campaigns for this survey (outside transaction)
            try {
                updateSurveyCampaigns(id);
            } catch (Exception ex) {
                System.out.println("Error syncing campaigns for survey " + id + ": " + ex.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Error updating survey metadata " + survey.optString("_id") + ": " + e.getMessage());
            e.printStackTrace();
            if (e instanceof org.springframework.transaction.CannotCreateTransactionException || 
                e instanceof org.springframework.jdbc.CannotGetJdbcConnectionException ||
                (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("closed"))) {
                throw new RuntimeException("Fatal database connection error during survey sync", e);
            }
        }
    }

    private void insertBlock(String surveyId, JSONObject block) {
        String originalId = block.getString("_id");
        String id = generateUniqueId(surveyId, originalId); // Use unique ID
        
        String tieuDe = block.optString("tieuDe", "");
        String moTa = block.optString("moTa", null);
        boolean show = block.optBoolean("show", true);
        boolean isDanhGia = block.optBoolean("isDanhGiaChuanDauRa", false);
        
        String configType = null;
        Integer configMin = null;
        Integer configMax = null;
        
        if (block.has("cauHinh") && !block.isNull("cauHinh")) {
            JSONObject cauHinh = block.getJSONObject("cauHinh");
            configType = cauHinh.optString("loai", null);
            if (cauHinh.has("gioiHanDuoiTuyenTinh")) configMin = cauHinh.getInt("gioiHanDuoiTuyenTinh");
            if (cauHinh.has("gioiHanTrenTuyenTinh")) configMax = cauHinh.getInt("gioiHanTrenTuyenTinh");
        }
        
        // Upsert logic for safety
        boolean exists = false;
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM survey_blocks WHERE id = ?", Integer.class, id);
            exists = true;
        } catch (EmptyResultDataAccessException e) {}
        final String finalId = id;
        final String finalSurveyId = surveyId;
        final String finalTieuDe = tieuDe;
        final String finalMoTa = moTa;
        final boolean finalShow = show;
        final boolean finalIsDanhGia = isDanhGia;
        final String finalConfigType = configType;
        final Integer finalConfigMin = configMin;
        final Integer finalConfigMax = configMax;

        if (!exists) {
            String sql = "INSERT INTO survey_blocks (id, survey_id, title, description, is_visible, is_clo_evaluation, config_type, config_min, config_max) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, finalId);
                ps.setString(2, finalSurveyId);
                ps.setNString(3, finalTieuDe);
                ps.setNString(4, finalMoTa);
                ps.setBoolean(5, finalShow);
                ps.setBoolean(6, finalIsDanhGia);
                ps.setString(7, finalConfigType);
                if (finalConfigMin != null) ps.setInt(8, finalConfigMin); else ps.setNull(8, java.sql.Types.INTEGER);
                if (finalConfigMax != null) ps.setInt(9, finalConfigMax); else ps.setNull(9, java.sql.Types.INTEGER);
                return ps;
            });
        }
        
        if (block.has("danhSachCauHoi")) {
            JSONArray questions = block.getJSONArray("danhSachCauHoi");
            for (int i = 0; i < questions.length(); i++) {
                insertQuestion(surveyId, id, questions.getJSONObject(i)); // Pass surveyId for uniqueness
            }
        }
    }

    private void insertQuestion(String surveyId, String blockId, JSONObject q) {
        String originalId = q.getString("_id");
        String id = generateUniqueId(surveyId, originalId); // Use unique ID
        
        String loai = q.optString("loai", "");
        boolean batBuoc = q.optBoolean("batBuoc", false);
        String noiDung = q.optString("noiDungCauHoi", "");
        boolean cauTraLoiKhac = q.optBoolean("cauTraLoiKhac", false);
        boolean isLayTuDanhMuc = q.optBoolean("isLayTuDanhMuc", false);
        
        Integer minLinear = q.has("gioiHanDuoiTuyenTinh") && !q.isNull("gioiHanDuoiTuyenTinh") ? q.getInt("gioiHanDuoiTuyenTinh") : null;
        Integer maxLinear = q.has("gioiHanTrenTuyenTinh") && !q.isNull("gioiHanTrenTuyenTinh") ? q.getInt("gioiHanTrenTuyenTinh") : null;
        
        double diemMacDinh = q.optDouble("diemMacDinh", 0);
        double diem = q.optDouble("diem", 0);
        
        String createdBy = null;
        if (q.has("thongTinNguoiTao") && !q.isNull("thongTinNguoiTao")) {
            createdBy = q.getJSONObject("thongTinNguoiTao").optString("id", null);
        }
        
        boolean exists = false;
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM survey_questions WHERE id = ?", Integer.class, id);
            exists = true;
        } catch (EmptyResultDataAccessException e) {}
        final String finalId = id;
        final String finalBlockId = blockId;
        final String finalLoai = loai;
        final boolean finalBatBuoc = batBuoc;
        final String finalNoiDung = noiDung;
        final boolean finalCauTraLoiKhac = cauTraLoiKhac;
        final boolean finalIsLayTuDanhMuc = isLayTuDanhMuc;
        final Integer finalMinLinear = minLinear;
        final Integer finalMaxLinear = maxLinear;
        final double finalDiemMacDinh = diemMacDinh;
        final double finalDiem = diem;
        final String finalCreatedBy = createdBy;

        if (!exists) {
            String sql = "INSERT INTO survey_questions (id, block_id, question_type, is_required, content, has_other_option, is_from_category, linear_min, linear_max, default_score, score, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, finalId);
                ps.setString(2, finalBlockId);
                ps.setString(3, finalLoai);
                ps.setBoolean(4, finalBatBuoc);
                ps.setNString(5, finalNoiDung);
                ps.setBoolean(6, finalCauTraLoiKhac);
                ps.setBoolean(7, finalIsLayTuDanhMuc);
                if (finalMinLinear != null) ps.setInt(8, finalMinLinear); else ps.setNull(8, java.sql.Types.INTEGER);
                if (finalMaxLinear != null) ps.setInt(9, finalMaxLinear); else ps.setNull(9, java.sql.Types.INTEGER);
                ps.setDouble(10, finalDiemMacDinh);
                ps.setDouble(11, finalDiem);
                ps.setString(12, finalCreatedBy);
                return ps;
            });
            
            if (q.has("luaChonHang") && !q.isNull("luaChonHang")) {
                JSONArray rows = q.getJSONArray("luaChonHang");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    String rowId = generateUniqueId(surveyId, r.getString("_id"));
                    String text = r.getString("noiDung");
                    final String finalRowId = rowId;
                    final String finalRowText = text;
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement("INSERT INTO question_matrix_rows (id, question_id, content) VALUES (?, ?, ?)");
                        ps.setString(1, finalRowId);
                        ps.setString(2, finalId);
                        ps.setNString(3, finalRowText);
                        return ps;
                    });
                }
            }
            if (q.has("luaChonCot") && !q.isNull("luaChonCot")) {
                JSONArray cols = q.getJSONArray("luaChonCot");
                for (int i = 0; i < cols.length(); i++) {
                    JSONObject c = cols.getJSONObject(i);
                    String colId = generateUniqueId(surveyId, c.getString("_id"));
                    String text = c.getString("noiDung");
                    final String finalColId = colId;
                    final String finalColText = text;
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement("INSERT INTO question_matrix_cols (id, question_id, content) VALUES (?, ?, ?)");
                        ps.setString(1, finalColId);
                        ps.setString(2, finalId);
                        ps.setNString(3, finalColText);
                        return ps;
                    });
                }
            }
            if (q.has("luaChon") && !q.isNull("luaChon")) {
                JSONArray opts = q.getJSONArray("luaChon");
                for (int i = 0; i < opts.length(); i++) {
                    JSONObject o = opts.getJSONObject(i);
                    String optId = generateUniqueId(surveyId, o.getString("_id"));
                    String text = o.getString("noiDung");
                    final String finalOptId = optId;
                    final String finalOptText = text;
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement("INSERT INTO question_options (id, question_id, content) VALUES (?, ?, ?)");
                        ps.setString(1, finalOptId);
                        ps.setString(2, finalId);
                        ps.setNString(3, finalOptText);
                        return ps;
                    });
                }
            }
        }
    }

    @Scheduled(fixedDelay = 5*60*60*1000, initialDelay = 5*60*60*1000)
    public void syncOngoingSurveyDetails() {
        System.out.println("Starting Slink Survey Details Sync...");
        try {
            // Find active surveys to sync
            List<String> activeSurveyIds = jdbcTemplate.queryForList("SELECT id FROM surveys WHERE is_active = 1", String.class);
            
            String cleanApiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "";
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10000);
            requestFactory.setReadTimeout(10000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            for (String surveyId : activeSurveyIds) {
                int page = 1;
                int limit = 100;
                boolean hasMore = true;
                
                while (hasMore) {
                    String url = "https://gw.aisoftech.vn/ptit/slink/internal/cau-tra-loi-khao-sat/khao-sat/" + surveyId + "/page?page=" + page + "&limit=" + limit;
                    try {
                        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
                        String responseBody = response.getBody() != null ? new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8) : "{}";
                        JSONObject jRes = new JSONObject(responseBody);
                        if (jRes.has("success") && jRes.getBoolean("success")) {
                            JSONObject data = jRes.getJSONObject("data");
                            JSONArray items = data.getJSONArray("result");
                            
                            if (items.length() == 0) {
                                hasMore = false;
                                break;
                            }
                            
                            for (int i = 0; i < items.length(); i++) {
                                updateSurveyResponse(items.getJSONObject(i)); //TODO: Uncomment this line when the method is implemented
                            }
                            
                            int total = data.getInt("total");
                            if (page * limit >= total) {
                                hasMore = false;
                            } else {
                                page++;
                            }
                        } else {
                            hasMore = false;
                        }
                    } catch (Exception ex) {
                        System.out.println("Error syncing details for survey " + surveyId + ": " + ex.getMessage());
                        hasMore = false;
                        if (ex.getMessage() != null && ex.getMessage().contains("Fatal database connection error") || 
                            ex instanceof org.springframework.transaction.CannotCreateTransactionException || 
                            ex instanceof org.springframework.jdbc.CannotGetJdbcConnectionException ||
                            (ex.getCause() != null && ex.getCause().getMessage() != null && ex.getCause().getMessage().contains("closed"))) {
                            throw ex;
                        }
                    }
                }
            }
            System.out.println("Slink Survey Details Sync Completed successfully.");
        } catch (Exception e) {
            System.out.println("Error in syncOngoingSurveyDetails: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateSurveyResponse(JSONObject res) {
        try {
            String id = res.getString("_id");
            String surveyId = res.getString("idKhaoSat");
            String hoTen = res.optString("hoTen", null);
            String userCode = res.optString("userCode", null);
            boolean answered = res.optBoolean("answered", false);
            String role = res.optString("vaiTro", null);
            String classCode = res.optString("maLop", null);
            
            Timestamp startedAt = parseDate(res.optString("startedAt"));
            Timestamp createdAt = parseDate(res.optString("createdAt"));
            Timestamp updatedAt = parseDate(res.optString("updatedAt"));

            final String finalId = id;
            final String finalSurveyId = surveyId;
            final String finalUserCode = userCode;
            final String finalHoTen = hoTen;
            final String finalRole = role;
            final String finalClassCode = classCode;
            final boolean finalAnswered = answered;
            final Timestamp finalStartedAt = startedAt;
            final Timestamp finalCreatedAt = createdAt;
            final Timestamp finalUpdatedAt = updatedAt;

            Timestamp dbUpdatedAt = null;
            boolean exists = false;
            try {
                dbUpdatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM survey_responses WHERE id = ?", Timestamp.class, finalId);
                exists = true;
            } catch (EmptyResultDataAccessException e) {
                // Not found
            }

            final Timestamp finalDbUpdatedAt = dbUpdatedAt;
            final boolean finalExists = exists;

            // Wrap ALL write operations in a single TransactionTemplate so they share ONE
            // connection that is returned to the pool when the lambda exits, preventing the
            // HikariCP connection leak that was triggered on the scheduling-1 thread.
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                if (finalExists) {
                    if (finalDbUpdatedAt == null || (finalUpdatedAt != null && finalUpdatedAt.after(finalDbUpdatedAt))) {
                        String updateSql = "UPDATE survey_responses SET is_answered=?, updated_at=? WHERE id=?";
                        jdbcTemplate.update(connection -> {
                            java.sql.PreparedStatement ps = connection.prepareStatement(updateSql);
                            ps.setBoolean(1, finalAnswered);
                            ps.setTimestamp(2, finalUpdatedAt);
                            ps.setString(3, finalId);
                            return ps;
                        });

                        jdbcTemplate.update("DELETE FROM survey_response_answers WHERE response_id = ?", finalId);
                        insertAnswers(finalSurveyId, finalId, res.optJSONArray("danhSachTraLoi"));
                    }
                } else {
                    String insertSql = "INSERT INTO survey_responses (id, survey_id, user_code, full_name, role, class_code, is_answered, started_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement(insertSql);
                        ps.setString(1, finalId);
                        ps.setString(2, finalSurveyId);
                        ps.setString(3, finalUserCode);
                        ps.setNString(4, finalHoTen);
                        ps.setNString(5, finalRole);
                        ps.setString(6, finalClassCode);
                        ps.setBoolean(7, finalAnswered);
                        ps.setTimestamp(8, finalStartedAt);
                        ps.setTimestamp(9, finalCreatedAt);
                        ps.setTimestamp(10, finalUpdatedAt);
                        return ps;
                    });

                    insertAnswers(finalSurveyId, finalId, res.optJSONArray("danhSachTraLoi"));
                }
            });
        } catch (Exception e) {
            System.out.println("Error updating survey response " + res.optString("_id") + ": " + e.getMessage());
            e.printStackTrace();
            if (e instanceof org.springframework.transaction.CannotCreateTransactionException || 
                e instanceof org.springframework.jdbc.CannotGetJdbcConnectionException ||
                (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("closed"))) {
                throw new RuntimeException("Fatal database connection error during survey sync", e);
            }
        }
    }

    private void insertAnswers(String surveyId, String responseId, JSONArray answers) {
        if (answers == null || answers.length() == 0) return;
        
        List<Object[]> batchParams = new java.util.ArrayList<>();
        for (int i = 0; i < answers.length(); i++) {
            JSONObject ans = answers.getJSONObject(i);
            String ansId = ans.optString("_id", java.util.UUID.randomUUID().toString());
            String originalQId = ans.getString("idCauHoi");
            String qId = generateUniqueId(surveyId, originalQId);
            String traLoiKhac = ans.optString("traLoiKhac", null);
            
            JSONArray listLuaChon = ans.optJSONArray("listLuaChon");
            JSONArray listLuaChonBang = ans.optJSONArray("listLuaChonBang");
            
            String choicesStr = null;
            if (listLuaChon != null && listLuaChon.length() > 0) {
                choicesStr = listLuaChon.toString();
            } else if (listLuaChonBang != null && listLuaChonBang.length() > 0) {
                choicesStr = listLuaChonBang.toString();
            }
            
            batchParams.add(new Object[]{ansId, responseId, qId, choicesStr, traLoiKhac});
        }

        String sql = "INSERT INTO survey_response_answers (id, response_id, question_id, choices, other_answer) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, batchParams);
    }

    private void updateSurveyCampaigns(String surveyId) {
        try {
            String cleanApiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "";
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10000);
            requestFactory.setReadTimeout(10000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            int page = 1;
            int limit = 100;
            boolean hasMore = true;

            while (hasMore) {
                String url = "https://gw.aisoftech.vn/ptit/slink/internal/dot-khao-sat/khao-sat/" + surveyId + "/page?page=" + page + "&limit=" + limit;
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
                String responseBody = response.getBody() != null ? new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8) : "{}";
                
                JSONObject jRes = new JSONObject(responseBody);
                if (jRes.has("success") && jRes.getBoolean("success")) {
                    JSONObject data = jRes.getJSONObject("data");
                    JSONArray items = data.getJSONArray("result");
                    
                    if (items.length() == 0) {
                        hasMore = false;
                        break;
                    }
                    
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject campaign = items.getJSONObject(i);
                        saveOrUpdateCampaign(surveyId, campaign);
                    }
                    
                    int total = data.getInt("total");
                    if (page * limit >= total) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                } else {
                    hasMore = false;
                }
            }
        } catch (Exception e) {
            System.out.println("Error in updateSurveyCampaigns for survey " + surveyId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveOrUpdateCampaign(String surveyId, JSONObject campaign) {
        try {
            String id = campaign.getString("_id");
            String title = campaign.optString("ten", null);
            String semesterCode = campaign.optString("maHocKy", null);
            String semesterName = campaign.optString("tenHocKy", null);
            
            Timestamp startTime = parseDate(campaign.optString("thoiGianBatDau"));
            Timestamp endTime = parseDate(campaign.optString("thoiGianKetThuc"));
            
            int maxResponses = campaign.optInt("soLanTraLoiToiDa", 1);
            boolean isActive = campaign.optBoolean("kichHoat", false);
            
            Timestamp createdAt = parseDate(campaign.optString("createdAt"));
            Timestamp updatedAt = parseDate(campaign.optString("updatedAt"));
            
            boolean exists = false;
            try {
                jdbcTemplate.queryForObject("SELECT 1 FROM survey_campaigns WHERE id = ?", Integer.class, id);
                exists = true;
            } catch (EmptyResultDataAccessException e) {}
            
            final String finalId = id;
            final String finalSurveyId = surveyId;
            final String finalTitle = title;
            final String finalSemesterCode = semesterCode;
            final String finalSemesterName = semesterName;
            final Timestamp finalStartTime = startTime;
            final Timestamp finalEndTime = endTime;
            final int finalMaxResponses = maxResponses;
            final boolean finalIsActive = isActive;
            final Timestamp finalCreatedAt = createdAt;
            final Timestamp finalUpdatedAt = updatedAt;

            if (exists) {
                String updateSql = "UPDATE survey_campaigns SET survey_id=?, title=?, semester_code=?, semester_name=?, start_time=?, end_time=?, max_responses=?, is_active=?, updated_at=? WHERE id=?";
                jdbcTemplate.update(connection -> {
                    java.sql.PreparedStatement ps = connection.prepareStatement(updateSql);
                    ps.setString(1, finalSurveyId);
                    ps.setNString(2, finalTitle);
                    ps.setString(3, finalSemesterCode);
                    ps.setNString(4, finalSemesterName);
                    ps.setTimestamp(5, finalStartTime);
                    ps.setTimestamp(6, finalEndTime);
                    ps.setInt(7, finalMaxResponses);
                    ps.setBoolean(8, finalIsActive);
                    ps.setTimestamp(9, finalUpdatedAt);
                    ps.setString(10, finalId);
                    return ps;
                });
            } else {
                String insertSql = "INSERT INTO survey_campaigns (id, survey_id, title, semester_code, semester_name, start_time, end_time, max_responses, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                jdbcTemplate.update(connection -> {
                    java.sql.PreparedStatement ps = connection.prepareStatement(insertSql);
                    ps.setString(1, finalId);
                    ps.setString(2, finalSurveyId);
                    ps.setNString(3, finalTitle);
                    ps.setString(4, finalSemesterCode);
                    ps.setNString(5, finalSemesterName);
                    ps.setTimestamp(6, finalStartTime);
                    ps.setTimestamp(7, finalEndTime);
                    ps.setInt(8, finalMaxResponses);
                    ps.setBoolean(9, finalIsActive);
                    ps.setTimestamp(10, finalCreatedAt);
                    ps.setTimestamp(11, finalUpdatedAt);
                    return ps;
                });
            }
        } catch (Exception e) {
            System.out.println("Error saving/updating campaign: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Timestamp parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("null")) return null;
        try {
            Instant instant = Instant.parse(dateStr);
            return Timestamp.from(instant);
        } catch (Exception e) {
            return null;
        }
    }
}
