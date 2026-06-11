package com.hanhdv.readexcel;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jxl.Sheet;
import jxl.Workbook;
import java.io.File;

@Service
public class ImportExcelData {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void ReadExcelAndInsertIntoDB(String path, int org_id, int user_id) {
        try {
            File inputWorkbook = new File(path);
            if (!inputWorkbook.exists()) return;

            Workbook w = Workbook.getWorkbook(inputWorkbook);
            Sheet sheet = w.getSheet(0);

            for (int i = 2; i < sheet.getRows(); i++) {
                String cmnd = sheet.getCell(1, i).getContents();
                String ten = sheet.getCell(2, i).getContents();
                String hoc_ham = sheet.getCell(3, i).getContents();
                String hoc_vi = sheet.getCell(4, i).getContents();
                String email = sheet.getCell(5, i).getContents();
                String ngay_sinh = sheet.getCell(6, i).getContents();
                String gioi_tinh = sheet.getCell(7, i).getContents();
                String chuc_vu = sheet.getCell(8, i).getContents();

                insertRow(cmnd, ten, hoc_ham, hoc_vi, email, ngay_sinh, gioi_tinh, chuc_vu);
            }
            w.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void insertRow(String cmnd, String ten, String hoc_ham, String hoc_vi, String email, String ngay_sinh, String gioi_tinh, String chuc_vu) {
        try {
            int hocham = getDefValue("dbo.def_hocham", hoc_ham);
            int hocvi = getDefValue("dbo.def_trinhdo", hoc_vi);
            int gioitinh = getDefValue("dbo.def_gioitinh", gioi_tinh);
            int chucvu = getDefValue("dbo.def_chucdanh", chuc_vu);

            String sql = "Insert into [TBL_CBCNV](cccd, tendaydu, hocham, trinhdo, email, ngaysinh, gioitinh, chucdanh) values(?,?,?,?,?,?,?,?)";
            jdbcTemplate.update(sql, cmnd, ten, hocham, hocvi, email, ngay_sinh, gioitinh, chucvu);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getDefValue(String table, String name) {
        try {
            return jdbcTemplate.queryForObject("select value from " + table + " where name=?", Integer.class, name);
        } catch (Exception e) {
            return -1;
        }
    }

    public void insertRow_2(String ten, String email, String don_vi, String chuc_vu) {
        try {
            String org_code = "";
            try {
                org_code = jdbcTemplate.queryForObject("select code from dbo.tbl_org where name=?", String.class, don_vi);
            } catch (Exception e) {}

            int chucvu = getDefValue("dbo.def_chucdanh", chuc_vu);

            String sql = "Insert into [TBL_CBCNV](tendaydu, email, orgcode, chucdanh) values(?,?,?,?)";
            jdbcTemplate.update(sql, ten, email, org_code, chucvu);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void insertRow_CSVC_2(String nganh, String dau_sach, String ban_sach) {
        jdbcTemplate.update("Insert into [TBL_BOOK_STORE_STAT](nhomnganh, dausach, bansach) values(?,?,?)", nganh, dau_sach, ban_sach);
    }

    public void insertRow_CSVC_3(String ten, String dientich, String sohuu) {
        try {
            float val_dientich = Float.parseFloat(dientich);
            int val_sohuu = sohuu.equalsIgnoreCase("Sở hữu") ? 1 : -1;
            jdbcTemplate.update("Insert into [TBL_CAMPUS_AREA](ten, dientich, ownertype) values(?,?,?)", ten, val_dientich, val_sohuu);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void insertRow_CSVC_4(String ten_tbi, String ten_phong, String dtg_sd, String so_lg) {
        try {
            int camp_area_id = -1;
            try {
                camp_area_id = jdbcTemplate.queryForObject("select id from dbo.tbl_CAMPUS_AREA where ten=?", Integer.class, ten_phong);
            } catch (Exception e) {}

            int org_id = -1;
            try {
                org_id = jdbcTemplate.queryForObject("select id from dbo.tbl_ORG where name=?", Integer.class, ten_phong);
            } catch (Exception e) {}

            jdbcTemplate.update("Insert into [TBL_CAMPUS_AREA_ASSET](ten, CampusAreaID, DonViSuDung, SoLuong) values(?,?,?,?)",
                    ten_tbi, camp_area_id, org_id, Integer.parseInt(so_lg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
