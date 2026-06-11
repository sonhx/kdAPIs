package com.ct;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/ct")
public class CtService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private CtExtend ctExtend;

    @PostMapping("/list")
    public String listCtApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            if (sessionService.getSessionInfo(jin.getString("session_id")) == null) return "{\"code\":700}";

            JSONArray jsaCts = ctExtend.listNganhDT();
            jout.put("list_ct", jsaCts);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800}";
        }
        return jout.toString();
    }

    @PostMapping("/create")
    public String createChuongtrinhApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700}";

            ctExtend.createNganhDT(jin.getString("ten"), jin.getString("abbr"), sst.UserID);
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
        return jout.toString();
    }

    @PostMapping("/edit")
    public String editCtApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700}";

            ctExtend.updateCt(jin.getInt("id"), jin.getString("ten"), jin.optString("abbr", ""), sst.UserID);
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
        return jout.toString();
    }

    @PostMapping("/delete")
    public String deleteCtApi(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            if (sessionService.getSessionInfo(jin.getString("session_id")) == null) return "{\"code\":700}";
            ctExtend.deleteCt(jin.getInt("id"));
            return "{\"code\":200}";
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
    }
}
