package com.daotao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DaotaoRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public boolean existsChiTieu(int namTS, String maNganh, String coSo) {
        String sql = "Select count(*) from tbl_chi_tieu_daotao where nam_ts=? and ma_nganh=? and co_so=?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, namTS, maNganh, coSo);
        return count != null && count > 0;
    }

    public void insertChiTieu(String trinhDo, String maNganh, String tenNganh, int namTS, int chiTieu, String coSo) {
        String sql = "Insert into tbl_chi_tieu_daotao(trinh_do, ma_nganh, ten_nganh, nam_ts, chi_tieu, co_so) values(?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, trinhDo, maNganh, tenNganh, namTS, chiTieu, coSo);
    }

    public boolean existsSinhVienTinhThanh(int namHoc, String maTinhThanh) {
        String sql = "Select count(*) from tbl_phan_bo_sv where nam_hoc=? and ma_tinh_thanh=?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, namHoc, maTinhThanh);
        return count != null && count > 0;
    }

    public void insertSinhVienTinhThanh(String maTinhThanh, String tenTinhThanh, int namHoc, int soLuong) {
        String sql = "Insert into tbl_phan_bo_sv(ma_tinh_thanh, ten_tinh_thanh, nam_hoc, so_luong) values(?, ?, ?, ?)";
        jdbcTemplate.update(sql, maTinhThanh, tenTinhThanh, namHoc, soLuong);
    }

    public boolean existsQuiMoTS(int year, String heDaoTao) {
        String sql = "Select count(*) from TBL_QUYMOTUYENSINH q "
                   + " INNER JOIN DEF_HEDAOTAO h on h.Value = q.HeDaoTao "
                   + " where q.Year=? and h.Name = ? "
                   + " and (h.IsDeleted is null or h.IsDeleted = 0) "
                   + " and (q.IsDeleted is null or q.IsDeleted = 0)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, year, heDaoTao);
        return count != null && count > 0;
    }

    public Integer getHeDaoTaoValue(String name) {
        String sql = "select value from DEF_HEDAOTAO where Name = ? and (IsDeleted is null or IsDeleted = 0)";
        List<Integer> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("value"), name);
        return values.isEmpty() ? null : values.get(0);
    }

    public void insertQuiMoTS(int year, int heDaoTao, int soLuong) {
        String sql = "Insert into TBL_QUYMOTUYENSINH(Year, HeDaoTao, SoLuong) values(?, ?, ?)";
        jdbcTemplate.update(sql, year, heDaoTao, soLuong);
    }

    public boolean existsNguonLuc(int namHoc, String tenNguonLuc) {
        String sql = "Select count(*) from tbl_nguon_luc_hoc_vien where nam_hoc=? and ten_nguon_luc=?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, namHoc, tenNguonLuc);
        return count != null && count > 0;
    }

    public void insertNguonLuc(int namHoc, String loaiNguonLuc, String tenNguonLuc, int soLuong) {
        String sql = "Insert into tbl_nguon_luc_hoc_vien(nam_hoc, loai_nguon_luc, ten_nguon_luc, so_luong) values(?, ?, ?, ?)";
        jdbcTemplate.update(sql, namHoc, loaiNguonLuc, tenNguonLuc, soLuong);
    }
}
