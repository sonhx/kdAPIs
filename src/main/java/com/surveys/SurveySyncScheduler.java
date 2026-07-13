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

@Service
public class SurveySyncScheduler {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            while (hasMore) {
                String url = "https://gw.aisoftech.vn/ptit/slink/internal/khao-sat/page?page=" + page + "&limit=" + limit;
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                
                JSONObject jRes = new JSONObject(response.getBody());
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

            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM surveys WHERE id = ?", Integer.class, id);

            if (count != null && count > 0) {
                String updateSql = "UPDATE surveys SET parent_id=?, survey_type=?, title=?, description=?, is_active=?, course_semester_code=?, max_responses=?, has_commitment=?, updated_at=? WHERE id=?";
                jdbcTemplate.update(updateSql, parentId, loai, tieuDe, moTa, kichHoat, courseSemesterCode, maxResponses, coCamKet, updatedAt, id);
            } else {
                String insertSql = "INSERT INTO surveys (id, parent_id, survey_type, title, description, is_active, course_semester_code, max_responses, has_commitment, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                jdbcTemplate.update(insertSql, id, parentId, loai, tieuDe, moTa, kichHoat, courseSemesterCode, maxResponses, coCamKet, createdBy, createdAt, updatedAt);
                
                // Insert blocks
                if (survey.has("danhSachKhoi")) {
                    JSONArray blocks = survey.getJSONArray("danhSachKhoi");
                    for (int i = 0; i < blocks.length(); i++) {
                        insertBlock(id, blocks.getJSONObject(i));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error updating survey metadata " + survey.optString("_id") + ": " + e.getMessage());
            e.printStackTrace();
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
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey_blocks WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            String sql = "INSERT INTO survey_blocks (id, survey_id, title, description, is_visible, is_clo_evaluation, config_type, config_min, config_max) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, id, surveyId, tieuDe, moTa, show, isDanhGia, configType, configMin, configMax);
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
        
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey_questions WHERE id = ?", Integer.class, id);
        if (count == null || count == 0) {
            String sql = "INSERT INTO survey_questions (id, block_id, question_type, is_required, content, has_other_option, is_from_category, linear_min, linear_max, default_score, score, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, id, blockId, loai, batBuoc, noiDung, cauTraLoiKhac, isLayTuDanhMuc, minLinear, maxLinear, diemMacDinh, diem, createdBy);
            
            if (q.has("luaChonHang") && !q.isNull("luaChonHang")) {
                JSONArray rows = q.getJSONArray("luaChonHang");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    String rowId = generateUniqueId(surveyId, r.getString("_id"));
                    jdbcTemplate.update("INSERT INTO question_matrix_rows (id, question_id, content) VALUES (?, ?, ?)", rowId, id, r.getString("noiDung"));
                }
            }
            if (q.has("luaChonCot") && !q.isNull("luaChonCot")) {
                JSONArray cols = q.getJSONArray("luaChonCot");
                for (int i = 0; i < cols.length(); i++) {
                    JSONObject c = cols.getJSONObject(i);
                    String colId = generateUniqueId(surveyId, c.getString("_id"));
                    jdbcTemplate.update("INSERT INTO question_matrix_cols (id, question_id, content) VALUES (?, ?, ?)", colId, id, c.getString("noiDung"));
                }
            }
            if (q.has("luaChon") && !q.isNull("luaChon")) {
                JSONArray opts = q.getJSONArray("luaChon");
                for (int i = 0; i < opts.length(); i++) {
                    JSONObject o = opts.getJSONObject(i);
                    String optId = generateUniqueId(surveyId, o.getString("_id"));
                    jdbcTemplate.update("INSERT INTO question_options (id, question_id, content) VALUES (?, ?, ?)", optId, id, o.getString("noiDung"));
                }
            }
        }
    }

    @Scheduled(fixedDelay = 900000) // 15 mins
    public void syncOngoingSurveyDetails() {
        System.out.println("Starting Slink Survey Details Sync...");
        try {
            // Find active surveys to sync
            List<String> activeSurveyIds = jdbcTemplate.queryForList("SELECT id FROM surveys WHERE is_active = 1", String.class);
            
            String cleanApiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "";
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            for (String surveyId : activeSurveyIds) {
                int page = 1;
                int limit = 20;
                boolean hasMore = true;
                
                while (hasMore) {
                    String url = "https://gw.aisoftech.vn/ptit/slink/internal/cau-tra-loi-khao-sat/khao-sat/" + surveyId + "/page?page=" + page + "&limit=" + limit;
                    try {
                        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                        JSONObject jRes = new JSONObject(response.getBody());
                        if (jRes.has("success") && jRes.getBoolean("success")) {
                            JSONObject data = jRes.getJSONObject("data");
                            JSONArray items = data.getJSONArray("result");
                            
                            if (items.length() == 0) {
                                hasMore = false;
                                break;
                            }
                            
                            for (int i = 0; i < items.length(); i++) {
                                updateSurveyResponse(items.getJSONObject(i));
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

            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey_responses WHERE id = ?", Integer.class, id);
            
            if (count != null && count > 0) {
                // Check if we need to update
                Timestamp dbUpdatedAt = null;
                try {
                    dbUpdatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM survey_responses WHERE id = ?", Timestamp.class, id);
                } catch(EmptyResultDataAccessException e){}
                
                if (dbUpdatedAt == null || (updatedAt != null && updatedAt.after(dbUpdatedAt))) {
                    String updateSql = "UPDATE survey_responses SET is_answered=?, updated_at=? WHERE id=?";
                    jdbcTemplate.update(updateSql, answered, updatedAt, id);
                    
                    // We can just delete answers and re-insert them if it was updated
                    jdbcTemplate.update("DELETE FROM survey_response_answers WHERE response_id = ?", id);
                    insertAnswers(surveyId, id, res.optJSONArray("danhSachTraLoi"));
                }
            } else {
                String insertSql = "INSERT INTO survey_responses (id, survey_id, user_code, full_name, role, class_code, is_answered, started_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                jdbcTemplate.update(insertSql, id, surveyId, userCode, hoTen, role, classCode, answered, startedAt, createdAt, updatedAt);
                
                insertAnswers(surveyId, id, res.optJSONArray("danhSachTraLoi"));
            }
        } catch (Exception e) {
            System.out.println("Error updating survey response " + res.optString("_id") + ": " + e.getMessage());
        }
    }

    private void insertAnswers(String surveyId, String responseId, JSONArray answers) {
        if (answers == null) return;
        for (int i = 0; i < answers.length(); i++) {
            JSONObject ans = answers.getJSONObject(i);
            String ansId = ans.optString("_id", java.util.UUID.randomUUID().toString());
            String originalQId = ans.getString("idCauHoi");
            String qId = generateUniqueId(surveyId, originalQId); // Transform using same hashing logic!
            String traLoiKhac = ans.optString("traLoiKhac", null);
            
            JSONArray listLuaChon = ans.optJSONArray("listLuaChon");
            JSONArray listLuaChonBang = ans.optJSONArray("listLuaChonBang");
            
            String choicesStr = null;
            if (listLuaChon != null && listLuaChon.length() > 0) {
                choicesStr = listLuaChon.toString();
            } else if (listLuaChonBang != null && listLuaChonBang.length() > 0) {
                choicesStr = listLuaChonBang.toString();
            }
            
            String sql = "INSERT INTO survey_response_answers (id, response_id, question_id, choices, other_answer) VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, ansId, responseId, qId, choicesStr, traLoiKhac);
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
