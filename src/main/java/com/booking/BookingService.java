package com.booking;

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
@RequestMapping("/booking")
public class BookingService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private BookingExtend bookingExtend;

    @PostMapping("/createbooking")
    public String createBooking(@RequestBody String sReq) {
        System.out.println("-------------- createBooking:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int campus_area_id = jsonobjReq.getInt("campus_area_id");
            String begin_time = jsonobjReq.getString("begin_time");
            String end_time = jsonobjReq.getString("end_time");
            String reason = jsonobjReq.has("reason") ? jsonobjReq.getString("reason") : "";

            int booking_id = bookingExtend.createBooking(campus_area_id, sst.UserID, begin_time, end_time, reason);
            jout.put("booking_id", booking_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/updatebookingorder")
    public String updateBookingOrder(@RequestBody String sReq) {
        System.out.println("updateBookingOrder:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int booking_id = jsonobjReq.getInt("booking_id");
            int new_state = jsonobjReq.getInt("new_state");
            String reason = jsonobjReq.has("reason") ? jsonobjReq.getString("reason") : "";

            bookingExtend.updateBookingOrder(booking_id, new_state, sst.UserID, reason);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/updatebookingenabledoption")
    public String updateBookingEnabledOption(@RequestBody String sReq) {
        System.out.println("updateBookingEnabledOption:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int campus_area_id = jsonobjReq.getInt("campus_area_id");
            int is_booking_enabled = jsonobjReq.getInt("is_booking_enabled");

            bookingExtend.updateBookingEnabledOption(campus_area_id, is_booking_enabled);
            jout.put("campus_area_id", campus_area_id);
            jout.put("is_booking_enabled", is_booking_enabled);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/deletebooking")
    public String deleteBooking(@RequestBody String sReq) {
        System.out.println("DeleteBooking:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int booking_id = jsonobjReq.getInt("booking_id");
            bookingExtend.deleteBooking(booking_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listbookingorderofarea")
    public String listBookingOrderOfArea(@RequestBody String sReq) {
        System.out.println("-------------- listBookingOrderOfArea:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            int campus_area_id = jo.getInt("campus_area_id");
            JSONArray jar = bookingExtend.listBookingOrderOfArea(campus_area_id);
            jout.put("booking_order_list", jar);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listmybookingorder")
    public String listMyBookingOrder(@RequestBody String sReq) {
        System.out.println("-------------- listMyBookingOrder:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String session_id = jo.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int campus_area_id = jo.getInt("campus_area_id");
            JSONArray jar = bookingExtend.listMyBookingOrder(sst.UserID, campus_area_id);
            jout.put("booking_order_list", jar);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listbookingenablearea")
    public String listBookingEnableArea(@RequestBody String sReq) {
        System.out.println("-------------- listBookingEnableArea:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            int root_campus_area_id = jo.getInt("root_campus_area_id");
            // Assuming all_level=0 for this specific endpoint as it was in original
            JSONArray jar = bookingExtend.listAvailableBookingArea(root_campus_area_id, 0);
            jout.put("bookingenablearea_list", jar);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listscheduletimeofroom")
    public String listScheduleTimeOfRoom(@RequestBody String sReq) {
        System.out.println("-------------- listScheduleTimeOfRoom:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            String room_code = jo.getString("room_code");
            int campus_area_id = jo.getInt("campus_area_id");
            
            JSONArray scheduleList = bookingExtend.listScheduleTimeOfRoom(campus_area_id, room_code);
            jout.put("schedule_list", scheduleList);
            
            JSONArray extraList = bookingExtend.listExtraSchedule(campus_area_id);
            jout.put("extra_schedule_list", extraList);
            
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listbookingstate")
    public String listBookingState(@RequestBody String sReq) {
        System.out.println("-------------- listBookingState:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONArray jar = bookingExtend.listBookingState();
            jout.put("booking_state_list", jar);
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listavailablebookingarea")
    public String listAvailableBookingArea(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            System.out.println("----------listAvailableBookingArea:" + sReq);
            int root_campus_area_id = jo.getInt("root_campus_area_id");
            int all_level = jo.has("all_level") ? jo.getInt("all_level") : 0;
            JSONArray jar = bookingExtend.listAvailableBookingArea(root_campus_area_id, all_level);
            jout.put("availablebookingarea_list", jar);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }
}
