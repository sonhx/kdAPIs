package com.tdg;

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
@RequestMapping("/tdg_upload")
public class UploadTdg {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/{session_id}")
    public String uploadTdg(
            @PathVariable("session_id") String session_id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("kd_id") int kdId,
            @RequestParam("doituong_kd") String doituongKd,
            @RequestParam(value = "ghi_chu", required = false) String ghiChu) {

        try {
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "Unauthorized";

            String filename = file.getOriginalFilename();
            String path = Config.homeDir + "/tdg";
            File targetFile = new File(path + File.separator + filename);
            if (!targetFile.getParentFile().exists()) targetFile.getParentFile().mkdirs();
            file.transferTo(targetFile);

            registerTdg(filename, ghiChu, sst.UserID, kdId, doituongKd);
            return "Files uploaded successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "Upload failed: " + e.getMessage();
        }
    }

    private void registerTdg(String tenTdg, String ghiChu, int userId, int kdId, String doituongKd) {
        String dbPath = "/kdgd/doc/" + kdId + "/tdg/" + tenTdg;
        String sql = "insert into TBL_Tudanhgia (ten, ghi_chu, path, Createdtime, CreatedBy, kd_id, doituong_kd, status, IsDeleted) "
                + " values (?, ?, ?, GETDATE(), ?, ?, ?, 0, 0)";
        jdbcTemplate.update(sql, tenTdg, ghiChu, dbPath, userId, kdId, doituongKd);
    }
}
