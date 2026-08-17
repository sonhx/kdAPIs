package com.capa;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capa.dto.CapaActionDto;
import com.capa.dto.CapaDto;
import com.capa.dto.CapaStatsDto;
import com.session.SessionService;
import com.session.struct_session;

/**
 * CapaController — REST API Controller for the CAPA Module.
 * Base Path: /capa
 * Target Frontend: http://localhost:3000/qa-ims/capa
 */
@RestController
@RequestMapping("/capa")
public class CapaController {

    @Autowired
    private CapaExtend capaExtend;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private com.employees.employeesServices employeesServices;

    /**
     * GET /capa/departments
     * Returns list of Level 3 departments (with exceptions) for CAPA dropdown.
     */
    @GetMapping("/departments")
    public String getCapaDepartments() {
        System.out.println("-------getCapaDepartments");
        JSONObject jout = new JSONObject();
        try {
            JSONArray depts = employeesServices.getCachedDepartments();
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", depts);
            jout.put("departments", depts);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /capa/init-tables
     * Manually trigger table creation and seeding for CAPA module.
     */
	/*@GetMapping("/init-tables")
	public String initTables() {
	    System.out.println("-------initTables manual trigger");
	    JSONObject jout = new JSONObject();
	    try {
	        capaExtend.init();
	        jout.put("code", 200);
	        jout.put("description", "Đã khởi tạo thành công các bảng dbo.capa, dbo.capa_actions, dbo.capa_history và dữ liệu mẫu.");
	    } catch (Exception e) {
	        e.printStackTrace();
	        jout.put("code", 500);
	        jout.put("description", "Lỗi tạo bảng: " + e.getMessage());
	    }
	    return jout.toString();
	}*/

    // =========================================================================
    // 1. LIST / SEARCH CAPAS
    // =========================================================================

    /**
     * GET /capa/list
     * Query Parameters: status, capaType, departmentName, priority, keyword
     */
    @GetMapping("/list")
    public String listCapas(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "capaType", required = false) String capaType,
            @RequestParam(name = "departmentName", required = false) String departmentName,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "keyword", required = false) String keyword) {

        System.out.println("-------listCapas status=" + status + " type=" + capaType + " dept=" + departmentName);
        JSONObject jout = new JSONObject();
        try {
            List<CapaDto> list = capaExtend.listCapas(status, capaType, departmentName, priority, keyword);
            JSONArray arr = new JSONArray();
            for (CapaDto dto : list) {
                arr.put(dtoToJson(dto));
            }
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", arr);
            jout.put("total", arr.length());
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // 2. DASHBOARD COUNTERS & CHARTS
    // =========================================================================

    /**
     * GET /capa/stats
     */
    @GetMapping("/stats")
    public String getStats(@RequestParam(name = "departmentName", required = false) String departmentName) {
        System.out.println("-------getCapaStats departmentName=" + departmentName);
        JSONObject jout = new JSONObject();
        try {
            CapaStatsDto stats = capaExtend.getStats(departmentName);
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("total", stats.getTotal());
            jout.put("pending_closure", stats.getPendingClosure());
            jout.put("processing", stats.getProcessing());
            jout.put("overdue", stats.getOverdue());
            jout.put("closed", stats.getClosed());
            jout.put("effectiveness_rate", stats.getEffectivenessRate());
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /capa/charts/trend
     */
    @GetMapping("/charts/trend")
    public String getMonthlyTrend() {
        System.out.println("-------getMonthlyTrend");
        JSONObject jout = new JSONObject();
        try {
            JSONArray trend = capaExtend.getMonthlyTrend();
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", trend);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /capa/charts/distribution
     */
    @GetMapping("/charts/distribution")
    public String getDepartmentDistribution() {
        System.out.println("-------getDepartmentDistribution");
        JSONObject jout = new JSONObject();
        try {
            JSONArray dist = capaExtend.getDepartmentDistribution();
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", dist);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // 3. GET SINGLE CAPA
    // =========================================================================

    /**
     * GET /capa/{capaId}
     */
    @GetMapping("/{capaId}")
    public String getCapaById(@PathVariable("capaId") Integer capaId) {
        System.out.println("-------getCapaById:" + capaId);
        JSONObject jout = new JSONObject();
        try {
            if (capaId == null || capaId <= 0) {
                jout.put("code", 400);
                jout.put("description", "capaId không hợp lệ");
                return jout.toString();
            }

            CapaDto dto = capaExtend.getCapaById(capaId);
            if (dto == null) {
                jout.put("code", 404);
                jout.put("description", "Không tìm thấy CAPA với ID: " + capaId);
                return jout.toString();
            }

            List<CapaActionDto> actions = capaExtend.getActionsByCapaId(capaId);
            JSONArray actionsArr = new JSONArray();
            for (CapaActionDto a : actions) {
                actionsArr.put(actionDtoToJson(a));
            }

            JSONArray historyArr = capaExtend.getCapaHistory(capaId);

            JSONObject capaJson = dtoToJson(dto);
            capaJson.put("actions", actionsArr);
            capaJson.put("history", historyArr);

            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", capaJson);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // 4. CREATE CAPA
    // =========================================================================

    /**
     * POST /capa/create
     */
    @PostMapping("/create")
    public String createCapa(@RequestBody String sReq) {
        System.out.println("-------createCapa:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);

            String sessionId = jin.optString("session_id", null);
            String createdBy = resolveUserIdFromSession(sessionId);

            String title = jin.optString("title", "").trim();
            if (title.isEmpty()) {
                jout.put("code", 400);
                jout.put("description", "Tiêu đề CAPA không được để trống");
                return jout.toString();
            }

            String dueDate = jin.optString("due_date", null);
            if (dueDate == null || dueDate.isBlank()) {
                jout.put("code", 400);
                jout.put("description", "Hạn hoàn thành không được để trống");
                return jout.toString();
            }

            String openDate = jin.optString("open_date", jin.optString("openDate", null));
            String description = jin.optString("description", "").trim();
            String capaType = jin.optString("capa_type", jin.optString("type", "Khắc phục")).trim();
            String departmentName = jin.optString("department_name", jin.optString("department", "Khoa Công nghệ thông tin")).trim();
            String departmentId = jin.optString("department_id", null);
            String priority = jin.optString("priority", "Medium");
            String source = jin.optString("source", null);
            String rootCause = jin.optString("root_cause", jin.optString("rootCause", null));
            String actionPlan = jin.optString("action_plan", jin.optString("corrective_action", jin.optString("preventive_action", null)));
            String assignee = jin.optString("assignee", jin.optString("assigned_to_name", null));

            int newId = capaExtend.createCapa(title, description, capaType, openDate, dueDate, departmentName, departmentId, priority, source, rootCause, actionPlan, assignee, createdBy);

            if (newId <= 0) {
                jout.put("code", 500);
                jout.put("description", "Không thể tạo CAPA");
                return jout.toString();
            }

            CapaDto created = capaExtend.getCapaById(newId);
            jout.put("code", 201);
            jout.put("description", "Tạo CAPA thành công");
            jout.put("capa_id", newId);
            if (created != null) {
                jout.put("capa_code", created.getCapaCode());
                jout.put("data", dtoToJson(created));
            }

        } catch (JSONException e) {
            e.printStackTrace();
            jout.put("code", 800);
            jout.put("description", "JSON error: Thiếu tham số? " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // 5. WORKFLOW ACTIONS (Pending Closure, Close, Revert with Feedback)
    // =========================================================================

    /**
     * PUT /capa/{capaId}/pending-closure
     * Department reports completion -> moves to pending_closure
     */
    @PutMapping("/{capaId}/pending-closure")
    public String markPendingClosure(@PathVariable("capaId") Integer capaId, @RequestBody(required = false) String sReq) {
        System.out.println("-------markPendingClosure:" + capaId);
        JSONObject jout = new JSONObject();
        try {
            String user = "DepartmentUser";
            if (sReq != null && !sReq.isBlank()) {
                JSONObject jin = new JSONObject(sReq);
                String sessionId = jin.optString("session_id", null);
                user = resolveUserIdFromSession(sessionId);
            }

            boolean ok = capaExtend.markPendingClosure(capaId, user);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Đã báo cáo hoàn thành! Đang chờ TTKT thẩm định." : "Không tìm thấy CAPA");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * PUT /capa/{capaId}/close
     * TTKT approves and closes CAPA (Khép vòng) -> status: closed, effectiveness: Đạt
     */
    @PutMapping("/{capaId}/close")
    public String closeCapa(@PathVariable("capaId") Integer capaId, @RequestBody(required = false) String sReq) {
        System.out.println("-------closeCapa:" + capaId);
        JSONObject jout = new JSONObject();
        try {
            String user = "TTKT_Admin";
            String effectiveness = "Đạt";
            if (sReq != null && !sReq.isBlank()) {
                JSONObject jin = new JSONObject(sReq);
                String sessionId = jin.optString("session_id", null);
                user = resolveUserIdFromSession(sessionId);
                if (jin.has("effectiveness")) {
                    effectiveness = jin.getString("effectiveness");
                }
            }

            boolean ok = capaExtend.closeCapa(capaId, user, effectiveness);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Đã duyệt đóng (Khép vòng) hồ sơ CAPA thành công!" : "Không tìm thấy CAPA");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * PUT /capa/{capaId}/revert
     * TTKT rejects closure and requests improvement -> status: processing, feedback: feedbackComment
     */
    @PutMapping("/{capaId}/revert")
    public String revertCapa(@PathVariable("capaId") Integer capaId, @RequestBody String sReq) {
        System.out.println("-------revertCapa:" + capaId);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String feedbackComment = jin.optString("feedback", jin.optString("comment", "")).trim();
            if (feedbackComment.isEmpty()) {
                jout.put("code", 400);
                jout.put("description", "Bắt buộc phải nhập ý kiến cải tiến chi tiết!");
                return jout.toString();
            }

            String sessionId = jin.optString("session_id", null);
            String user = resolveUserIdFromSession(sessionId);

            boolean ok = capaExtend.revertCapa(capaId, user, feedbackComment);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Đã từ chối đóng hồ sơ CAPA. Yêu cầu cải tiến đã được chuyển cho đơn vị." : "Không tìm thấy CAPA");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // 6. DELETE
    // =========================================================================

    /**
     * DELETE /capa/{capaId}
     */
    @DeleteMapping("/{capaId}")
    public String deleteCapa(@PathVariable("capaId") Integer capaId, @RequestBody(required = false) String sReq) {
        System.out.println("-------deleteCapa:" + capaId);
        JSONObject jout = new JSONObject();
        try {
            String deletedBy = "system";
            if (sReq != null && !sReq.isBlank()) {
                JSONObject jin = new JSONObject(sReq);
                String sessionId = jin.optString("session_id", null);
                deletedBy = resolveUserIdFromSession(sessionId);
            }

            boolean ok = capaExtend.deleteCapa(capaId, deletedBy);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Xóa CAPA thành công" : "Không tìm thấy CAPA với ID: " + capaId);

        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // 7. CAPA SOURCE DEFINITIONS (/capa/sources)
    // =========================================================================

    /**
     * GET /capa/sources
     * Query param: activeOnly (optional, default false)
     */
    @GetMapping("/sources")
    public String listCapaSources(@RequestParam(name = "activeOnly", required = false, defaultValue = "false") Boolean activeOnly) {
        System.out.println("-------listCapaSources activeOnly=" + activeOnly);
        JSONObject jout = new JSONObject();
        try {
            List<com.capa.dto.CapaSourceDto> sources = capaExtend.listSources(activeOnly);
            JSONArray arr = new JSONArray();
            for (com.capa.dto.CapaSourceDto dto : sources) {
                JSONObject sJson = new JSONObject();
                sJson.put("source_id", dto.getSourceId());
                sJson.put("source_code", dto.getSourceCode());
                sJson.put("source_name", dto.getSourceName());
                sJson.put("description", dto.getDescription() != null ? dto.getDescription() : "");
                sJson.put("sort_order", dto.getSortOrder());
                sJson.put("is_active", dto.getIsActive());
                sJson.put("created_at", dto.getCreatedAt());
                sJson.put("updated_at", dto.getUpdatedAt());
                arr.put(sJson);
            }
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", arr);
            jout.put("total", arr.length());
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /capa/sources/{sourceId}
     */
    @GetMapping("/sources/{sourceId}")
    public String getCapaSourceById(@PathVariable("sourceId") Integer sourceId) {
        System.out.println("-------getCapaSourceById:" + sourceId);
        JSONObject jout = new JSONObject();
        try {
            if (sourceId == null || sourceId <= 0) {
                jout.put("code", 400);
                jout.put("description", "sourceId không hợp lệ");
                return jout.toString();
            }
            com.capa.dto.CapaSourceDto dto = capaExtend.getSourceById(sourceId);
            if (dto == null) {
                jout.put("code", 404);
                jout.put("description", "Không tìm thấy định nghĩa nguồn CAPA với ID: " + sourceId);
                return jout.toString();
            }
            JSONObject sJson = new JSONObject();
            sJson.put("source_id", dto.getSourceId());
            sJson.put("source_code", dto.getSourceCode());
            sJson.put("source_name", dto.getSourceName());
            sJson.put("description", dto.getDescription() != null ? dto.getDescription() : "");
            sJson.put("sort_order", dto.getSortOrder());
            sJson.put("is_active", dto.getIsActive());
            sJson.put("created_at", dto.getCreatedAt());
            sJson.put("updated_at", dto.getUpdatedAt());

            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", sJson);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /capa/sources
     */
    @PostMapping("/sources")
    public String createCapaSource(@RequestBody String sReq) {
        System.out.println("-------createCapaSource:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sourceName = jin.optString("source_name", jin.optString("name", "")).trim();
            if (sourceName.isEmpty()) {
                jout.put("code", 400);
                jout.put("description", "Tên nguồn CAPA (source_name) không được để trống");
                return jout.toString();
            }

            String sourceCode = jin.optString("source_code", jin.optString("code", null));
            String description = jin.optString("description", null);
            Integer sortOrder = jin.has("sort_order") ? jin.getInt("sort_order") : (jin.has("sortOrder") ? jin.getInt("sortOrder") : null);

            int newId = capaExtend.createSource(sourceCode, sourceName, description, sortOrder);
            if (newId <= 0) {
                jout.put("code", 500);
                jout.put("description", "Không thể tạo định nghĩa nguồn CAPA");
                return jout.toString();
            }

            com.capa.dto.CapaSourceDto created = capaExtend.getSourceById(newId);
            jout.put("code", 201);
            jout.put("description", "Tạo định nghĩa nguồn CAPA thành công");
            jout.put("source_id", newId);
            if (created != null) {
                JSONObject sJson = new JSONObject();
                sJson.put("source_id", created.getSourceId());
                sJson.put("source_code", created.getSourceCode());
                sJson.put("source_name", created.getSourceName());
                sJson.put("description", created.getDescription() != null ? created.getDescription() : "");
                sJson.put("sort_order", created.getSortOrder());
                sJson.put("is_active", created.getIsActive());
                sJson.put("created_at", created.getCreatedAt());
                sJson.put("updated_at", created.getUpdatedAt());
                jout.put("data", sJson);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            jout.put("code", 800);
            jout.put("description", "Lỗi định dạng JSON request: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * PUT /capa/sources/{sourceId}
     */
    @PutMapping("/sources/{sourceId}")
    public String updateCapaSource(@PathVariable("sourceId") Integer sourceId, @RequestBody String sReq) {
        System.out.println("-------updateCapaSource:" + sourceId + " payload=" + sReq);
        JSONObject jout = new JSONObject();
        try {
            if (sourceId == null || sourceId <= 0) {
                jout.put("code", 400);
                jout.put("description", "sourceId không hợp lệ");
                return jout.toString();
            }
            JSONObject jin = new JSONObject(sReq);
            String sourceCode = jin.optString("source_code", jin.optString("code", null));
            String sourceName = jin.optString("source_name", jin.optString("name", null));
            String description = jin.has("description") ? jin.getString("description") : null;
            Integer sortOrder = jin.has("sort_order") ? jin.getInt("sort_order") : (jin.has("sortOrder") ? jin.getInt("sortOrder") : null);
            Boolean isActive = jin.has("is_active") ? jin.getBoolean("is_active") : (jin.has("isActive") ? jin.getBoolean("isActive") : null);

            boolean ok = capaExtend.updateSource(sourceId, sourceCode, sourceName, description, sortOrder, isActive);
            if (!ok) {
                jout.put("code", 404);
                jout.put("description", "Không tìm thấy hoặc không thể cập nhật nguồn CAPA với ID: " + sourceId);
                return jout.toString();
            }

            com.capa.dto.CapaSourceDto updated = capaExtend.getSourceById(sourceId);
            jout.put("code", 200);
            jout.put("description", "Cập nhật nguồn CAPA thành công");
            if (updated != null) {
                JSONObject sJson = new JSONObject();
                sJson.put("source_id", updated.getSourceId());
                sJson.put("source_code", updated.getSourceCode());
                sJson.put("source_name", updated.getSourceName());
                sJson.put("description", updated.getDescription() != null ? updated.getDescription() : "");
                sJson.put("sort_order", updated.getSortOrder());
                sJson.put("is_active", updated.getIsActive());
                sJson.put("created_at", updated.getCreatedAt());
                sJson.put("updated_at", updated.getUpdatedAt());
                jout.put("data", sJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * DELETE /capa/sources/{sourceId}
     */
    @DeleteMapping("/sources/{sourceId}")
    public String deleteCapaSource(@PathVariable("sourceId") Integer sourceId) {
        System.out.println("-------deleteCapaSource:" + sourceId);
        JSONObject jout = new JSONObject();
        try {
            if (sourceId == null || sourceId <= 0) {
                jout.put("code", 400);
                jout.put("description", "sourceId không hợp lệ");
                return jout.toString();
            }
            boolean ok = capaExtend.deleteSource(sourceId);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Xóa định nghĩa nguồn CAPA thành công" : "Không tìm thấy nguồn CAPA với ID: " + sourceId);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String resolveUserIdFromSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "system";
        try {
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst != null) return String.valueOf(sst.UserID);
        } catch (Exception ignored) {}
        return "system";
    }

    private JSONObject dtoToJson(CapaDto dto) {
        JSONObject obj = new JSONObject();
        obj.put("id", dto.getCapaCode()); // Compatible with frontend id: 'CAPA-26-001'
        obj.put("capa_id", dto.getCapaId());
        obj.put("capa_code", dto.getCapaCode());
        obj.put("title", dto.getTitle());
        obj.put("department", dto.getDepartmentName());
        obj.put("department_name", dto.getDepartmentName());
        obj.put("department_id", dto.getDepartmentId() != null ? dto.getDepartmentId() : JSONObject.NULL);
        obj.put("type", dto.getCapaType());
        obj.put("capa_type", dto.getCapaType());
        obj.put("status", dto.getStatus());
        obj.put("openDate", dto.getOpenDate() != null ? dto.getOpenDate() : JSONObject.NULL);
        obj.put("dueDate", dto.getDueDate() != null ? dto.getDueDate() : JSONObject.NULL);
        obj.put("completedDate", dto.getCompletedDate() != null ? dto.getCompletedDate() : JSONObject.NULL);
        obj.put("effectiveness", dto.getEffectivenessStatus() != null ? dto.getEffectivenessStatus() : "Đang đánh giá");
        obj.put("description", dto.getDescription() != null ? dto.getDescription() : "");
        obj.put("feedback", dto.getFeedback() != null ? dto.getFeedback() : "");
        obj.put("priority", dto.getPriority());
        obj.put("source", dto.getSource() != null ? dto.getSource() : JSONObject.NULL);
        obj.put("source_ref", dto.getSourceRef() != null ? dto.getSourceRef() : JSONObject.NULL);
        obj.put("root_cause", dto.getRootCause() != null ? dto.getRootCause() : JSONObject.NULL);
        obj.put("action_plan", dto.getActionPlan() != null ? dto.getActionPlan() : JSONObject.NULL);
        obj.put("created_at", dto.getCreatedAt());
        obj.put("updated_at", dto.getUpdatedAt());
        return obj;
    }

    private JSONObject actionDtoToJson(CapaActionDto a) {
        JSONObject obj = new JSONObject();
        obj.put("action_id", a.getActionId());
        obj.put("capa_id", a.getCapaId());
        obj.put("action_description", a.getActionDescription());
        obj.put("assigned_to", a.getAssignedTo() != null ? a.getAssignedTo() : JSONObject.NULL);
        obj.put("assigned_to_name", a.getAssignedToName() != null ? a.getAssignedToName() : JSONObject.NULL);
        obj.put("due_date", a.getDueDate() != null ? a.getDueDate() : JSONObject.NULL);
        obj.put("completed_date", a.getCompletedDate() != null ? a.getCompletedDate() : JSONObject.NULL);
        obj.put("status", a.getStatus());
        obj.put("sort_order", a.getSortOrder() != null ? a.getSortOrder() : JSONObject.NULL);
        obj.put("created_at", a.getCreatedAt());
        obj.put("updated_at", a.getUpdatedAt());
        obj.put("notes", a.getNotes() != null ? a.getNotes() : JSONObject.NULL);
        return obj;
    }
}
