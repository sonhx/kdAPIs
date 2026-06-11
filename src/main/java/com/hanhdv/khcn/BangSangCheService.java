package com.hanhdv.khcn;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/bangsangche")
public class BangSangCheService {

    @Autowired
    private BangSangCheDAO bangSangCheDAO;

    @PostMapping("/uploadfile")
    public String updateInsertFile(@RequestParam("file") MultipartFile file) {
        String sServerPath = System.getProperty("user.dir");
        String uploadedFileLocation = sServerPath + "/files/ptitioc/" + file.getOriginalFilename();
        
        File dir = new File(sServerPath + "/files/ptitioc/");
        if (!dir.exists()) dir.mkdirs();

        try {
            file.transferTo(new File(uploadedFileLocation));
            // Legacy Excel processing logic would go here
        } catch (IOException e) {
            return "{\"code\":500, \"description\":\"Upload failed\"}";
        }

        return "{\"code\":200, \"description\":\"Upload thành công\"}";
    }

    @PostMapping("/create")
    public String createBangSangChe(@RequestBody String req) {
        return bangSangCheDAO.createBangSangChe(req);
    }

    @PostMapping("/getlist")
    public String getListBangSangChe(@RequestBody String req) {
        return bangSangCheDAO.getListBangSangChe();
    }

    @PostMapping("/update")
    public String updateBangSangChe(@RequestBody String req) {
        return bangSangCheDAO.updateBangSangChe(req);
    }

    @PostMapping("/delete")
    public String deleteBangSangChe(@RequestBody String req) {
        return bangSangCheDAO.deleteBangSangChe(req);
    }
}
