package com.vanphong;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VanphongExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String fn_user_name(int user_id) {
        String sql = "select Fullname from dbo.tbl_user where ID=?";
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Fullname"), user_id);
        return list.isEmpty() ? "unknown" : list.get(0);
    }

    // EVENT
    public int createEvent(String event_name, String event_time, int user_id) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "insert into tbl_event (Name, CreatedBy, Time, CreatedTime) values (?, ?, ?, ?)";
        return jdbcTemplate.update(sql, event_name, user_id, event_time, dateFormat.format(new Date()));
    }

    public int deleteEvent(int event_id) {
        return jdbcTemplate.update("update tbl_event set IsDeleted=1 where ID=?", event_id);
    }

    public JSONArray listEvent() {
        JSONArray jaout = new JSONArray();
        String sql = "select * from tbl_event where (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("time", row.get("Time"));
            obj.put("one_time", row.get("OneTime"));
            obj.put("name", row.get("Name"));
            obj.put("created_time", row.get("CreatedTime"));
            obj.put("created_by_name", fn_user_name((int) row.get("CreatedBy")));
            jaout.put(obj);
        }
        return jaout;
    }

    // PAPER RECORD
    public int createPaperRecord(String pname, String purl, String ptime) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "insert into tbl_baibao_pr (TenBaiBao, Url, PublicTime, NamXuatBan, CreatedTime) values (?, ?, ?, 2022, ?)";
        return jdbcTemplate.update(sql, pname, purl, ptime, dateFormat.format(new Date()));
    }

    public int deletePaperRecord(int paper_id) {
        return jdbcTemplate.update("update tbl_baibao_pr set IsDeleted=1 where ID=?", paper_id);
    }

    public JSONArray listPaperRecord() {
        JSONArray jaout = new JSONArray();
        String sql = "select * from tbl_baibao_pr where (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("ten_bai_bao", row.get("TenBaiBao"));
            obj.put("ten_tac_gia", row.get("TacGia"));
            obj.put("nam_xuat_ban", row.get("NamXuatBan"));
            obj.put("url", row.get("Url"));
            obj.put("public_time", row.get("PublicTime"));
            obj.put("created_time", row.get("CreatedTime"));
            jaout.put(obj);
        }
        return jaout;
    }

    // PORTAL & FANPAGE
    public int createPortalAndFanpageRecord(int year, int month, int portal, int fanpage, int user_id) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "insert into TBL_PORTAL_AND_FANPAGE_ACCESS (Year, Month, Portal, Fanpage, CreatedBy, CreatedTime) values (?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, year, month, portal, fanpage, user_id, dateFormat.format(new Date()));
    }

    public int deletePortalAndFanpageRecord(int record_id) {
        return jdbcTemplate.update("update TBL_PORTAL_AND_FANPAGE_ACCESS set IsDeleted=1 where ID=?", record_id);
    }

    public int updatePortalAndFanpageRecord(int record_id, int portal, int fanpage) {
        if (portal >= 0 && fanpage >= 0) {
            return jdbcTemplate.update("Update TBL_PORTAL_AND_FANPAGE_ACCESS set Portal=?, Fanpage=? where ID=?", portal, fanpage, record_id);
        } else if (portal >= 0) {
            return jdbcTemplate.update("Update TBL_PORTAL_AND_FANPAGE_ACCESS set Portal=? where ID=?", portal, record_id);
        } else if (fanpage >= 0) {
            return jdbcTemplate.update("Update TBL_PORTAL_AND_FANPAGE_ACCESS set Fanpage=? where ID=?", fanpage, record_id);
        }
        return 0;
    }

    public JSONArray listPortalAndFanpageRecord() {
        JSONArray jaout = new JSONArray();
        String sql = "select * from TBL_PORTAL_AND_FANPAGE_ACCESS where (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("year", row.get("Year"));
            obj.put("month", row.get("Month"));
            obj.put("portal", row.get("Portal"));
            obj.put("fanpage", row.get("Fanpage"));
            obj.put("description", row.get("Description"));
            obj.put("created_time", row.get("CreatedTime"));
            obj.put("created_by_name", fn_user_name((int) row.get("CreatedBy")));
            jaout.put(obj);
        }
        return jaout;
    }

    // LIBRARY ACCESS
    public int createLibraryRecord(int year, int month, int access, int download, int user_id) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "insert into tbl_library_access (Year, Month, Access, Download, CreatedBy, CreatedTime) values (?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, year, month, access, download, user_id, dateFormat.format(new Date()));
    }

    public int deleteLibraryRecord(int record_id) {
        return jdbcTemplate.update("update tbl_library_access set IsDeleted=1 where ID=?", record_id);
    }

    public JSONArray listLibraryRecord() {
        JSONArray jaout = new JSONArray();
        String sql = "select * from tbl_library_access where (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("year", row.get("Year"));
            obj.put("month", row.get("Month"));
            obj.put("access", row.get("Access"));
            obj.put("download", row.get("Download"));
            obj.put("description", row.get("Description"));
            obj.put("created_time", row.get("CreatedTime"));
            obj.put("created_by_name", fn_user_name((int) row.get("CreatedBy")));
            jaout.put(obj);
        }
        return jaout;
    }
}
