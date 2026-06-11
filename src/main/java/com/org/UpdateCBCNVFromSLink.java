package com.org;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class UpdateCBCNVFromSLink {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    @Scheduled(cron = "0 0 4 * * *") // Run at 4:00 AM every day
    public void syncCBCNVData() {
        try {
            // Get the last processed updatedTime from TBL_CBCNV
            String lastProcessedTime = getLastProcessedTime();

            // Fetch new data from S-Link where updatedTime > lastProcessedTime
            List<Map<String, Object>> newData = fetchCBCNVFromSLink(lastProcessedTime);

            for (Map<String, Object> row : newData) {
                updateCBCNV(row);
            }
        } catch (Exception e) {
            // Log error, but since no logger, perhaps System.out
            System.out.println("Error in syncCBCNVData: " + e.getMessage());
        }
    }

    private String getLastProcessedTime() {
        // Get the maximum UpdatedTime from TBL_CBCNV
        String sql = "SELECT MAX(UpdatedTime) FROM TBL_CBCNV WHERE UpdatedTime IS NOT NULL";
        String maxTime = jdbcTemplate.queryForObject(sql, String.class);
        if (maxTime == null) {
            // Default start time if no data
            return "1900/01/01 00:00:00";
        }
        return maxTime;
    }

    private List<Map<String, Object>> fetchCBCNVFromSLink(String lastTime) {
        // Placeholder for fetching CBCNV data from S-Link
        // This should connect to S-Link and fetch rows where updatedTime > lastTime
        // For now, return empty list
        // TODO: Implement actual connection to S-Link
        return List.of(); // Return empty list as placeholder
    }

    private void updateCBCNV(Map<String, Object> row) {
        Integer id = (Integer) row.get("id");
        if (id == null) return;

        // Check if CBCNV exists
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TBL_CBCNV WHERE ID = ?", Integer.class, id);
        String currentTime = dateFormat.format(new Date());

        if (count != null && count > 0) {
            // Update existing
            String updateSql = "UPDATE TBL_CBCNV SET MaCB = ?, TenDayDu = ?, NgaySinh = ?, NamSinh = ?, GioiTinh = ?, PlaceCode = ?, OrgCode = ?, ChucDanh = ?, Trinhdo = ?, HocHam = ?, LoaiHD = ?, NgachLuong = ?, Ngach = ?, NgayVaoHocVien = ?, State = ?, LastStateChangedTime = ?, TsMoi = ?, Mobile = ?, Email = ?, NoiSinh = ?, QueQuan = ?, MaDT = ?, MaTG = ?, MADBQUOCTICH = ?, NamTuyendung = ?, CosoDaotao = ?, HanHD = ?, ChucdanhKH = ?, TGDatChucdanh = ?, TGDatTrinhdo = ?, CMDaotao = ?, NoicapBang = ?, TrinhdoChinhtri = ?, VitriQuanly = ?, HesoTrachnhiem = ?, Bac = ?, MaGDDH = ?, MaGDThs = ?, MaGDTs = ?, UpdatedTime = ? WHERE ID = ?";
            jdbcTemplate.update(updateSql,
                row.get("macb"),
                row.get("tendaydu"),
                row.get("ngaysinh"),
                row.get("namsinh"),
                row.get("gioitinh"),
                row.get("placecode"),
                row.get("orgcode"),
                row.get("chucdanh"),
                row.get("trinhdo"),
                row.get("hocham"),
                row.get("loaihd"),
                row.get("ngachluong"),
                row.get("ngach"),
                row.get("ngayvaohocvien"),
                row.get("state"),
                row.get("laststatechangedtime"),
                row.get("tsmoi"),
                row.get("mobile"),
                row.get("email"),
                row.get("noisinh"),
                row.get("quequan"),
                row.get("madt"),
                row.get("matg"),
                row.get("madbquoctich"),
                row.get("namtuyendung"),
                row.get("cosodaotao"),
                row.get("hanhd"),
                row.get("chucdanhkh"),
                row.get("tgdatchucdanh"),
                row.get("tgdattrinhdo"),
                row.get("cmdaotao"),
                row.get("noicapbang"),
                row.get("trinhdochinhri"),
                row.get("vitriquanly"),
                row.get("hesotrachnhiem"),
                row.get("bac"),
                row.get("magddh"),
                row.get("magdths"),
                row.get("magdts"),
                row.get("updatedtime"),
                id);
        } else {
            // Insert new
            String insertSql = "INSERT INTO TBL_CBCNV (ID, MaCB, TenDayDu, NgaySinh, NamSinh, GioiTinh, PlaceCode, OrgCode, ChucDanh, Trinhdo, HocHam, LoaiHD, NgachLuong, Ngach, NgayVaoHocVien, State, LastStateChangedTime, TsMoi, Mobile, Email, NoiSinh, QueQuan, MaDT, MaTG, MADBQUOCTICH, NamTuyendung, CosoDaotao, HanHD, ChucdanhKH, TGDatChucdanh, TGDatTrinhdo, CMDaotao, NoicapBang, TrinhdoChinhtri, VitriQuanly, HesoTrachnhiem, Bac, MaGDDH, MaGDThs, MaGDTs, CreatedTime, UpdatedTime, IsDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
            jdbcTemplate.update(insertSql,
                id,
                row.get("macb"),
                row.get("tendaydu"),
                row.get("ngaysinh"),
                row.get("namsinh"),
                row.get("gioitinh"),
                row.get("placecode"),
                row.get("orgcode"),
                row.get("chucdanh"),
                row.get("trinhdo"),
                row.get("hocham"),
                row.get("loaihd"),
                row.get("ngachluong"),
                row.get("ngach"),
                row.get("ngayvaohocvien"),
                row.get("state"),
                row.get("laststatechangedtime"),
                row.get("tsmoi"),
                row.get("mobile"),
                row.get("email"),
                row.get("noisinh"),
                row.get("quequan"),
                row.get("madt"),
                row.get("matg"),
                row.get("madbquoctich"),
                row.get("namtuyendung"),
                row.get("cosodaotao"),
                row.get("hanhd"),
                row.get("chucdanhkh"),
                row.get("tgdatchucdanh"),
                row.get("tgdattrinhdo"),
                row.get("cmdaotao"),
                row.get("noicapbang"),
                row.get("trinhdochinhri"),
                row.get("vitriquanly"),
                row.get("hesotrachnhiem"),
                row.get("bac"),
                row.get("magddh"),
                row.get("magdths"),
                row.get("magdts"),
                currentTime,
                row.get("updatedtime"));
        }
    }
}
