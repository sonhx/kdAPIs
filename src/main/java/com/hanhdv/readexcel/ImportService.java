package com.hanhdv.readexcel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/import")
public class ImportService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ImportExcelData importExcelData;

    @Autowired
    private ReadXLSXFile readXLSXFile;

    private final String uploadDir = System.getProperty("catalina.base", ".") + "/webapps/files/kiemdinh";

    @PostMapping("/dulieu_1")
    public String uploadDuLieu_1(
            @RequestParam("file") MultipartFile file,
            @RequestParam("detail") String detail) {
        return processUpload(file, detail, 1);
    }

    @PostMapping("/dulieu_2")
    public String uploadDuLieu_2(
            @RequestParam("file") MultipartFile file,
            @RequestParam("detail") String detail) {
        return processUpload(file, detail, 2);
    }

    @PostMapping("/dulieu_csvc_2")
    public String uploadDuLieu_CSVC_2(
            @RequestParam("file") MultipartFile file,
            @RequestParam("detail") String detail) {
        return processUpload(file, detail, 3);
    }

    @PostMapping("/dulieu_csvc_3")
    public String uploadDuLieu_CSVC_3(
            @RequestParam("file") MultipartFile file,
            @RequestParam("detail") String detail) {
        return processUpload(file, detail, 4);
    }

    @PostMapping("/dulieu_csvc_4")
    public String uploadDuLieu_CSVC_4(
            @RequestParam("file") MultipartFile file,
            @RequestParam("detail") String detail) {
        return processUpload(file, detail, 5);
    }

    private String processUpload(MultipartFile file, String detail, int type) {
        try {
            JSONObject jo = new JSONObject(detail);
            String session_id = jo.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int org_id = jo.optInt("org_id", -1);
            int user_id = sst.UserID;

            String filename = file.getOriginalFilename();
            File targetDir = new File(uploadDir);
            if (!targetDir.exists()) targetDir.mkdirs();
            
            File targetFile = new File(targetDir, filename);
            file.transferTo(targetFile);

            String ext = getFileExtension(filename);
            if (ext.equalsIgnoreCase("xls")) {
                if (type == 1) importExcelData.ReadExcelAndInsertIntoDB(targetFile.getAbsolutePath(), org_id, user_id);
            } else if (ext.equalsIgnoreCase("xlsx")) {
                switch(type) {
                    case 1: readXLSXFile.ReadExcelAndInsertIntoDB(targetFile.getAbsolutePath(), org_id, user_id); break;
                    case 2: readXLSXFile.ReadExcelAndInsertIntoDB_2(targetFile.getAbsolutePath(), org_id, user_id); break;
                    case 3: readXLSXFile.ReadExcelAndInsertIntoDB_CSVC_2(targetFile.getAbsolutePath(), org_id, user_id); break;
                    case 4: readXLSXFile.ReadExcelAndInsertIntoDB_CSVC_3(targetFile.getAbsolutePath(), org_id, user_id); break;
                    case 5: readXLSXFile.ReadExcelAndInsertIntoDB_CSVC_4(targetFile.getAbsolutePath(), org_id, user_id); break;
                }
            }
            return "{\"code\":200, \"description\":\"Upload thành công\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.lastIndexOf(".") != -1) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "";
    }
}
