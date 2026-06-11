package com.hanhdv.khcn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/statistic")
public class GetDataStatisticService {

    @Autowired
    private GetDataStatistic gds;

    @PostMapping("/thongkedetai")
    public String getThongKeDeTai() {
        return gds.getThongKeDeTai();
    }

    @PostMapping("/thongkedoanhthudetai")
    public String getThongKeDoanhThuNCKH() {
        return gds.getThongKeDoanhThuNCKH();
    }

    @PostMapping("/thongkecanbodetai")
    public String thongKeCanBoDeTai() {
        return gds.thongKeCanBoDeTai_5nam();
    }

    @PostMapping("/thongkebaibao")
    public String getThongKeBaiBao() {
        return gds.getThongKeBaiBao();
    }

    @PostMapping("/thongkecanbobaibao")
    public String thongKeCanBoBaiBao() {
        return gds.thongKeCanBoBaiBao();
    }

    @PostMapping("/thongkehoithao")
    public String getThongKeHoiThao() {
        return gds.getThongKeHoiThao();
    }

    @PostMapping("/thongkecanbohoithao")
    public String thongKeCanBoHoiThao() {
        return gds.thongKeCanBoHoiThao();
    }

    @PostMapping("/thongkesangche")
    public String getThongKeSangChe() {
        return gds.getThongKeSangChe();
    }

    @PostMapping("/thongkesinhviendetai")
    public String thongKeSinhVienDeTai() {
        return gds.thongKeSinhVienDeTai();
    }

    @PostMapping("/thongkesvgiaithuong")
    public String thongKeSinhVienGiaiThuong() {
        return gds.getThongKeSinhVienGiaiThuong();
    }
}
