package com.timetable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TimeTableExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public int max_day_of_month(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    public int getBlockHour(int block) {
        switch (block) {
            case 1: return 7;
            case 2: return 8;
            case 3: return 9;
            case 4: return 10;
            case 5: return 12;
            case 6: return 13;
            case 7: return 14;
            case 8: return 15;
            case 9: return 16;
            case 10: return 17;
            case 11: return 18;
            case 12: return 19;
            default: return 7;
        }
    }

    public JSONObject getUTDTotal(Date date) {
        String sql = "select NumberOfBlock, TestType from TBL_TimeTable where EndTime <= CONVERT(DATETIME, ?, 102)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, dateFormat.format(date));
        return calculateStats(rows, "grand_utd", "inclass_utd", "lab_utd");
    }

    public JSONObject getTotalBlock(Date date) {
        String sql = "select NumberOfBlock, TestType from TBL_TimeTable";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return calculateStats(rows, "grand_total", "inclass_total", "lab_total");
    }

    public JSONObject getTodayBlock(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DATE);

        String sql = "select NumberOfBlock, TestType from TBL_TimeTable where Year=? and Month=? and StartDay=?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, year, month, day);
        return calculateStats(rows, "grand_total", "inclass_total", "lab_total");
    }

    private JSONObject calculateStats(List<Map<String, Object>> rows, String grandKey, String inclassKey, String labKey) {
        int Tgrand = 0, Tinclass = 0, Tlab = 0;
        for (Map<String, Object> row : rows) {
            int num = (int) row.get("NumberOfBlock");
            Tgrand += num;
            String type = (String) row.get("TestType");
            if (type != null && type.equalsIgnoreCase("Phòng máy")) {
                Tlab += num;
            } else {
                Tinclass += num;
            }
        }
        JSONObject jo = new JSONObject();
        jo.put(grandKey, Tgrand);
        jo.put(inclassKey, Tinclass);
        jo.put(labKey, Tlab);
        return jo;
    }

    public JSONObject getCurrentClasses(Date date) {
        String sql = "select * from TBL_TimeTable where StartTime <= CONVERT(DATETIME, ?, 102) and EndTime >= CONVERT(DATETIME, ?, 102)";
        String dateStr = dateFormat.format(date);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, dateStr, dateStr);

        JSONArray jsaInClass = new JSONArray();
        JSONArray jsaLab = new JSONArray();
        int inclass_num = 0, lab_num = 0;

        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            jo.put("SubjectID", row.get("SubjectID"));
            jo.put("SubjectName", row.get("SubjectName"));
            jo.put("ClassID", row.get("ClassID"));
            jo.put("BatchID", row.get("BatchID"));
            jo.put("MajorID", row.get("MajorID"));
            jo.put("NumberOfBlock", row.get("NumberOfBlock"));
            jo.put("StartingBlock", row.get("StartingBlock"));
            jo.put("ClassRoom", row.get("ClassRoom"));
            jo.put("Building", row.get("Building"));
            jo.put("Faculty", row.get("Faculty"));
            jo.put("Department", row.get("Department"));
            jo.put("Lecturer", row.get("Lecturer"));
            jo.put("TestType", row.get("TestType"));
            jo.put("StartTime", row.get("StartTime"));
            jo.put("EndTime", row.get("EndTime"));

            String type = (String) row.get("TestType");
            if (type != null && type.equalsIgnoreCase("Phòng máy")) {
                lab_num++;
                jsaLab.put(jo);
            } else {
                inclass_num++;
                jsaInClass.put(jo);
            }
        }

        JSONObject joRes = new JSONObject();
        joRes.put("inclass", new JSONObject().put("total", inclass_num).put("classes", jsaInClass));
        joRes.put("lab", new JSONObject().put("total", lab_num).put("classes", jsaLab));
        return joRes;
    }

    public JSONArray getDayTimeTable(int year, int month, int day, String classID, String subjectID, String lecturerCode) {
        StringBuilder sql = new StringBuilder("select * from TBL_TIMETABLE where Year=? and Month=? and StartDay=?");
        List<Object> params = new java.util.ArrayList<>();
        params.add(year); params.add(month); params.add(day);

        if (classID != null && !classID.isEmpty()) { sql.append(" and ClassID=?"); params.add(classID); }
        if (subjectID != null && !subjectID.isEmpty()) { sql.append(" and SubjectID=?"); params.add(subjectID); }
        if (lecturerCode != null && !lecturerCode.isEmpty()) { sql.append(" and LecturerCode=?"); params.add(lecturerCode); }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        JSONArray jar = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("subject_name", row.get("SubjectName"));
            obj.put("lecture", row.get("Lecturer"));
            obj.put("class_id", row.get("ClassID"));
            obj.put("building_name", row.get("Building"));
            obj.put("room_name", row.get("ClassRoom"));
            obj.put("period", row.get("Period"));
            jar.put(obj);
        }
        return jar;
    }

    public List<Map<String, Object>> getDistinctRooms() {
        return jdbcTemplate.queryForList("SELECT distinct Building, ClassRoom from TBL_TIMETABLE order by Building, ClassRoom");
    }

    public int getUsageBlock(String building, String room) {
        Integer count = jdbcTemplate.queryForObject("Select sum(NumberOfBlock) from TBL_TIMETABLE where Building=? and ClassRoom=?", Integer.class, building, room);
        return count != null ? count : 0;
    }

    public int getTotalWorkingDays() {
        String sql = "WITH internalQuery (Amount) AS (SELECT (0) FROM TBL_TIMETABLE GROUP BY Year, month, StartDay) SELECT COUNT(*) FROM internalQuery";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public JSONArray listDistinct(String column) {
        String sql = "SELECT distinct " + column + " from TBL_TIMETABLE where " + column + " is not null";
        List<String> results = jdbcTemplate.queryForList(sql, String.class);
        JSONArray jar = new JSONArray();
        for (String s : results) {
            jar.put(new JSONObject().put("name", s));
        }
        return jar;
    }

    public JSONArray listLecturers() {
        String sql = "SELECT distinct LecturerCode, Lecturer from TBL_TIMETABLE";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        JSONArray jar = new JSONArray();
        for (Map<String, Object> row : rows) {
            jar.put(new JSONObject().put("code", row.get("LecturerCode")).put("name", row.get("Lecturer")));
        }
        return jar;
    }

    public JSONArray listSubjects() {
        String sql = "SELECT distinct SubjectID, SubjectName from TBL_TIMETABLE";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        JSONArray jar = new JSONArray();
        for (Map<String, Object> row : rows) {
            jar.put(new JSONObject().put("code", row.get("SubjectID")).put("name", row.get("SubjectName")));
        }
        return jar;
    }

    public JSONArray getTimeTable(String filterColumn, String filterValue, int week, int year) {
        String sql = "SELECT * from TBL_TIMETABLE where Year=? and " + filterColumn + "=? and Week=?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, year, filterValue, week);
        JSONArray jar = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("faculty", row.get("Faculty"));
            obj.put("subject_id", row.get("SubjectID"));
            obj.put("lecturer_code", row.get("LecturerCode"));
            obj.put("lecturer_name", row.get("Lecturer"));
            obj.put("class_id", row.get("ClassID"));
            obj.put("classroom", row.get("ClassRoom"));
            obj.put("building", row.get("Building"));
            obj.put("date", row.get("StartDay") + "/" + row.get("Month") + "/" + row.get("Year"));
            obj.put("start_hour", row.get("StartHour"));
            obj.put("start_min", row.get("StartMin"));
            obj.put("end_hour", row.get("EndHour"));
            obj.put("end_min", row.get("EndMin"));
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray listRoomSchedule(String building, String room) {
        String sql = "SELECT distinct Year, Month, StartDay from TBL_TIMETABLE where Building=? and ClassRoom=? order by Year, Month, StartDay Asc";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, building, room);
        
        int MAXDAY = 180;
        int[][] dataArr = new int[MAXDAY][6];
        int[] years = new int[MAXDAY];
        int[] months = new int[MAXDAY];
        int[] days = new int[MAXDAY];
        
        int dayIdx = 0;
        for (Map<String, Object> row : rows) {
            if (dayIdx >= MAXDAY) break;
            years[dayIdx] = (int) row.get("Year");
            months[dayIdx] = (int) row.get("Month");
            days[dayIdx] = (int) row.get("StartDay");
            dayIdx++;
        }

        for (int i = 0; i < dayIdx; i++) {
            String pSql = "SELECT Period from TBL_TIMETABLE where Building=? and ClassRoom=? and Year=? and Month=? and StartDay=?";
            List<Integer> periods = jdbcTemplate.queryForList(pSql, Integer.class, building, room, years[i], months[i], days[i]);
            for (int p : periods) {
                if (p >= 1 && p <= 6) dataArr[i][p - 1] = 1;
            }
        }

        JSONArray jar = new JSONArray();
        for (int i = 0; i < dayIdx; i++) {
            JSONObject obj = new JSONObject();
            obj.put("year", years[i]);
            obj.put("month", months[i]);
            obj.put("day", days[i]);
            obj.put("data", dataArr[i]);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getAvailableRooms(String building_name, int start_period, int end_period, int day, int month, int year) {
        String areaSql = "select ID from TBL_CAMPUS_AREA WHERE UPPER(?) LIKE UPPER(Ten)";
        List<Integer> areaIds = jdbcTemplate.queryForList(areaSql, Integer.class, building_name);
        int rootId = areaIds.isEmpty() ? 2 : areaIds.get(0);

        String hierarchySql = "WITH Hierarchy(Ma, ChildId, ChildName, Loai, Mota, DienTich, SoChoNgoi) AS ("
                + " SELECT Ma, Id, Ten, Loai, Mota, DienTich, SoChoNgoi FROM TBL_CAMPUS_AREA WHERE id=? "
                + " UNION ALL "
                + " SELECT n.Ma, n.Id, n.Ten, n.Loai, n.Mota, n.DienTich, n.SoChoNgoi FROM TBL_CAMPUS_AREA n "
                + " INNER JOIN Hierarchy h ON n.ParentId = h.ChildId WHERE (n.isDeleted=0 or n.isDeleted is null)"
                + " ) select * from Hierarchy where Loai=13";
        
        List<Map<String, Object>> areas = jdbcTemplate.queryForList(hierarchySql, rootId);
        JSONArray ja = new JSONArray();
        
        for (Map<String, Object> area : areas) {
            String itemName = (String) area.get("ChildName");
            String tSql = "select Period from TBL_TIMETABLE where Building=? and ClassRoom=? and Year=? and Month=? and StartDay=?";
            List<Integer> occupiedPeriods = jdbcTemplate.queryForList(tSql, Integer.class, building_name, itemName, year, month, day);
            
            boolean[] occupied = new boolean[7];
            for (int p : occupiedPeriods) {
                if (p >= 1 && p <= 6) occupied[p] = true;
            }

            boolean isMatched = true;
            for (int i = start_period; i <= end_period; i++) {
                if (i >= 1 && i <= 6 && occupied[i]) {
                    isMatched = false;
                    break;
                }
            }

            if (isMatched) {
                JSONObject json = new JSONObject();
                json.put("id", area.get("ChildId"));
                json.put("mo_ta", area.get("Mota"));
                json.put("ten", itemName);
                json.put("ma", area.get("Ma"));
                json.put("dien_tich", area.get("DienTich"));
                json.put("seats", area.get("SoChoNgoi"));
                
                JSONArray availablePeriods = new JSONArray();
                for (int i = 1; i <= 6; i++) {
                    if (!occupied[i]) availablePeriods.put(i);
                }
                json.put("available_period_list", availablePeriods);
                ja.put(json);
            }
        }
        return ja;
    }

    public int createBooking(int campus_area_id, int userId, String begin_time, String end_time, String reason) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String sql = "insert into TBL_CAMPUS_AREA_BOOKING (CampusAreaID, OderByID, BeginTime, EndTime, Reason, State, CreatedTime) values (?, ?, ?, ?, ?, 1, ?)";
        jdbcTemplate.update(sql, campus_area_id, userId, begin_time, end_time, reason, dateFormat.format(new Date()));
        return jdbcTemplate.queryForObject("select max(ID) from TBL_CAMPUS_AREA_BOOKING", Integer.class);
    }
    public Date addDate(Date currentDate, int year, int month, int day, int hour, int minute, int second) {
        Calendar c = Calendar.getInstance();
        c.setTime(currentDate);
        c.add(Calendar.YEAR, year);
        c.add(Calendar.MONTH, month);
        c.add(Calendar.DATE, day);
        c.add(Calendar.HOUR, hour);
        c.add(Calendar.MINUTE, minute);
        c.add(Calendar.SECOND, second);
        return c.getTime();
    }

    public JSONArray termList() {
        JSONArray jsaTerms = new JSONArray();
        SimpleDateFormat fmd = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
        String sql = "select * from TBL_TERM where (IsDeleted is null or IsDeleted=0)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            jo.put("id", row.get("ID"));
            jo.put("name", row.get("Name"));
            Object start = row.get("StartTime");
            Object end = row.get("EndTime");
            if (start instanceof Date) jo.put("start_time", fmd.format(start) + " " + fmt.format(start));
            if (end instanceof Date) jo.put("end_time", fmd.format(end) + " " + fmt.format(end));
            jo.put("is_current", row.get("IsCurrent"));
            jsaTerms.put(jo);
        }
        return jsaTerms;
    }

    public JSONArray getClassRoomTimeTable(String building, String classroom, int week, int year) {
        String sql = "SELECT * from TBL_TIMETABLE where Building=? and ClassRoom=? and Week=? and Year=?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, building, classroom, week, year);
        JSONArray jar = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("subject_name", row.get("SubjectName"));
            obj.put("lecturer_name", row.get("Lecturer"));
            obj.put("start_time", row.get("StartTime"));
            obj.put("end_time", row.get("EndTime"));
            jar.put(obj);
        }
        return jar;
    }
}
