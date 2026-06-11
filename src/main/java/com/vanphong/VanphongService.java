package com.vanphong;

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
@RequestMapping("/vanphong")
public class VanphongService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private VanphongExtend vanphongExtend;

    // EVENT
    @PostMapping("/createevent")
    public String createEvent(@RequestBody String sReq) {
        System.out.println("---------------- createEvent:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String event_name = jsonobjReq.getString("event_name");
            String event_time = jsonobjReq.getString("event_time");

            vanphongExtend.createEvent(event_name, event_time, sst.UserID);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/deleteevent")
    public String deleteEvent(@RequestBody String sReq) {
        System.out.println("----------deleteEvent:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int event_id = jsonobjReq.getInt("event_id");
            vanphongExtend.deleteEvent(event_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listevent")
    public String listEvent(@RequestBody String sReq) {
        System.out.println("----------listEvent:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONArray jaout = vanphongExtend.listEvent();
            jout.put("event_list", jaout);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    // PAPER RECORD
    @PostMapping("/createpaperrecord")
    public String createPaperRecord(@RequestBody String sReq) {
        System.out.println("---------------- createPaperRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String pname = jsonobjReq.getString("paper_name");
            String purl = jsonobjReq.getString("paper_url");
            String ptime = jsonobjReq.getString("public_date");

            vanphongExtend.createPaperRecord(pname, purl, ptime);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/deletepaperrecord")
    public String deletePaperRecord(@RequestBody String sReq) {
        System.out.println("----------deletePaperRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int paper_id = jsonobjReq.getInt("paper_id");
            vanphongExtend.deletePaperRecord(paper_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listpaperrecord")
    public String listPaperRecord(@RequestBody String sReq) {
        System.out.println("----------listPaperRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONArray jaout = vanphongExtend.listPaperRecord();
            jout.put("paper_list", jaout);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    // PORTAL & FANPAGE
    @PostMapping("/createportalandfanpagerecord")
    public String createPortalAndFanpageRecord(@RequestBody String sReq) {
        System.out.println("---------------- createPortalAndFanpageRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int year = jsonobjReq.has("year") ? jsonobjReq.getInt("year") : 0;
            int month = jsonobjReq.has("month") ? jsonobjReq.getInt("month") : 0;
            int portal_counter = jsonobjReq.has("portal_counter") ? jsonobjReq.getInt("portal_counter") : -1;
            int fanpage_counter = jsonobjReq.has("fanpage_counter") ? jsonobjReq.getInt("fanpage_counter") : -1;

            vanphongExtend.createPortalAndFanpageRecord(year, month, portal_counter, fanpage_counter, sst.UserID);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/deleteportalandfanpagerecord")
    public String deletePortalAndFanpageRecord(@RequestBody String sReq) {
        System.out.println("----------deletePortalAndFanpageRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int pr_record_id = jsonobjReq.getInt("pr_record_id");
            vanphongExtend.deletePortalAndFanpageRecord(pr_record_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/updateportalandfanpagerecord")
    public String updatePortalAndFanpageRecord(@RequestBody String sReq) {
        System.out.println("  ------------updatePortalAndFanpageRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            int pr_record_id = jo.getInt("pr_record_id");
            int portal = jo.has("portal") ? jo.getInt("portal") : -1;
            int fanpage = jo.has("fanpage") ? jo.getInt("fanpage") : -1;

            vanphongExtend.updatePortalAndFanpageRecord(pr_record_id, portal, fanpage);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listportalandfanpagerecord")
    public String listPortalAndFanpageRecord(@RequestBody String sReq) {
        System.out.println("----------listPortalAndFanpageRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONArray jaout = vanphongExtend.listPortalAndFanpageRecord();
            jout.put("pnf_record_list", jaout);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    // LIBRARY ACCESS
    @PostMapping("/createlibraryrecord")
    public String createLibraryRecord(@RequestBody String sReq) {
        System.out.println("---------------- createLibraryRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int year = jsonobjReq.has("year") ? jsonobjReq.getInt("year") : 0;
            int month = jsonobjReq.has("month") ? jsonobjReq.getInt("month") : 0;
            int access_counter = jsonobjReq.has("access_counter") ? jsonobjReq.getInt("access_counter") : -1;
            int download_counter = jsonobjReq.has("download_counter") ? jsonobjReq.getInt("download_counter") : -1;

            vanphongExtend.createLibraryRecord(year, month, access_counter, download_counter, sst.UserID);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/deletelibraryrecord")
    public String deleteLibraryRecord(@RequestBody String sReq) {
        System.out.println("----------deleteLibraryRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int pr_record_id = jsonobjReq.getInt("pr_record_id");
            vanphongExtend.deleteLibraryRecord(pr_record_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listlibraryrecord")
    public String listLibraryRecord(@RequestBody String sReq) {
        System.out.println("----------listLibraryRecord:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String session_id = jsonobjReq.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONArray jaout = vanphongExtend.listLibraryRecord();
            jout.put("library_access_record_list", jaout);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        return jout.toString();
    }
}
