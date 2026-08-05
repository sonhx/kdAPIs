package com.orgs;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/orgs", "/khcn/orgs"})
public class OrgManagerController {

    private static final Logger log = LoggerFactory.getLogger(OrgManagerController.class);

    @Autowired
    private OrgManagerService orgManagerService;

    /**
     * Upload orgs.json file via Multipart File.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadOrgsFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                JSONObject err = new JSONObject();
                err.put("status", "ERROR");
                err.put("message", "File upload rỗng.");
                return ResponseEntity.badRequest().body(err.toString());
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            JSONObject result = orgManagerService.syncOrgsFromJsonString(content);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error handling orgs file upload", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Lỗi xử lý file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err.toString());
        }
    }

    /**
     * Upload raw orgs.json content via JSON Body.
     */
    @PostMapping(value = "/upload-json", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadOrgsJsonRaw(@RequestBody String jsonContent) {
        JSONObject result = orgManagerService.syncOrgsFromJsonString(jsonContent);
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Get orgs tree structure.
     */
    @RequestMapping(value = "/tree", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOrgsTree(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "search", required = false) String search) {
        JSONObject treeResult = orgManagerService.getOrgsTree(includeDeleted, search);
        return ResponseEntity.ok(treeResult.toString());
    }

    /**
     * Get orgs flat list.
     */
    @RequestMapping(value = "/list", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOrgsList(
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "search", required = false) String search) {
        List<Map<String, Object>> list = orgManagerService.getOrgsList(includeDeleted, search);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("data", list);
        result.put("total", list.size());
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Manually trigger sync from local orgs.txt.
     */
    @RequestMapping(value = "/sync-initial", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> syncInitial() {
        JSONObject result = orgManagerService.syncInitialLocalFile();
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Set org leader ("Phụ trách đơn vị").
     */
    @PostMapping(value = "/set-leader", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setOrgLeader(@RequestBody Map<String, String> body) {
        String orgId = body.get("orgId");
        String leaderId = body.get("leaderId");
        String leaderName = body.get("leaderName");
        JSONObject result = orgManagerService.setOrgLeader(orgId, leaderId, leaderName);
        return ResponseEntity.ok(result.toString());
    }
}
