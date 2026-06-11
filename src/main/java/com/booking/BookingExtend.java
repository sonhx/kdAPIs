package com.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int MAXLEVEL = 10;

    public String fn_campus_area_name(int campus_area_id) {
        String sql = "select Ten from dbo.tbl_campus_area where ID=?";
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Ten"), campus_area_id);
        return list.isEmpty() ? "unknown" : list.get(0);
    }

    public String fn_building_name_by_room_id(int room_id) {
        int c_campus_area_id = room_id;
        while (c_campus_area_id != 0) {
            String sql = "select Ten, Loai, ParentID from dbo.tbl_campus_area where ID=?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, c_campus_area_id);
            if (rows.isEmpty()) break;
            Map<String, Object> row = rows.get(0);
            if ((int) row.get("Loai") == 4) { // Building
                return (String) row.get("Ten");
            }
            c_campus_area_id = (int) row.get("ParentID");
        }
        return "";
    }

    public String fn_user_name(int user_id) {
        String sql = "select Fullname from dbo.tbl_user where ID=?";
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Fullname"), user_id);
        return list.isEmpty() ? "unknown" : list.get(0);
    }

    public String fn_booking_state_name(int state_id) {
        String sql = "select Name from dbo.DEF_CAMPUS_AREA_BOOKING_STATE where ID=?";
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Name"), state_id);
        return list.isEmpty() ? "unknown" : list.get(0);
    }

    public int createBooking(int campus_area_id, int user_id, String begin_time, String end_time, String reason) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "insert into TBL_CAMPUS_AREA_BOOKING (CampusAreaID, OderByID, BeginTime, EndTime, Reason, State, CreatedTime) values (?, ?, ?, ?, ?, 1, ?)";
        jdbcTemplate.update(sql, campus_area_id, user_id, begin_time, end_time, reason, dateFormat.format(new Date()));
        return jdbcTemplate.queryForObject("select max(ID) from TBL_CAMPUS_AREA_BOOKING", Integer.class);
    }

    public int updateBookingOrder(int booking_id, int new_state, int user_id, String reason) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "update dbo.TBL_CAMPUS_AREA_BOOKING set State=?, ExecByID=?, ExecTime=?, ExecReason=? where ID=?";
        return jdbcTemplate.update(sql, new_state, user_id, dateFormat.format(new Date()), reason, booking_id);
    }

    public int updateBookingEnabledOption(int campus_area_id, int is_booking_enabled) {
        return jdbcTemplate.update("update dbo.TBL_CAMPUS_AREA set IsBookEnabled=? where ID=?", is_booking_enabled, campus_area_id);
    }

    public int deleteBooking(int booking_id) {
        return jdbcTemplate.update("update dbo.TBL_CAMPUS_AREA_BOOKING set IsDeleted=1 where ID=?", booking_id);
    }

    public JSONArray listBookingOrderOfArea(int campus_area_id) {
        JSONArray jar = new JSONArray();
        String sql = "select * from TBL_CAMPUS_AREA_BOOKING where (IsDeleted is null or IsDeleted=0) order by CreatedTime desc";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            int c_campus_area_id = (int) row.get("CampusAreaID");
            if (is_area_sub_area(c_campus_area_id, campus_area_id) != 1) continue;

            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("campus_area_id", c_campus_area_id);
            obj.put("campus_area_name", fn_campus_area_name(c_campus_area_id));
            obj.put("oder_by_id", row.get("OderByID"));
            obj.put("oder_by_name", fn_user_name((int) row.get("OderByID")));
            obj.put("begin_time", row.get("BeginTime"));
            obj.put("end_time", row.get("EndTime"));
            obj.put("state", row.get("State"));
            obj.put("state_name", fn_booking_state_name((int) row.get("State")));
            obj.put("created_time", row.get("CreatedTime"));
            jar.put(obj);
        }
        return jar;
    }

    public String fn_area_prefix(int area_id) {
        List<String> names = new ArrayList<>();
        int curr_id = area_id;
        while (curr_id != 0) {
            String sql = "select Ten, ParentID from dbo.TBL_CAMPUS_AREA where ID=?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, curr_id);
            if (rows.isEmpty()) break;
            Map<String, Object> row = rows.get(0);
            names.add((String) row.get("Ten"));
            curr_id = (int) row.get("ParentID");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = names.size() - 3; i >= 1; i--) {
            if (i < names.size() - 3) sb.append(" >>> ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    public JSONArray listMyBookingOrder(int user_id, int campus_area_id) {
        JSONArray jar = new JSONArray();
        String sql = "select * from TBL_CAMPUS_AREA_BOOKING where OderByID=? and (IsDeleted is null or IsDeleted=0) order by CreatedTime desc";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, user_id);
        for (Map<String, Object> row : rows) {
            int c_campus_area_id = (int) row.get("CampusAreaID");
            if (is_area_sub_area(c_campus_area_id, campus_area_id) != 1) continue;

            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("campus_area_id", c_campus_area_id);
            obj.put("campus_area_name", fn_campus_area_name(c_campus_area_id));
            obj.put("oder_by_id", row.get("OderByID"));
            obj.put("oder_by_name", fn_user_name((int) row.get("OderByID")));
            obj.put("begin_time", row.get("BeginTime"));
            obj.put("end_time", row.get("EndTime"));
            obj.put("state", row.get("State"));
            obj.put("state_name", fn_booking_state_name((int) row.get("State")));
            obj.put("created_time", row.get("CreatedTime"));
            obj.put("prefix", fn_area_prefix(c_campus_area_id));
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray listAvailableBookingArea(int root_campus_area_id, int all_level) {
        JSONArray jar = new JSONArray();
        String sql;
        if (all_level == 0) {
            sql = "select * from TBL_CAMPUS_AREA where ParentId=?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_campus_area_id);
            for (Map<String, Object> row : rows) {
                jar.put(mapAreaRow(row));
            }
        } else {
            sql = "WITH Hierarchy(Ma,ChildId, ChildName, Generation, ParentId,Loai,IsBookEnabled,DienTich,SoChoNgoi,MoTa) AS "
                    + " (SELECT Ma, Id, Ten, 0, ParentId,Loai,IsBookEnabled,DienTich,SoChoNgoi,MoTa FROM TBL_CAMPUS_AREA "
                    + " WHERE id=?  UNION ALL "
                    + " SELECT Parent.Ma, Child.Id, Child.Ten, Generation + 1, Child.ParentId, Child.Loai, Child.IsBookEnabled, Child.DienTich, Child.SoChoNgoi, Child.MoTa "
                    + " FROM TBL_CAMPUS_AREA AS Child "
                    + " INNER JOIN Hierarchy AS Parent ON Child.ParentId = Parent.ChildId) "
                    + " SELECT * FROM Hierarchy WHERE ChildId <> ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_campus_area_id, root_campus_area_id);
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("ChildId"));
                obj.put("ten", row.get("ChildName"));
                obj.put("ma", row.get("Ma"));
                obj.put("dientich", row.get("DienTich"));
                obj.put("so_cho_ngoi", row.get("SoChoNgoi"));
                obj.put("loai", row.get("Loai"));
                obj.put("status", row.get("IsBookEnabled"));
                obj.put("mota", row.get("MoTa"));
                jar.put(obj);
            }
        }
        return jar;
    }

    private JSONObject mapAreaRow(Map<String, Object> row) {
        JSONObject obj = new JSONObject();
        obj.put("id", row.get("ID"));
        obj.put("ten", row.get("Ten"));
        obj.put("ma", row.get("Ma"));
        obj.put("dientich", row.get("DienTich"));
        obj.put("so_cho_ngoi", row.get("SoChoNgoi"));
        obj.put("loai", row.get("Loai"));
        obj.put("status", row.get("IsBookEnabled"));
        obj.put("mota", row.get("MoTa"));
        return obj;
    }

    public int is_area_sub_area(int s_area_id, int d_area_id) {
        if (s_area_id == d_area_id) return 1;
        int t_area_id = s_area_id;
        while (t_area_id != 0) {
            String sql = "select ParentID from TBL_CAMPUS_AREA where ID=? and (IsDeleted is null or IsDeleted='0')";
            List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("ParentID"), t_area_id);
            if (list.isEmpty()) break;
            t_area_id = list.get(0);
            if (t_area_id == d_area_id) return 1;
        }
        return 0;
    }

    public JSONArray listBookingState() {
        JSONArray jar = new JSONArray();
        String sql = "select * from dbo.DEF_CAMPUS_AREA_BOOKING_STATE where (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("name", row.get("Name"));
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray listScheduleTimeOfRoom(int campus_area_id, String room_code) {
        String building_code = fn_building_name_by_room_id(campus_area_id);
        JSONArray jar = new JSONArray();
        
        String sqlTimetable = "select * from TBL_TIMETABLE where ClassRoom=? and Building=?";
        List<Map<String, Object>> ttRows = jdbcTemplate.queryForList(sqlTimetable, room_code, building_code);
        for (Map<String, Object> row : ttRows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("title", row.get("SubjectName"));
            obj.put("start", row.get("StartTime"));
            obj.put("end", row.get("EndTime"));
            obj.put("type", "timetable");
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray listExtraSchedule(int campus_area_id) {
        JSONArray jar = new JSONArray();
        String sqlBooking = "select * from TBL_CAMPUS_AREA_BOOKING where CampusAreaID=? and (IsDeleted is null or IsDeleted=0)";
        List<Map<String, Object>> bRows = jdbcTemplate.queryForList(sqlBooking, campus_area_id);
        for (Map<String, Object> row : bRows) {
            JSONObject obj = new JSONObject();
            obj.put("id", 0);
            obj.put("title", row.get("Reason"));
            obj.put("start", row.get("BeginTime"));
            obj.put("end", row.get("EndTime"));
            obj.put("type", "extra");
            jar.put(obj);
        }
        return jar;
    }
}
