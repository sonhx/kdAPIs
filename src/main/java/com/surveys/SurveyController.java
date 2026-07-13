package com.surveys;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.session.SessionService;
import com.session.struct_session;
import com.user.UserService;

@RestController
@RequestMapping("/surveys")
public class SurveyController {

    @Autowired
    private SessionService sessionService;

    @Value("${slink.api-key}")
    private String slinkApiKey;

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
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // Forward the Slink response
            return response.getBody();

        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"Lỗi khi gọi API Slink: " + e.getMessage() + "\"}";
        }
    }
}
