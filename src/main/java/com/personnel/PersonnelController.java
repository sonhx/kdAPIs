package com.personnel;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/personnel"})
public class PersonnelController {

    private static final Logger log = LoggerFactory.getLogger(PersonnelController.class);

    @Autowired
    private PersonnelSyncScheduler personnelSyncScheduler;

    /**
     * Trigger manual personnel sync from TCNS API.
     */
    @RequestMapping(value = "/sync", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerPersonnelSync() {
        log.info("Manual personnel sync requested.");
        JSONObject syncResult = personnelSyncScheduler.syncPersonnel();
        return ResponseEntity.ok(syncResult.toString());
    }

    /**
     * Get personnel list.
     */
    @RequestMapping(value = "/list", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPersonnelList(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "donViChinhId", required = false) String donViChinhId,
            @RequestParam(value = "donViL3Id", required = false) String donViL3Id) {

        List<Map<String, Object>> list = personnelSyncScheduler.getPersonnelList(includeDeleted, search, donViChinhId, donViL3Id);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("data", list);
        result.put("total", list.size());
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Get total number of lecturers.
     * Lecturers are identified as persons with tenChucVu matching:
     * "Giảng viên", "Giảng viên chính", "Chuyên gia - Giảng viên", or "Giảng viên cao cấp".
     */
    @RequestMapping(value = {"/count-lecturers", "/lecturers/count"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLecturerCount(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "donViChinhId", required = false) String donViChinhId,
            @RequestParam(value = "donViL3Id", required = false) String donViL3Id) {

        int count = personnelSyncScheduler.getLecturerCount(includeDeleted, donViChinhId, donViL3Id);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("totalLecturers", count);
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Get list of lecturers.
     * Lecturers are identified as persons with tenChucVu matching:
     * "Giảng viên", "Giảng viên chính", "Chuyên gia - Giảng viên", or "Giảng viên cao cấp".
     */
    @RequestMapping(value = {"/lecturers"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLecturerList(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "donViChinhId", required = false) String donViChinhId,
            @RequestParam(value = "donViL3Id", required = false) String donViL3Id) {
    	
    	System.out.println("Request received for getLecturerList with parameters: includeDeleted=" + includeDeleted + ", search=" + search + ", donViChinhId=" + donViChinhId + ", donViL3Id=" + donViL3Id);

        List<Map<String, Object>> list = personnelSyncScheduler.getLecturerList(includeDeleted, search, donViChinhId, donViL3Id);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("data", list);
        result.put("total", list.size());
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Get total number of PhD lecturers.
     */
    @RequestMapping(value = {"/count-phd-lecturers", "/lecturers/phd/count"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPhdLecturerCount(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "donViChinhId", required = false) String donViChinhId,
            @RequestParam(value = "donViL3Id", required = false) String donViL3Id) {

        int count = personnelSyncScheduler.getPhdLecturerCount(includeDeleted, donViChinhId, donViL3Id);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("totalPhdLecturers", count);
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Get list of PhD lecturers.
     */
    @RequestMapping(value = {"/lecturers/phd", "/phd-lecturers"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPhdLecturerList(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "donViChinhId", required = false) String donViChinhId,
            @RequestParam(value = "donViL3Id", required = false) String donViL3Id) {

        List<Map<String, Object>> list = personnelSyncScheduler.getPhdLecturerList(includeDeleted, search, donViChinhId, donViL3Id);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("data", list);
        result.put("total", list.size());
        return ResponseEntity.ok(result.toString());
    }
}
