package com.surveys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.session.SessionService;
import com.session.struct_session;
import com.surveys.service.SurveyT107Service;
import com.user.UserService;

@CrossOrigin(originPatterns = "*", maxAge = 3600, allowCredentials = "true")
@RestController
@RequestMapping("/surveys")
public class SurveyController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SurveyT107Service surveyT107Service;

    @Value("${slink.api-key}")
    private String slinkApiKey;

    @PostMapping(value = "/upload-t107", produces = MediaType.APPLICATION_JSON_VALUE)
    public String uploadT107SurveyExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period_id", required = false) String periodId,
            @RequestParam(value = "survey_title", required = false) String surveyTitle,
            @RequestParam(value = "session_id", required = false) String sessionId) {
        JSONObject jout = new JSONObject();
        try {
            if (file == null || file.isEmpty()) {
                jout.put("code", 400);
                jout.put("description", "Vui lòng chọn tệp Excel khảo sát (.xlsx, .xls).");
                return jout.toString();
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) originalFilename = "survey_t107.xlsx";
            
            System.out.println("Received file: " + originalFilename + ", size: " + file.getSize() + " bytes");

            JSONObject res = surveyT107Service.processT107Excel(
                file.getInputStream(),
                originalFilename,
                periodId,
                "Admin"
            );
            return res.toString();
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý tệp khảo sát Excel: " + e.getMessage());
            return jout.toString();
        }
    }

    @GetMapping(value = "/download-t107-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> downloadT107Template() {
        byte[] excelBytes = surveyT107Service.generateT107ExcelTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "Mau_Khao_Sat_CTDT_T107.xlsx");
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/slink/list")
    public String getSlinkSurveys(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jReq = new JSONObject(sReq);
            String sessionId = jReq.has("session_id") ? jReq.getString("session_id") : null;
            if (sessionId == null) {
                return "{\"code\":700, \"description\":\"Thiếu session_id\"}";
            }

            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Người sử dụng chưa đăng nhập\"}";
            }

            int page = jReq.has("page") ? jReq.getInt("page") : 1;
            int limit = jReq.has("limit") ? jReq.getInt("limit") : 20;

            JSONObject tokens = UserService.slinkTokensMap.get(sessionId);
            if (tokens == null || !tokens.has("access_token")) {
                return "{\"code\":401, \"description\":\"Không tìm thấy access token Slink cho session này\"}";
            }

            String accessToken = tokens.getString("access_token");
            String cleanApiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "";

            RestTemplate restTemplate = new RestTemplate();
            String url = "https://gw.aisoftech.vn/ptit/slink/internal/khao-sat/page?page=" + page + "&limit=" + limit;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            String responseBody = response.getBody() != null ? new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8) : "{}";

            // Forward the Slink response
            return responseBody;

        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"Lỗi khi gọi API Slink: " + e.getMessage() + "\"}";
        }
    }

    @GetMapping("/stats")
    public String getStats() {
        try {
            Integer totalSurveys = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM surveys", Integer.class);
            Integer totalCampaigns = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey_campaigns", Integer.class);
            
            String sqlOngoing = "SELECT COUNT(*) FROM survey_campaigns WHERE is_active = 1 " +
                                "AND (start_time IS NULL OR start_time <= GETDATE()) " +
                                "AND (end_time IS NULL OR end_time >= GETDATE())";
            Integer ongoingCampaigns = jdbcTemplate.queryForObject(sqlOngoing, Integer.class);
            
            Integer totalResponses = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey_responses", Integer.class);
            
            JSONObject stats = new JSONObject();
            stats.put("totalSurveys", totalSurveys != null ? totalSurveys : 0);
            stats.put("totalCampaigns", totalCampaigns != null ? totalCampaigns : 0);
            stats.put("ongoingCampaigns", ongoingCampaigns != null ? ongoingCampaigns : 0);
            stats.put("totalResponses", totalResponses != null ? totalResponses : 0);
            
            return stats.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @GetMapping("/campaigns")
    public String getCampaigns() {
        try {
            String sql = "SELECT id, survey_id, title, semester_code, semester_name, " +
                         "CONVERT(VARCHAR(24), start_time, 126) as start_time, " +
                         "CONVERT(VARCHAR(24), end_time, 126) as end_time, " +
                         "max_responses, is_active, " +
                         "CONVERT(VARCHAR(24), created_at, 126) as created_at, " +
                         "CONVERT(VARCHAR(24), updated_at, 126) as updated_at " +
                         "FROM survey_campaigns " +
                         "ORDER BY created_at DESC";
            
            List<Map<String, Object>> campaigns = jdbcTemplate.queryForList(sql);
            JSONArray arr = new JSONArray(campaigns);
            return arr.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @GetMapping("/list")
    public String getSurveyList() {
        try {
            String sql = "SELECT s.id, " +
                         "s.title as name, " +
                         "s.survey_type as target, " +
                         "s.is_active, " +
                         "CONVERT(VARCHAR(10), c.min_start, 120) as startDate, " +
                         "CONVERT(VARCHAR(10), c.max_end, 120) as endDate, " +
                         "ISNULL(r.response_count, 0) as responses " +
                         "FROM surveys s " +
                         "LEFT JOIN (" +
                         "    SELECT survey_id, MIN(start_time) as min_start, MAX(end_time) as max_end " +
                         "    FROM survey_campaigns " +
                         "    GROUP BY survey_id" +
                         ") c ON s.id = c.survey_id " +
                         "LEFT JOIN (" +
                         "    SELECT survey_id, COUNT(*) as response_count " +
                         "    FROM survey_responses " +
                         "    GROUP BY survey_id" +
                         ") r ON s.id = r.survey_id " +
                         "ORDER BY s.created_at DESC";
            
            System.out.println("Executing SQL: " + sql); // Debugging line to print the SQL query   
            
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
            
            for (Map<String, Object> map : list) {
                Number responsesNum = (Number) map.get("responses");
                int responses = responsesNum != null ? responsesNum.intValue() : 0;
                int targetCount = responses > 800 ? responses + 200 : 1000;
                map.put("targetCount", targetCount);
                
                Boolean isActive = (Boolean) map.get("is_active");
                map.put("status", (isActive != null && isActive) ? "open" : "closed");
                
                if (map.get("startDate") == null) {
                    map.put("startDate", "N/A");
                }
                if (map.get("endDate") == null) {
                    map.put("endDate", "N/A");
                }
                
                // Add an empty array for questions to prevent crashes
                map.put("questions", new ArrayList<Object>());
            }
            
            JSONArray arr = new JSONArray(list);
            return arr.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @GetMapping("/{id}")
    public String getSurveyDetail(@PathVariable("id") String id) {
        try {
            Map<String, Object> survey = jdbcTemplate.queryForMap(
                "SELECT id, title as name, survey_type as target, is_active, " +
                "CONVERT(VARCHAR(10), created_at, 120) as startDate, " +
                "CONVERT(VARCHAR(10), updated_at, 120) as endDate " +
                "FROM surveys WHERE id = ?", id
            );
            
            Boolean isActive = (Boolean) survey.get("is_active");
            survey.put("status", (isActive != null && isActive) ? "open" : "closed");
            
            Integer responses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM survey_responses WHERE survey_id = ?", Integer.class, id
            );
            int resCount = responses != null ? responses : 0;
            survey.put("responses", resCount);
            survey.put("targetCount", resCount > 800 ? resCount + 200 : 1000);
            
            String qSql = "SELECT q.id, q.content as text, q.question_type as type, q.linear_min as linearMin, q.linear_max as linearMax, " +
                          "b.id as blockId, b.title as blockTitle, b.description as blockDescription " +
                          "FROM survey_questions q " +
                          "JOIN survey_blocks b ON q.block_id = b.id " +
                          "WHERE b.survey_id = ?";
            List<Map<String, Object>> questions = jdbcTemplate.queryForList(qSql, id);
            
            for (Map<String, Object> q : questions) {
                String qId = (String) q.get("id");
                String qType = (String) q.get("type");
                
                if ("Text".equalsIgnoreCase(qType) || "Paragraph".equalsIgnoreCase(qType)) {
                    q.put("type", "open-ended");
                    q.put("totalComments", 0);
                    q.put("sampleComments", new ArrayList<String>());
                } else if ("NumericRange".equalsIgnoreCase(qType)) {
                    q.put("type", "likert");
                    q.put("likertScale", 5);
                    q.put("likertLabels", Arrays.asList("Rất kém", "Kém", "Trung bình", "Tốt", "Rất tốt"));
                    q.put("likertDistribution", Arrays.asList(0, 0, 0, 0, 0));
                    q.put("likertAverage", 0.0);
                } else if ("GridSingleChoice".equalsIgnoreCase(qType) || "GridMultipleChoice".equalsIgnoreCase(qType)) {
                    q.put("type", "grid");
                    List<Map<String, Object>> gridRows = jdbcTemplate.queryForList(
                        "SELECT id, content as text FROM question_matrix_rows WHERE question_id = ?", qId
                    );
                    q.put("rows", gridRows);
                } else {
                    q.put("type", "multiple-choice");
                    List<Map<String, Object>> opts = jdbcTemplate.queryForList(
                        "SELECT content as label, 0 as count FROM question_options WHERE question_id = ?", qId
                    );
                    q.put("options", opts);
                }
            }
            
            survey.put("questions", questions);
            
            JSONObject obj = new JSONObject(survey);
            return obj.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
