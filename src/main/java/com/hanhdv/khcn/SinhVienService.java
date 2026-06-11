package com.hanhdv.khcn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/khcnsinhvien")
public class SinhVienService {

    @Autowired
    private SinhVienDAO sinhVienDAO;

    @PostMapping("/creategiaithuong")
    public String createGiaiThuongSV(@RequestBody String req) {
        return sinhVienDAO.createGiaiThuongSV(req);
    }

    @PostMapping("/getlistgiaithuong")
    public String getListGiaiThuong(@RequestBody String req) {
        return sinhVienDAO.getListGiaiThuong();
    }

    @PostMapping("/updategiaithuong")
    public String updateGiaiThuong(@RequestBody String req) {
        return sinhVienDAO.updateGiaiThuong(req);
    }

    @PostMapping("/updatesv")
    public String updateSinhVien(@RequestBody String req) {
        return sinhVienDAO.updateSinhVien(req);
    }

    @PostMapping("/deletesv")
    public String deleteSinhVien(@RequestBody String req) {
        return sinhVienDAO.deleteSinhVien(req);
    }

    @PostMapping("/deletegiaithuong")
    public String deleteGiaiThuong(@RequestBody String req) {
        return sinhVienDAO.deleteGiaiThuong(req);
    }
}
