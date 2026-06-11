package com.tdg;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/tdg")
public class TdgService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private TdgExtend tdgExtend;

    @PostMapping("/list")
    public String listTdgApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            int kd_id = jin.getInt("kd_id");
            int kd_scope = jin.getInt("loai_hinh");
            int status = jin.getInt("status");
            String doituong_kd = jin.getString("doituong_kd");

            JSONArray jsaTdgs = tdgExtend.listTdg(kd_id, kd_scope, status, doituong_kd);
            jout.put("list_tdg", jsaTdgs);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/edit")
    public String editTdgApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            String ghi_chu = jin.getString("ghi_chu");
            int id = jin.getInt("id");
            String so_vb = jin.optString("so_vb", "");
            String ngay_bh = jin.optString("ngay_bh", null);
            
            tdgExtend.updateTdg(id, so_vb, ngay_bh, ghi_chu, sst.UserID);
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/delete")
    public String deleteTdgApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            int id = jin.getInt("id");
            tdgExtend.deleteTdg(id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }
}
