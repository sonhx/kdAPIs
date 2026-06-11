package com.vanban;

import org.json.JSONArray;
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
@RequestMapping("/vb")
public class VBService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private VBExtend vbExtend;

    @PostMapping("/create_tc")
    public String createToChucApi(@RequestBody String sReq) {
        System.out.println("-------createToChucApi:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String ten_tc = jin.getString("ten_tc");
            vbExtend.createTochuc(ten_tc);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/list_tc")
    public String listToChucApi(@RequestBody String sReq) {
        System.out.println("-------listToChucApi:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONArray jsaTochuc = vbExtend.listToChuc();
            jout.put("code", 200);
            jout.put("list_tc", jsaTochuc);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/list_vb")
    public String listVBApi(@RequestBody String sReq) {
        System.out.println("-------listVBApi:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            String type = jin.getString("type");

            JSONArray jsaVBs = vbExtend.listVB(kd_id, doituong_kd, type);
            jout.put("list_vb", jsaVBs);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/edit")
    public String editVBApi(@RequestBody String sReq) {
        System.out.println("-------editVBApi:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String ghi_chu = jin.getString("ghi_chu");
            int tc_id = jin.has("id_tochuc") ? jin.getInt("id_tochuc") : -1;
            int vb_id = jin.getInt("vb_id");
            String so_vb = jin.has("so_vb") ? jin.getString("so_vb") : "";
            String ngay_bh = jin.has("ngay_bh") ? jin.getString("ngay_bh") : null;
            String ngay_gui = jin.has("ngay_gui") ? jin.getString("ngay_gui") : null;

            vbExtend.updateVb(vb_id, so_vb, ngay_bh, ngay_gui, tc_id, ghi_chu, sst.UserID);
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/edit_vbden")
    public String editVBdenApi(@RequestBody String sReq) {
        return editVBApi(sReq);
    }

    @PostMapping("/delete_vbden")
    public String deleteVbdenApi(@RequestBody String sReq) {
        return deleteVbApi(sReq);
    }

    @PostMapping("/delete_vb")
    public String deleteVbApi(@RequestBody String sReq) {
        System.out.println("-------deleteVbApi:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("id");
            vbExtend.deleteVb(id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/edit_vbdi")
    public String editVBdiApi(@RequestBody String sReq) {
        return editVBApi(sReq);
    }

    @PostMapping("/delete_vbdi")
    public String deleteVbdiApi(@RequestBody String sReq) {
        return deleteVbApi(sReq);
    }
}
