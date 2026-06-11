package com.ketqua;

import java.io.File;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.config.Config;
import com.file.UploadBase64;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/kq")
public class KqService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private KqExtend kqExtend;

    @PostMapping("/list")
    public String listKQApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int kd_scope = jin.optInt("loai_hinh", -1);
            JSONArray jsaKQs = kqExtend.listKq(kd_scope);
            jout.put("list_kq", jsaKQs);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/edit")
    public String editKqApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            String ghi_chu = jin.optString("ghi_chu", "");
            String qd_so = jin.optString("qd_so", null);
            String nq_so = jin.optString("nq_so", null);
            String gcn_so = jin.optString("gcn_so", null);
            String gcn_thoihan = jin.getString("gcn_thoihan");
            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            int kq_id = jin.getInt("kq_id");

            kqExtend.updateKq(kq_id, kd_id, doituong_kd, qd_so, nq_so, gcn_so, gcn_thoihan, ghi_chu, sst.UserID);

            String path = Config.homePath + "/" + kd_id + "/" + (doituong_kd.equals("cs") ? "csgd" : "ctdt/" + doituong_kd) + "/kq/" + kq_id;
            String fdir = path.replaceFirst(Config.homePath, Config.homeDir);
            File dir = new File(fdir);
            if (!dir.exists()) dir.mkdirs();

            handleFile(jin, "nq_file", fdir, path, kq_id, "nq", sst.UserID);
            handleFile(jin, "qd_file", fdir, path, kq_id, "qd", sst.UserID);
            handleFile(jin, "gcn_file", fdir, path, kq_id, "gcn", sst.UserID);

            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    private void handleFile(JSONObject jin, String key, String fdir, String path, int kq_id, String loai, int userId) throws Exception {
        if (jin.has(key)) {
            JSONObject joFile = jin.getJSONObject(key);
            String filename = joFile.getString("filename");
            String sFile = fdir + File.separator + filename;
            File file = new File(sFile);
            if (!file.exists()) file.createNewFile();
            UploadBase64.b64Decode(joFile.getString("base64"), sFile);
            kqExtend.updateKqDoc_short(kq_id, path, filename, loai, userId);
        }
    }

    @PostMapping("/delete")
    public String deleteKqApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            int id = jin.getInt("id");
            kqExtend.deleteKq(id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }
}
