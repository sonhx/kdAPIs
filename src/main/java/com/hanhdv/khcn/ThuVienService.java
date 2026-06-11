package com.hanhdv.khcn;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/thuvien")
public class ThuVienService {

    @Autowired
    private ThuVienDAO thuVienDAO;

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

    @PostMapping("/createnewbook")
    public String createNewBook(@RequestBody String req) {
        return thuVienDAO.createNewBook(req);
    }

    @PostMapping("/getlistbooks")
    public String getListBooks(@RequestBody String req) {
        return thuVienDAO.getListBook();
    }

    @PostMapping("/getlistdausach")
    public String getListDauSach(@RequestBody String req) {
        return thuVienDAO.getListDauSach();
    }

    @PostMapping("/createdausach")
    public String createDauSach(@RequestBody String req) {
        return thuVienDAO.createDauSach(req);
    }

    @PostMapping("/updatebook")
    public String updateBook(@RequestBody String req) {
        return thuVienDAO.updateBook(req);
    }

    @PostMapping("/deletebook")
    public String deleteBook(@RequestBody String req) {
        return thuVienDAO.deleteBook(req);
    }

    @PostMapping("/updatedausach")
    public String updateDauSach(@RequestBody String req) {
        return thuVienDAO.updateDauSach(req);
    }

    @PostMapping("/deletedausach")
    public String deleteDauSach(@RequestBody String req) {
        return thuVienDAO.deleteDauSach(req);
    }
}
