package com.plan;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.session.SessionService;
import com.session.struct_session;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/plan")
public class PlanService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @PostMapping("/getplan")
    public String getPlan(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            if (sReq == null || sReq.trim().isEmpty()) {
                return "{\"code\":400,\"message\":\"Request body is empty\"}";
            }
            JSONObject jsonobjReq = new JSONObject(sReq.trim());
            String plan_type = jsonobjReq.getString("plan_type");
            JSONArray jar_all = new JSONArray();

            for (int year = 2018; year <= 2029; year++) {
                JSONObject event_obj = new JSONObject();
                event_obj.put("value", year);
                
                String sql = "SELECT * FROM TBL_PLAN where Type=? and (IsDeleted=0 or IsDeleted is null) order by CreatedTime Desc";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, plan_type);
                
                JSONArray jar = new JSONArray();
                for (Map<String, Object> row : rows) {
                    Date mountedTime = (Date) row.get("MountedTime");
                    if (mountedTime == null || (mountedTime.getYear() + 1900) != year) continue;

                    JSONObject obj = new JSONObject();
                    int planId = (int) row.get("ID");
                    obj.put("id", planId);
                    obj.put("name", row.get("Name"));
                    obj.put("desc", row.get("Description"));
                    obj.put("mounted_time", row.get("MountedTime"));
                    obj.put("created_time", row.get("CreatedTime"));

                    List<Map<String, Object>> docRows = jdbcTemplate.queryForList("select * from tbl_plan_doc where PlanID=? and (IsDeleted=0 or IsDeleted is null)", planId);
                    JSONArray jarDocs = new JSONArray();
                    for (Map<String, Object> docRow : docRows) {
                        JSONObject docObj = new JSONObject();
                        docObj.put("id", docRow.get("ID"));
                        docObj.put("name", docRow.get("Name"));
                        docObj.put("file_url", docRow.get("FileUrl"));
                        jarDocs.put(docObj);
                    }
                    obj.put("doc_list", jarDocs);
                    jar.put(obj);
                }
                event_obj.put("event_list", jar);
                jar_all.put(event_obj);
            }
            jout.put("plan_list", jar_all);
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500}";
        }
        return jout.toString();
    }

    @PostMapping("/adddoc")
    public String addDoc(
            @RequestParam("files") MultipartFile file,
            @RequestParam("attach") String attach,
            HttpServletRequest request) {

        try {
            if (attach == null || attach.trim().isEmpty()) {
                return "{\"code\":400,\"message\":\"attach parameter is empty\"}";
            }
            JSONObject jsonobjReq = new JSONObject(attach.trim());
            struct_session sst = sessionService.getSessionInfo(jsonobjReq.getString("session_id"));
            if (sst == null) return "{\"code\":700}";

            String UrlRoot = request.getScheme() + "://" + request.getServerName();
            if (UrlRoot.contains("localhost")) UrlRoot = "http://localhost:8080";
            
            String sServerPath = System.getProperty("catalina.base");
            if (sServerPath == null) sServerPath = ".";

            String filename = file.getOriginalFilename();
            String uploadedFileLocation = sServerPath + "/webapps/files/ptitioc_upload/" + filename;
            File targetFile = new File(uploadedFileLocation);
            if (!targetFile.getParentFile().exists()) targetFile.getParentFile().mkdirs();
            file.transferTo(targetFile);

            String fileUrl = UrlRoot + "/files/ptitioc_upload/" + filename;
            int plan_id = jsonobjReq.getInt("plan_id");
            String doc_name = jsonobjReq.optString("doc_name", "");

            jdbcTemplate.update("insert into TBL_PLAN_DOC (Name,FileUrl,PlanID) values (?, ?, ?)", doc_name, fileUrl, plan_id);
            return new JSONObject().put("code", 200).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":300}";
        }
    }

    @PostMapping("/addevent")
    public String addEvent(@RequestBody String sReq) {
        try {
            if (sReq == null || sReq.trim().isEmpty()) {
                return "{\"code\":400,\"message\":\"Request body is empty\"}";
            }
            JSONObject jsonobjReq = new JSONObject(sReq.trim());
            struct_session sst = sessionService.getSessionInfo(jsonobjReq.getString("session_id"));
            if (sst == null) return "{\"code\":700}";

            String event_name = jsonobjReq.getString("event_name");
            String event_desc = jsonobjReq.optString("event_desc", "");
            String event_time = jsonobjReq.getString("event_time"); // ISO format expected
            String plan_type = jsonobjReq.getString("plan_type");

            SimpleDateFormat isoSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            Date d_mounted_time = isoSdf.parse(event_time);

            jdbcTemplate.update("insert into TBL_PLAN (Name,Description,Type,MountedTime,CreatedTime) values (?, ?, ?, ?, ?)",
                    event_name, event_desc, plan_type, d_mounted_time, new Date());
            
            return new JSONObject().put("code", 200).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500}";
        }
    }

    @PostMapping("/deleteevent")
    public String deleteEvent(@RequestBody String sReq) {
        try {
            if (sReq == null || sReq.trim().isEmpty()) {
                return "{\"code\":400,\"message\":\"Request body is empty\"}";
            }
            JSONObject jin = new JSONObject(sReq.trim());
            if (sessionService.getSessionInfo(jin.getString("session_id")) == null) return "{\"code\":700}";
            jdbcTemplate.update("update TBL_PLAN set IsDeleted=1 where ID=?", jin.getInt("event_id"));
            return "{\"code\":200}";
        } catch (Exception e) { return "{\"code\":500}"; }
    }

    @PostMapping("/deletedoc")
    public String deleteDoc(@RequestBody String sReq) {
        try {
            if (sReq == null || sReq.trim().isEmpty()) {
                return "{\"code\":400,\"message\":\"Request body is empty\"}";
            }
            JSONObject jin = new JSONObject(sReq.trim());
            if (sessionService.getSessionInfo(jin.getString("session_id")) == null) return "{\"code\":700}";
            jdbcTemplate.update("update TBL_PLAN_DOC set IsDeleted=1 where ID=?", jin.getInt("doc_id"));
            return "{\"code\":200}";
        } catch (Exception e) { return "{\"code\":500}"; }
    }
}
