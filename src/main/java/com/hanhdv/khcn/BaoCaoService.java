package com.hanhdv.khcn;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/baocao")
public class BaoCaoService {

    @Autowired
    private BaoCaoDAO baoCaoDAO;

    @PostMapping("/uploadbaocao")
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

    @PostMapping("/getlistbaocao")
    public String getListBaoCao(@RequestBody String req) {
        return baoCaoDAO.getListBaoCao(req);
    }

    @PostMapping("/getlistloaibaocao")
    public String getListLoaiBaoCao() {
        return baoCaoDAO.getListLoaiBaoCao();
    }

    @PostMapping("/createnewbaocao")
    public String createNewBaoCao(@RequestBody String req) {
        return baoCaoDAO.createNewBaoCao(req);
    }

    @PostMapping("/updatebaocao")
    public String updateBaoCao(@RequestBody String req) {
        return baoCaoDAO.updateBaoCao(req);
    }

    @PostMapping("/deletebaocao")
    public String deleteBaoCao(@RequestBody String req) {
        return baoCaoDAO.deleteBaoCao(req);
    }

    @PostMapping("/updatemembaocao")
    public String updateMemBaoCao(@RequestBody String req) {
        return baoCaoDAO.updateMemberBaoCao(req);
    }

    @PostMapping("/getlistmem")
    public String getListMember(@RequestBody String req) {
        return baoCaoDAO.getListMemberByBaoCaoId(req);
    }

    @PostMapping("/deletemem")
    public String deleteMember(@RequestBody String req) {
        return baoCaoDAO.deleteMember(req);
    }

    @PostMapping("/createmem")
    public String createMem(@RequestBody String req) {
        return baoCaoDAO.addNewMem(req);
    }
}
