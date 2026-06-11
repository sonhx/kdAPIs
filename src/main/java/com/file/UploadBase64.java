package com.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.config.Config;
import com.minhchung.MCExtend;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/b64")
public class UploadBase64 {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MCExtend mcExtend;

    public static int b64Decode(String b64, String path) {
        try {
            byte[] data = Base64.getDecoder().decode(b64);
            try (OutputStream stream = new FileOutputStream(path)) {
                stream.write(data);
            }
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @PostMapping("/u")
    public String uploadFiles_b64Api(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            int kdId = jin.getInt("kd_id");
            String doituongKd = jin.getString("doituong_kd");
            String loaiTl = jin.getString("loai_tl");
            String subTl = jin.optString("sub_tl", null);
            String soVb = jin.optString("so_vb", "");
            String ngayBh = jin.optString("ngay_bh", null);
            String ghiChu = jin.optString("ghi_chu", "");
            String ngayGui = jin.optString("ngay_gui", null);
            int idTochuc = jin.optInt("id_tochuc", 0);
            String type = jin.optString("type", null);
            int status = jin.optInt("status", -1);

            String tenbang = tenbang(loaiTl);
            String path = fPath(kdId, doituongKd, loaiTl, subTl);
            String fdir = path.replaceFirst(Config.homePath, Config.homeDir);

            File dir = new File(fdir);
            if (!dir.exists()) dir.mkdirs();

            JSONArray files = jin.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject joFile = files.getJSONObject(i);
                String filename = joFile.getString("filename");
                String sFile = fdir + File.separator + filename;
                File file = new File(sFile);
                if (!file.exists()) file.createNewFile();
                
                b64Decode(joFile.getString("base64"), sFile);
                
                if ("TBL_Vanban".equalsIgnoreCase(tenbang)) {
                    registerDoc_vb(path, tenbang, soVb, ngayBh, filename, ghiChu, type, sst.UserID, kdId, doituongKd, ngayGui, idTochuc);
                } else {
                    registerDoc(path, tenbang, soVb, ngayBh, filename, ghiChu, sst.UserID, kdId, doituongKd, status);
                }
            }

            return "{\"code\":200, \"description\":\"Success\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/u/kq")
    public String uploadKQ_b64Api(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            int kdId = jin.getInt("kd_id");
            int kdScope = jin.getInt("loai_hinh");
            String doituongKd = jin.getString("doituong_kd");
            String loaiTl = jin.getString("loai_tl");
            String subTl = jin.optString("sub_tl", null);
            String ghiChu = jin.optString("ghi_chu", "");
            
            String qdSo = jin.optString("qd_so", null);
            String nqSo = jin.optString("nq_so", null);
            String gcnSo = jin.optString("gcn_so", null);
            String gcnThoihan = jin.getString("gcn_thoihan");

            String tenbang = tenbang(loaiTl);
            String path = fPath(kdId, doituongKd, loaiTl, subTl);
            int kqId = registerKq(tenbang, ghiChu, sst.UserID, kdId, doituongKd, kdScope);
            
            String fullPath = path + "/" + kqId;
            String fdir = fullPath.replaceFirst(Config.homePath, Config.homeDir);
            File dir = new File(fdir);
            if (!dir.exists()) dir.mkdirs();

            saveAndRegisterKqFile(jin.getJSONObject("nq_file"), fdir, fullPath, kqId, nqSo, null, "nq", ghiChu, sst.UserID);
            saveAndRegisterKqFile(jin.getJSONObject("qd_file"), fdir, fullPath, kqId, qdSo, null, "qd", ghiChu, sst.UserID);
            saveAndRegisterKqFile(jin.getJSONObject("gcn_file"), fdir, fullPath, kqId, gcnSo, gcnThoihan, "gcn", ghiChu, sst.UserID);

            return "{\"code\":200, \"description\":\"Success\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    private void saveAndRegisterKqFile(JSONObject joFile, String fdir, String path, int kqId, String so, String thoihan, String loai, String ghiChu, int userId) throws Exception {
        String filename = joFile.getString("filename");
        String sFile = fdir + File.separator + filename;
        File file = new File(sFile);
        if (!file.exists()) file.createNewFile();
        b64Decode(joFile.getString("base64"), sFile);
        registerKqDoc(kqId, path, filename, so, thoihan, loai, ghiChu, userId);
    }

    @PostMapping("/u/mc")
    public String uploadMinhchung_b64Api(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            int kdId = jin.getInt("kd_id");
            String doituongKd = jin.getString("doituong_kd");
            int status = jin.getInt("status");

            String path = fPath(kdId, doituongKd, "mc", null);
            JSONArray jsaFiles = jin.getJSONArray("files");
            for (int i = 0; i < jsaFiles.length(); i++) {
                JSONObject joFile = jsaFiles.getJSONObject(i);
                String filename = joFile.getString("filename");
                
                String ma_mc = "";
                String regex = "^(H[0-9]{1,2}\\.[0-9]{2}\\.[0-9]{2}\\.[0-9]{2})(.*)";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(filename);
                if (matcher.matches()) ma_mc = matcher.group(1).trim();
                else continue;

                String fdir = path.replaceFirst(Config.homePath, Config.homeDir);
                String ma_mc_path = ma_mc.replaceAll("\\.", "/");
                File dir = new File(fdir + "/" + ma_mc_path);
                if (!dir.exists()) dir.mkdirs();

                String sFile = dir.getAbsolutePath() + File.separator + filename;
                b64Decode(joFile.getString("base64"), sFile);

                if (mcExtend.isMaMCUnique(ma_mc, kdId, doituongKd, status)) {
                    registerMinhchung_ed(path, ma_mc, filename, sst.UserID, kdId, doituongKd);
                }
            }
            return "{\"code\":200, \"description\":\"Success\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String fPath(int kdId, String doituongKd, String loaiTl, String subTl) {
        String path = Config.homePath + "/" + kdId + "/";
        if ("cs".equals(doituongKd)) path += "csgd/" + loaiTl;
        else path += "ctdt/" + doituongKd + "/" + loaiTl;
        if (subTl != null && !subTl.isEmpty()) path += "/" + subTl;
        return path;
    }

    public static String tenbang(String loaiTl) {
        switch (loaiTl) {
            case "tdg": return "TBL_Tudanhgia";
            case "mc": return "TBL_Minhchung";
            case "bm": return "TBL_Bieumau";
            case "vb": return "TBL_Vanban";
            case "bb": return "TBL_Bienban";
            case "kq": return "TBL_Ketqua";
            case "bcrs": return "TBL_BCRasoat";
            case "ctcl": return "TBL_CaitienCL";
            default: return "";
        }
    }

    private void registerDoc_vb(String path, String tenbang, String soVb, String ngayBh, String ten, String ghiChu, String type, int userId, int kdId, String doituongKd, String ngayGui, int idTochuc) {
        String fullPath = path + "/" + ten;
        String sql = "insert into " + tenbang + " (ten, so_vb, ghi_chu, type, path, Createdtime, CreatedBy, kd_id, doituong_kd, ngay_bh, ngay_gui, id_tochuc) values (?, ?, ?, ?, ?, GETDATE(), ?, ?, ?, " 
                   + (ngayBh == null ? "NULL" : "CONVERT(DATETIME, ?, 102)") + ", " 
                   + (ngayGui == null ? "NULL" : "CONVERT(DATETIME, ?, 102)") + ", " 
                   + (idTochuc > 0 ? "?" : "NULL") + ")";
        
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(ten); params.add(soVb); params.add(ghiChu); params.add(type); params.add(fullPath); params.add(userId); params.add(kdId); params.add(doituongKd);
        if (ngayBh != null) params.add(ngayBh);
        if (ngayGui != null) params.add(ngayGui);
        if (idTochuc > 0) params.add(idTochuc);
        
        jdbcTemplate.update(sql, params.toArray());
    }

    private void registerDoc(String path, String tenbang, String soVb, String ngayBh, String ten, String ghiChu, int userId, int kdId, String doituongKd, int status) {
        String fullPath = path + "/" + ten;
        String sql = "insert into " + tenbang + " (ten, ghi_chu, path, so_vb, Createdtime, CreatedBy, kd_id, doituong_kd, status, ngay_bh) values (?, ?, ?, ?, GETDATE(), ?, ?, ?, ?, " 
                   + (ngayBh == null ? "NULL" : "CONVERT(DATETIME, ?, 102)") + ")";
        
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(ten); params.add(ghiChu); params.add(fullPath); params.add(soVb); params.add(userId); params.add(kdId); params.add(doituongKd); params.add(status);
        if (ngayBh != null) params.add(ngayBh);
        
        jdbcTemplate.update(sql, params.toArray());
    }

    private int registerKq(String tenbang, String ghiChu, int userId, int kdId, String doituongKd, int scope) {
        String sql = "insert into " + tenbang + " (ghi_chu, Createdtime, CreatedBy, kd_id, doituong_kd, scope) values (?, GETDATE(), ?, ?, ?, ?)";
        jdbcTemplate.update(sql, ghiChu, userId, kdId, doituongKd, scope);
        return jdbcTemplate.queryForObject("select max(ID) from " + tenbang, Integer.class);
    }

    private void registerKqDoc(int kqId, String path, String ten, String so, String thoihan, String loai, String ghiChu, int userId) {
        String fullPath = path + "/" + ten;
        String sql = "insert into TBL_Ketqua_doc (kq_id, ten, so, loai, ghi_chu, path, Createdtime, CreatedBy, ExpiryDate) values (?, ?, ?, ?, ?, ?, GETDATE(), ?, " 
                   + (thoihan == null ? "NULL" : "CONVERT(DATETIME, ?, 102)") + ")";
        
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(kqId); params.add(ten); params.add(so); params.add(loai); params.add(ghiChu); params.add(fullPath); params.add(userId);
        if (thoihan != null) params.add(thoihan);
        
        jdbcTemplate.update(sql, params.toArray());
    }

    private void registerMinhchung_ed(String path, String maMc, String filename, int userId, int kdId, String doituongKd) {
        String maMcPath = maMc.replaceAll("\\.", "/");
        String fullPath = path + "/" + maMcPath + "/" + filename;
        String sql = "update TBL_Minhchung set ma_mc = ?, ten_file = ?, path = ? where kd_id = ? and doituong_kd = ? and ma_mc = ?";
        jdbcTemplate.update(sql, maMc, filename, fullPath, kdId, doituongKd, maMc);
    }
}
