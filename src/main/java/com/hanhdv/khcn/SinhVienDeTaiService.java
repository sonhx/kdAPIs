package com.hanhdv.khcn;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/sinhviendetai")
public class SinhVienDeTaiService {

    @Autowired
    private SinhVienDeTaiDAO sinhVienDeTaiDAO;

    @PostMapping("/uploaddetaisinhvien")
    public String uploadFileDeTaiSinhVien(@RequestParam("file") MultipartFile file) {
        String sServerPath = System.getProperty("user.dir");
        String uploadedFileLocation = sServerPath + "/files/ptitioc/" + file.getOriginalFilename();
        
        File dir = new File(sServerPath + "/files/ptitioc/");
        if (!dir.exists()) dir.mkdirs();

        try {
            file.transferTo(new File(uploadedFileLocation));
        } catch (IOException e) {
            return "{\"code\":500, \"description\":\"Upload failed\"}";
        }

        return "{\"code\":200, \"description\":\"Upload file thành công\"}";
    }

    @PostMapping("/getlistdetaisv")
    public String getListDeTaiSinhVien(@RequestBody String req) {
        return sinhVienDeTaiDAO.getListDeTaiSinhVien();
    }

    @PostMapping("/createnewdetaisv")
    public String createNewDeTaiSinhVien(@RequestBody String req) {
        return sinhVienDeTaiDAO.createNewDeTaiSinhVien(req);
    }

    @PostMapping("/deletedetaisv")
    public String deleteDeTaiSinhVien(@RequestBody String req) {
        return sinhVienDeTaiDAO.deleteDeTaiSinhVien(req);
    }
}
