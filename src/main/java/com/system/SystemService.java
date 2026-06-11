package com.system;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/system")
public class SystemService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SystemExtend systemExtend;

    @PostMapping("/purgeallseason")
    public String purgeAllSeason(@RequestBody String sReq) {
        System.out.println("-------purgeAllSeason:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jsonobjReq.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            if (sst.UserType != 2) return "{\"code\":500, \"description\":\"Không đủ quyền\"}";

            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/addnationcodefile")
    public String addNationCodeFile(@RequestBody String sReq) {
        System.out.println("----------addNationCodeFile:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jsonobjReq.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            systemExtend.addNationCodes(jsonobjReq.getJSONArray("data"));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Processing error\"}";
        }
        return jout.toString();
    }
}
