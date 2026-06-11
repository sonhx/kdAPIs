package com.hanhdv.khcn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/baibao")
public class BaiBaoService {

    @Autowired
    private BaiBaoDAO baiBaoDAO;

    @PostMapping("/uploadbaibao")
    public String updateInsertFile(@RequestParam("file") MultipartFile file) {
        String sServerPath = System.getProperty("user.dir"); // Simplified for now
        String uploadedFileLocation = sServerPath + "/files/ptitioc/" + file.getOriginalFilename();
        
        File dir = new File(sServerPath + "/files/ptitioc/");
        if (!dir.exists()) dir.mkdirs();

        try {
            file.transferTo(new File(uploadedFileLocation));
            // Read file and write to db - This part needs Excel handling logic similar to legacy
            // For now, keeping the call if BaiBaoDAO still has the static method or refactoring it
            // legacy code used jxl library.
        } catch (IOException e) {
            return "{\"code\":500, \"description\":\"Upload failed\"}";
        }

        return "{\"code\":200, \"description\":\"Upload thành công\"}";
    }

    @PostMapping("/getlistbaibao")
    public String getListBaiBao(@RequestBody String req) {
        return baiBaoDAO.getListBaiBao(req);
    }

    @PostMapping("/getlistloaitapchi")
    public String getListLoaiTapChi(@RequestBody String req) {
        return baiBaoDAO.getListLoaiTapChi();
    }

    @PostMapping("/createnewbaibao")
    public String createNewBaiBao(@RequestBody String req) {
        return baiBaoDAO.createNewBaiBao(req);
    }

    @PostMapping("/deletebaibao")
    public String deleteBaiBao(@RequestBody String req) {
        return baiBaoDAO.deleteBaiBao(req);
    }

    @PostMapping("/updatebaibaomember")
    public String updateBaiBaoMember(@RequestBody String req) {
        return baiBaoDAO.updateBaiBaoMember(req);
    }

    @PostMapping("/deletemember")
    public String deleteBaiBaoMember(@RequestBody String req) {
        return baiBaoDAO.deleteMember(req);
    }

    @PostMapping("/addnewmember")
    public String addNewBaiBaoMember(@RequestBody String req) {
        return baiBaoDAO.addNewMem(req);
    }

    @PostMapping("/getmemberbybaibaoid")
    public String getMemberByBaiBaoId(@RequestBody String req) {
        return baiBaoDAO.getListMemberByBaiBaoId(req);
    }

    @PostMapping("/updatebaibao")
    public String updateBaiBao(@RequestBody String req) {
        return baiBaoDAO.updateBaiBao(req);
    }
}
