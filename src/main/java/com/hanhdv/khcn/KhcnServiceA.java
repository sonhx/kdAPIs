package com.hanhdv.khcn;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/khcnservice")
public class KhcnServiceA {

    @Autowired
    private KhcnDao khcnDao;

    @PostMapping("/uploadfile")
    public String updateInsertFile(@RequestParam("file") MultipartFile file) {
        String sServerPath = System.getProperty("user.dir");
        String uploadedFileLocation = sServerPath + "/files/ptitioc/" + file.getOriginalFilename();
        
        File dir = new File(sServerPath + "/files/ptitioc/");
        if (!dir.exists()) dir.mkdirs();

        try {
            file.transferTo(new File(uploadedFileLocation));
        } catch (IOException e) {
            return "{\"code\":500, \"description\":\"Upload failed\"}";
        }

        return "{\"code\":200, \"description\":\"Upload thành công\"}";
    }

    @PostMapping("/getalldetai")
    public String getListDetai(@RequestBody String req) {
        return khcnDao.getListDetai(req);
    }

    @PostMapping("/getmemberbydetaiid")
    public String getMemberByDeTaiid(@RequestBody String req) {
        return khcnDao.getListMemberDetaiByDetaiId(req);
    }

    @PostMapping("/addnewdetai")
    public String addNewDetai(@RequestBody String req) {
        return khcnDao.addNewDetai(req);
    }

    @PostMapping("/addnewdetaimember")
    public String addNewDetaiMember(@RequestBody String req) {
        return khcnDao.addNewMem(req);
    }

    @PostMapping("/updatedetai")
    public String updateDeTai(@RequestBody String req) {
        return khcnDao.updateDeTai(req);
    }

    @PostMapping("/updatedetaimember")
    public String updateDetaiMember(@RequestBody String req) {
        return khcnDao.updateDetaiMember(req);
    }

    @PostMapping("/deletemember")
    public String deleteDetaiMember(@RequestBody String req) {
        return khcnDao.deleteMember(req);
    }

    @PostMapping("/deletedetai")
    public String deleteDetai(@RequestBody String req) {
        return khcnDao.deleteDetai(req);
    }

    @PostMapping("/getlistcanbo")
    public String getListCanBo(@RequestBody String req) {
        return khcnDao.getListCanBo();
    }

    @PostMapping("/getlistcanbonew")
    public String getListCanBoNew(@RequestBody String req) {
        return khcnDao.getListCanBoNew();
    }
}
