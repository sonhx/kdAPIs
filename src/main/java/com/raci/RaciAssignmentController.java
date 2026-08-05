package com.raci;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/raci", "/raci"})
public class RaciAssignmentController {

    @Autowired
    private RaciAssignmentService raciAssignmentService;

    /**
     * GET /api/raci/matrix?namHoc=2025-2026
     */
    @GetMapping("/matrix")
    public ResponseEntity<String> getRaciMatrix(@RequestParam(value = "namHoc", required = false, defaultValue = "2025-2026") String namHoc) {
        List<Map<String, Object>> list = raciAssignmentService.getAssignments(namHoc);
        JSONObject res = new JSONObject();
        res.put("status", "SUCCESS");
        res.put("data", list);
        res.put("total", list.size());
        return ResponseEntity.ok(res.toString());
    }

    /**
     * POST /api/raci/assign
     */
    @PostMapping("/assign")
    public ResponseEntity<String> assignCell(@RequestBody String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            String deptId = body.optString("deptId");
            String compId = body.optString("compId");
            String namHoc = body.optString("namHoc", "2025-2026");
            boolean isR = body.optBoolean("isR", body.optBoolean("r", false));
            boolean isA = body.optBoolean("isA", body.optBoolean("a", false));
            boolean isC = body.optBoolean("isC", body.optBoolean("c", false));
            boolean isI = body.optBoolean("isI", body.optBoolean("i", false));
            int slaDays = body.optInt("slaDays", body.optInt("sla", 15));
            String assignee = body.optString("assignee", "");

            JSONObject result = raciAssignmentService.upsertAssignment(deptId, compId, namHoc, isR, isA, isC, isI, slaDays, assignee);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err.toString());
        }
    }

    /**
     * POST /api/raci/batch-save
     */
    @PostMapping("/batch-save")
    public ResponseEntity<String> batchSave(@RequestBody String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            JSONArray items = body.optJSONArray("items");
            String namHoc = body.optString("namHoc", "2025-2026");

            JSONObject result = raciAssignmentService.batchUpsert(items, namHoc);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err.toString());
        }
    }

    /**
     * POST /api/raci/reset
     * Reset matrix assignments to clean default initial values.
     */
    @PostMapping("/reset")
    public ResponseEntity<String> resetToDefaults() {
        try {
            raciAssignmentService.seedDefaultAssignments(true);
            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("message", "Đã khôi phục ma trận RACI mặc định thành công.");
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err.toString());
        }
    }
}
