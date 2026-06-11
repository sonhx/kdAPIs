package com.timetable;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/timetable")
public class TimeTableService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private TimeTableExtend tte;

    @PostMapping("/current")
    public String getCurrent(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        Date currentDate = tte.addDate(new Date(), 0, 0, -3, -5, 0, 0);
        try {
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("current", tte.getCurrentClasses(currentDate));
            jout.put("total", tte.getTotalBlock(currentDate));
            jout.put("today", tte.getTodayBlock(currentDate));
            jout.put("utd", tte.getUTDTotal(currentDate));
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/term")
    public String termList(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONArray jsaTerms = tte.termList();
            if (jsaTerms == null) return "{\"code\":9999, \"description\":\"Error while retrieving term list\"}";

            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("term_list", jsaTerms);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/getdaytimetable")
    public String getDayTimeTable(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            int year = jo.getInt("year");
            int month = jo.getInt("month");
            int day = jo.getInt("day");
            String class_id = jo.has("class_id") ? jo.getString("class_id") : "";
            String subject_code = jo.has("subject_code") ? jo.getString("subject_code") : "";
            String lecture_code = jo.has("lecture_code") ? jo.getString("lecture_code") : "";

            jout.put("daytimetable_list", tte.getDayTimeTable(year, month, day, class_id, subject_code, lecture_code));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/thongkedayvahoc")
    public String thongKeDayVaHoc(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        DecimalFormat df = new DecimalFormat("0.00");
        try {
            int total_working_day = tte.getTotalWorkingDays();
            int total_block_per_room = total_working_day * 6 * 2;
            List<Map<String, Object>> rooms = tte.getDistinctRooms();
            JSONArray jar = new JSONArray();
            int total_used_block = 0;
            int roomIdx = 0;

            for (Map<String, Object> room : rooms) {
                JSONObject obj = new JSONObject();
                String building = (String) room.get("Building");
                String roomName = (String) room.get("ClassRoom");
                int usedBlocks = tte.getUsageBlock(building, roomName);

                obj.put("id", roomIdx++);
                obj.put("building", building);
                obj.put("room", roomName);
                obj.put("usage_block", usedBlocks);
                obj.put("total_block", total_block_per_room);

                float ratio = (total_block_per_room > 0) ? 100 * ((float) usedBlocks / total_block_per_room) : 0;
                obj.put("usage_ratio", df.format(ratio));
                jar.put(obj);
                total_used_block += usedBlocks;
            }

            long totalAvail = (long) total_block_per_room * rooms.size();
            float totalRatio = (totalAvail > 0) ? 100 * ((float) total_used_block / totalAvail) : 0;

            jout.put("total_available_block", totalAvail);
            jout.put("total_used_block", total_used_block);
            jout.put("usage_ratio", df.format(totalRatio));
            jout.put("room_usage_list", jar);
            jout.put("code", 200);
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error processing statistics\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listroomschedule")
    public String listRoomSchedule(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            jout.put("room_schedule_list", tte.listRoomSchedule(jo.getString("bulding_name"), jo.getString("room_name")));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listclass")
    public String listClass(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("class_list", tte.listDistinct("ClassID"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listlecturer")
    public String listLecturer(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("lecturer_list", tte.listLecturers());
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listfaculty")
    public String listFaculty(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("faculty_list", tte.listDistinct("Faculty"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listsubject")
    public String listSubject(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("faculty_list", tte.listSubjects()); // Keeping original field name "faculty_list" for compatibility
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listbuilding")
    public String listBuilding(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("building_list", tte.listDistinct("Building"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listclassroom")
    public String listClassRoom(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("classroom_list", tte.listDistinct("ClassRoom"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listweek")
    public String listWeek(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("week_list", tte.listDistinct("Week"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listschoolyear")
    public String listSchoolYear(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("schoolyear_list", tte.listDistinct("SchoolYear"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/listsemester")
    public String listSemester(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        jout.put("semester_list", tte.listDistinct("Semester"));
        jout.put("code", 200);
        return jout.toString();
    }

    @PostMapping("/getclasstimetable")
    public String getClassTimeTable(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String classId = jo.getString("class_id");
            int week = jo.getInt("week");
            jout.put("class_id", classId);
            jout.put("week", week);
            jout.put("timetable_list", tte.getTimeTable("ClassID", classId, week, 2023));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/getlecturertimetable")
    public String getLecturerTimeTable(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String code = jo.getString("lecturer_code");
            int week = jo.getInt("week");
            jout.put("lecturer_code", code);
            jout.put("week", week);
            jout.put("timetable_list", tte.getTimeTable("LecturerCode", code, week, 2023));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/getclassroomtimetable")
    public String getClassRoomTimeTable(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String classroom = jo.getString("classroom");
            String building = jo.getString("building");
            int week = jo.getInt("week");
            jout.put("classroom", classroom);
            jout.put("building", building);
            jout.put("week", week);
            jout.put("timetable_list", tte.getClassRoomTimeTable(building, classroom, week, 2023));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/getfacultytimetable")
    public String getFacultyTimeTable(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String faculty = jo.getString("faculty");
            int week = jo.getInt("week");
            jout.put("faculty", faculty);
            jout.put("week", week);
            jout.put("timetable_list", tte.getTimeTable("Faculty", faculty, week, 2023));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/getavailablerooms")
    public String getAvailableRooms(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String building = jo.getString("building");
            int startP = jo.getInt("start_period");
            int endP = jo.getInt("end_period");
            int day = jo.getInt("day");
            int month = jo.getInt("month");
            int year = jo.getInt("year");

            JSONArray ja = tte.getAvailableRooms(building, startP, endP, day, month, year);
            jout.put("total_counter", ja.length());
            jout.put("available_room_list", ja);
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/createbooking")
    public String createBooking(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jo.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int areaId = jo.getInt("campus_area_id");
            String startT = jo.getString("begin_time");
            String endT = jo.getString("end_time");
            String reason = jo.has("reason") ? jo.getString("reason") : "";

            int bookingId = tte.createBooking(areaId, sst.UserID, startT, endT, reason);
            jout.put("booking_id", bookingId);
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }
}
