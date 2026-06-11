package com.ketqua;

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
@RequestMapping("/kq_upload")
public class UploadKQ {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/{session_id}")
    public String uploadKQ(
            @PathVariable("session_id") String session_id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ghi_chu", required = false) String ghiChu) {

        try {
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "Unauthorized";

            String filename = file.getOriginalFilename();
            String path = Config.homeDir + "/kq";
            File targetFile = new File(path + File.separator + filename);
            if (!targetFile.getParentFile().exists()) targetFile.getParentFile().mkdirs();
            file.transferTo(targetFile);

            registerKQ(filename, ghiChu, sst.UserID);
            return "Files uploaded successfully";
        } catch (Exception e) {
            e.printStackTrace();
            return "Upload failed: " + e.getMessage();
        }
    }

    private void registerKQ(String tenKq, String ghiChu, int userId) {
        String dbPath = "/kdgd/doc/kq/" + tenKq;
        String sql = "insert into TBL_Ketqua (ten, ghi_chu, path, Createdtime, CreatedBy, IsDeleted) values (?, ?, ?, GETDATE(), ?, 0)";
        jdbcTemplate.update(sql, tenKq, ghiChu, dbPath, userId);
    }
}
