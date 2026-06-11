package com.file;

import java.io.File;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.config.Config;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/u")
public class UploadFiles {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/{session_id}/{kd_id}/{doituong_kd}/{loai_tl}/{sub_tl}")
    public String uploadFiles(
            @PathVariable("session_id") String sessionId,
            @PathVariable("kd_id") int kdId,
            @PathVariable("doituong_kd") String doituongKd,
            @PathVariable("loai_tl") String loaiTl,
            @PathVariable("sub_tl") String subTl,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ghi_chu", required = false) String ghiChu) {

        struct_session sst = sessionService.getSessionInfo(sessionId);
        if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

        try {
            String ten = file.getOriginalFilename();
            String path = fPath(kdId, doituongKd, loaiTl, subTl);
            String fdir = path.replaceFirst(Config.homePath, Config.homeDir);
            
            File targetFile = new File(fdir + File.separator + ten);
            File dir = targetFile.getParentFile();
            if (!dir.exists()) dir.mkdirs();

            file.transferTo(targetFile);

            String tenbang = tenbang(loaiTl, subTl);
            registerDoc(path, tenbang, ten, ghiChu, sst.UserID, kdId, doituongKd);

            return "{\"code\":200, \"description\":\"Files uploaded successfully\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    private String fPath(int kdId, String doituongKd, String loaiTl, String subTl) {
        String path = Config.homePath + "/" + kdId + "/";
        if ("cs".equals(doituongKd)) {
            path += "csgd/" + loaiTl;
        } else {
            path += "ctdt/" + doituongKd + "/" + loaiTl;
        }
        if (subTl != null && !subTl.isEmpty()) {
            path += "/" + subTl;
        }
        return path;
    }

    private String tenbang(String loaiTl, String subTl) {
        switch (loaiTl) {
            case "tdg": return "TBL_Tudanhgia";
            case "mc": return "TBL_Minhchung";
            case "bm": return "TBL_Bieumau";
            case "vb": return "vb_den".equalsIgnoreCase(subTl) ? "TBL_Vanbanden" : "TBL_Vanbandi";
            case "kq": return "TBL_Ketqua";
            case "bcrs": return "TBL_BCRasoat";
            default: return "";
        }
    }

    private void registerDoc(String path, String tenbang, String ten, String ghiChu, int userId, int kdId, String doituongKd) {
        String fullPath = path + "/" + ten;
        // WARNING: table name is dynamic. Ensure validation is handled or tables are pre-defined.
        if (tenbang.isEmpty()) return;
        String sql = "insert into " + tenbang + " (ten, ghi_chu, path, Createdtime, CreatedBy, kd_id, doituong_kd) values (?,?,?,GETDATE(),?,?,?)";
        jdbcTemplate.update(sql, ten, ghiChu, fullPath, userId, kdId, doituongKd);
    }
}
