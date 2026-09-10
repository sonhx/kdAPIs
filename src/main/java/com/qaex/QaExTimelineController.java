package com.qaex;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timeline")
@CrossOrigin(originPatterns = "*", maxAge = 3600, allowCredentials = "true")
public class QaExTimelineController {

    private static final Logger log = LoggerFactory.getLogger(QaExTimelineController.class);

    @Autowired(required = false)
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private JdbcTemplate getJdbc() {
        return evidenceJdbcTemplate != null ? evidenceJdbcTemplate : jdbcTemplate;
    }

    /**
     * Automatically create database tables for Timeline & Milestones on application startup.
     */
	/* @PostConstruct
	public void autoInitSchema() {
	    java.util.concurrent.CompletableFuture.runAsync(() -> {
	        try {
	            Thread.sleep(2000);
	            JdbcTemplate jdbc = getJdbc();
	            if (jdbc == null) {
	                log.warn("[QA-EX Timeline] JdbcTemplate not ready yet.");
	                return;
	            }
	
	            // 1. Create dbo.qa_ex_timelines
	            String sqlTimelines =
	                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_timelines') AND type = 'U') " +
	                "BEGIN " +
	                "    CREATE TABLE dbo.qa_ex_timelines ( " +
	                "        TimelineID          INT IDENTITY(1,1) PRIMARY KEY, " +
	                "        KdID                INT           NOT NULL, " +
	                "        Title               NVARCHAR(200) NOT NULL, " +
	                "        Description         NVARCHAR(MAX) NULL, " +
	                "        Status              NVARCHAR(50)  NOT NULL DEFAULT 'Active', " +
	                "        CreatedAt           DATETIME2     NOT NULL DEFAULT GETDATE(), " +
	                "        UpdatedAt           DATETIME2     NOT NULL DEFAULT GETDATE() " +
	                "    ); " +
	                "    CREATE UNIQUE NONCLUSTERED INDEX UQ_qa_ex_timelines_KdID ON dbo.qa_ex_timelines (KdID); " +
	                "END";
	            jdbc.execute(sqlTimelines);
	
	            // 2. Create dbo.qa_ex_timeline_stages
	            String sqlStages =
	                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_timeline_stages') AND type = 'U') " +
	                "BEGIN " +
	                "    CREATE TABLE dbo.qa_ex_timeline_stages ( " +
	                "        StageID             INT IDENTITY(1,1) PRIMARY KEY, " +
	                "        TimelineID          INT           NOT NULL, " +
	                "        KdID                INT           NOT NULL, " +
	                "        StageName           NVARCHAR(100) NOT NULL, " +
	                "        Code                NVARCHAR(50)  NOT NULL, " +
	                "        StartDate           VARCHAR(20)   NOT NULL, " +
	                "        EndDate             VARCHAR(20)   NOT NULL, " +
	                "        Color               NVARCHAR(30)  NULL DEFAULT '#3B82F6', " +
	                "        OrderIndex          INT           NOT NULL DEFAULT 1, " +
	                "        Description         NVARCHAR(500) NULL " +
	                "    ); " +
	                "    CREATE NONCLUSTERED INDEX IX_qa_ex_timeline_stages_KdID ON dbo.qa_ex_timeline_stages (KdID); " +
	                "END";
	            jdbc.execute(sqlStages);
	
	            // 3. Create dbo.qa_ex_timeline_milestones
	            String sqlMilestones =
	                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_timeline_milestones') AND type = 'U') " +
	                "BEGIN " +
	                "    CREATE TABLE dbo.qa_ex_timeline_milestones ( " +
	                "        MilestoneID         INT IDENTITY(1,1) PRIMARY KEY, " +
	                "        TimelineID          INT           NOT NULL, " +
	                "        StageID             INT           NULL, " +
	                "        KdID                INT           NOT NULL, " +
	                "        Title               NVARCHAR(250) NOT NULL, " +
	                "        Description         NVARCHAR(MAX) NULL, " +
	                "        MilestoneDate       VARCHAR(20)   NOT NULL, " +
	                "        Time                VARCHAR(10)   NULL DEFAULT '08:00', " +
	                "        Status              NVARCHAR(50)  NOT NULL DEFAULT 'Pending', " +
	                "        Scope               NVARCHAR(20)  NOT NULL DEFAULT 'Shared', " +
	                "        UserID              INT           NULL, " +
	                "        IsCritical          BIT           NOT NULL DEFAULT 0, " +
	                "        OrderIndex          INT           NOT NULL DEFAULT 1, " +
	                "        OwnerName           NVARCHAR(150) NULL, " +
	                "        CreatedAt           DATETIME2     NOT NULL DEFAULT GETDATE(), " +
	                "        UpdatedAt           DATETIME2     NOT NULL DEFAULT GETDATE() " +
	                "    ); " +
	                "    CREATE NONCLUSTERED INDEX IX_qa_ex_timeline_milestones_KdID ON dbo.qa_ex_timeline_milestones (KdID, Scope); " +
	                "END";
	            jdbc.execute(sqlMilestones);
	
	            // 4. Create dbo.qa_ex_timeline_task_mappings
	            String sqlTaskMappings =
	                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_timeline_task_mappings') AND type = 'U') " +
	                "BEGIN " +
	                "    CREATE TABLE dbo.qa_ex_timeline_task_mappings ( " +
	                "        MappingID           INT IDENTITY(1,1) PRIMARY KEY, " +
	                "        MilestoneID         INT           NOT NULL, " +
	                "        TaskID              VARCHAR(100)  NOT NULL, " +
	                "        TaskTitle           NVARCHAR(250) NOT NULL, " +
	                "        Assignee            NVARCHAR(100) NULL, " +
	                "        Status              NVARCHAR(50)  NOT NULL DEFAULT 'Pending', " +
	                "        DueDate             VARCHAR(20)   NULL, " +
	                "        CreatedAt           DATETIME2     NOT NULL DEFAULT GETDATE() " +
	                "    ); " +
	                "    CREATE NONCLUSTERED INDEX IX_qa_ex_timeline_task_mappings_MsID ON dbo.qa_ex_timeline_task_mappings (MilestoneID); " +
	                "END";
	            jdbc.execute(sqlTaskMappings);
	
	            log.info("[QA-EX Timeline] Database tables initialized successfully.");
	        } catch (Exception e) {
	            log.error("[QA-EX Timeline] Schema initialization notice: ", e);
	        }
	    });
	}*/

    /**
     * Helper to fetch or initialize timeline details for a KdID.
     */
    private ResponseEntity<String> processGetTimelineDetail(int kdId) {
        JSONObject res = new JSONObject();
        try {
            JdbcTemplate jdbc = getJdbc();
            if (jdbc == null) {
                res.put("code", 500);
                res.put("status", "ERROR");
                res.put("message", "Database connection unavailable");
                return ResponseEntity.status(500).body(res.toString());
            }

            // Check if timeline exists for KdID
            String checkTimelineSql = "SELECT TimelineID, KdID, Title, Description FROM dbo.qa_ex_timelines WHERE KdID = ?";
            List<Map<String, Object>> timelines = jdbc.queryForList(checkTimelineSql, kdId);

            if (timelines.isEmpty()) {
                // Return blank timeline object if no data exists for this KdID
                JSONObject emptyTimelineData = new JSONObject();
                emptyTimelineData.put("kdId", kdId);
                emptyTimelineData.put("timelineId", JSONObject.NULL);
                emptyTimelineData.put("title", "Tiến trình Kiểm định (KdID " + kdId + ")");
                emptyTimelineData.put("stages", new JSONArray());
                emptyTimelineData.put("milestones", new JSONArray());

                res.put("code", 200);
                res.put("status", "SUCCESS");
                res.put("timeline", emptyTimelineData);
                return ResponseEntity.ok(res.toString());
            }

            int timelineId = (Integer) timelines.get(0).get("TimelineID");
            String title = (String) timelines.get(0).get("Title");

            // Query stages
            String stagesSql = "SELECT StageID, Code, StageName, StartDate, EndDate, Color, OrderIndex, Description " +
                               "FROM dbo.qa_ex_timeline_stages WHERE KdID = ? ORDER BY OrderIndex ASC, StageID ASC";
            List<Map<String, Object>> stages = jdbc.queryForList(stagesSql, kdId);

            // Query milestones
            String milestonesSql = "SELECT MilestoneID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, UserID, IsCritical, OrderIndex, OwnerName " +
                                   "FROM dbo.qa_ex_timeline_milestones WHERE KdID = ? ORDER BY MilestoneDate ASC, OrderIndex ASC";
            List<Map<String, Object>> milestones = jdbc.queryForList(milestonesSql, kdId);

            // Format timeline response object
            JSONObject timelineData = new JSONObject();
            timelineData.put("kdId", kdId);
            timelineData.put("timelineId", timelineId);
            timelineData.put("title", title != null ? title : ("Tiến trình Kiểm định (KdID " + kdId + ")"));
            timelineData.put("stages", new JSONArray(stages));
            timelineData.put("milestones", new JSONArray(milestones));

            res.put("code", 200);
            res.put("status", "SUCCESS");
            res.put("timeline", timelineData);
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error retrieving timeline detail for kdId=" + kdId + ": ", e);
            res.put("code", 500);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * GET /timeline/detail/{kdId}
     */
    @GetMapping(value = "/detail/{kdId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTimelineDetailByPath(@PathVariable("kdId") int kdId) {
        return processGetTimelineDetail(kdId);
    }

    /**
     * POST /timeline/detail
     * Request Body: { "kd_id": 2027 } or { "kdId": 2027 }
     */
    @PostMapping(value = "/detail", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTimelineDetailByPost(@RequestBody String requestBody) {
        try {
            JSONObject body = new JSONObject(requestBody);
            int kdId = body.optInt("kd_id", body.optInt("kdId", body.optInt("KdID", 1)));
            return processGetTimelineDetail(kdId);
        } catch (Exception e) {
            return processGetTimelineDetail(1);
        }
    }

    /**
     * POST /timeline/import
     * Parse setup file stages & milestones and persist to DB tables.
     */
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> importTimeline(@RequestBody String requestBody) {
        JSONObject res = new JSONObject();
        try {
            JdbcTemplate jdbc = getJdbc();
            if (jdbc == null) {
                res.put("code", 500);
                res.put("status", "ERROR");
                res.put("message", "Database connection unavailable");
                return ResponseEntity.status(500).body(res.toString());
            }

            JSONObject body = new JSONObject(requestBody);
            int kdId = body.optInt("KdID", body.optInt("kdId", body.optInt("kd_id", 1)));

            // 1. Ensure timeline record exists
            String checkTimelineSql = "SELECT TimelineID FROM dbo.qa_ex_timelines WHERE KdID = ?";
            List<Map<String, Object>> timelines = jdbc.queryForList(checkTimelineSql, kdId);
            int timelineId;
            if (timelines.isEmpty()) {
                String insertTimelineSql = "INSERT INTO dbo.qa_ex_timelines (KdID, Title, Description) VALUES (?, ?, ?)";
                jdbc.update(insertTimelineSql, kdId, "Tiến trình Kiểm định (KdID " + kdId + ")", "Lộ trình kiểm định nhập từ Excel");
                timelineId = jdbc.queryForObject("SELECT TimelineID FROM dbo.qa_ex_timelines WHERE KdID = ?", Integer.class, kdId);
            } else {
                timelineId = (Integer) timelines.get(0).get("TimelineID");
            }

            // 2. Import Stages
            if (body.has("stages")) {
                JSONArray stagesArr = body.getJSONArray("stages");
                // Clear existing stages for this KdID
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", kdId);

                for (int i = 0; i < stagesArr.length(); i++) {
                    JSONObject s = stagesArr.getJSONObject(i);
                    String sql = "INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbc.update(sql,
                        timelineId,
                        kdId,
                        s.optString("StageName", "Giai đoạn " + (i + 1)),
                        s.optString("Code", "STAGE_" + (i + 1)),
                        s.optString("StartDate", "2026-08-01"),
                        s.optString("EndDate", "2026-08-15"),
                        s.optString("Color", "#3B82F6"),
                        s.optInt("OrderIndex", i + 1),
                        s.optString("Description", "")
                    );
                }
            }

            // 3. Import Milestones
            if (body.has("milestones")) {
                JSONArray msArr = body.getJSONArray("milestones");
                // Clear existing milestones for this KdID
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_milestones WHERE KdID = ?", kdId);

                // Fetch newly inserted stages mapping (Code -> StageID)
                List<Map<String, Object>> insertedStages = jdbc.queryForList("SELECT StageID, Code FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", kdId);
                Map<String, Integer> stageCodeMap = new HashMap<>();
                for (Map<String, Object> st : insertedStages) {
                    stageCodeMap.put(st.get("Code").toString(), (Integer) st.get("StageID"));
                }

                for (int i = 0; i < msArr.length(); i++) {
                    JSONObject m = msArr.getJSONObject(i);
                    String stageCode = m.optString("StageCode", "");
                    Integer stageId = stageCodeMap.get(stageCode);
                    if (stageId == null && !insertedStages.isEmpty()) {
                        stageId = (Integer) insertedStages.get(0).get("StageID");
                    }

                    String sql = "INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, UserID, IsCritical, OrderIndex, OwnerName) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbc.update(sql,
                        timelineId,
                        stageId,
                        kdId,
                        m.optString("Title", "Mốc thời gian " + (i + 1)),
                        m.optString("Description", ""),
                        m.optString("MilestoneDate", "2026-08-24"),
                        m.optString("Time", "08:00"),
                        m.optString("Status", "Pending"),
                        m.optString("Scope", "Shared"),
                        m.has("UserID") ? m.optInt("UserID") : null,
                        m.optInt("IsCritical", 0),
                        m.optInt("OrderIndex", i + 1),
                        m.optString("OwnerName", "Ban Thư ký TĐG")
                    );
                }
            }

            res.put("code", 200);
            res.put("status", "SUCCESS");
            res.put("message", "Timeline setup imported and saved to database successfully.");
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error importing timeline setup: ", e);
            res.put("code", 500);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * POST /timeline/milestone/create
     * Create a single new milestone in SQL Server DB.
     */
    @PostMapping(value = "/milestone/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createMilestone(@RequestBody String requestBody) {
        JSONObject res = new JSONObject();
        try {
            JdbcTemplate jdbc = getJdbc();
            if (jdbc == null) {
                res.put("code", 500);
                res.put("status", "ERROR");
                res.put("message", "Database connection unavailable");
                return ResponseEntity.status(500).body(res.toString());
            }

            JSONObject body = new JSONObject(requestBody);
            int kdId = body.optInt("KdID", body.optInt("kdId", body.optInt("kd_id", 1)));

            // 1. Ensure main timeline record exists
            String checkTimelineSql = "SELECT TimelineID FROM dbo.qa_ex_timelines WHERE KdID = ?";
            List<Map<String, Object>> timelines = jdbc.queryForList(checkTimelineSql, kdId);
            int timelineId;
            if (timelines.isEmpty()) {
                String insertTimelineSql = "INSERT INTO dbo.qa_ex_timelines (KdID, Title, Description) VALUES (?, ?, ?)";
                jdbc.update(insertTimelineSql, kdId, "Tiến trình Kiểm định (KdID " + kdId + ")", "Tạo mốc thời gian thủ công");
                timelineId = jdbc.queryForObject("SELECT TimelineID FROM dbo.qa_ex_timelines WHERE KdID = ?", Integer.class, kdId);
            } else {
                timelineId = (Integer) timelines.get(0).get("TimelineID");
            }

            // Extract fields
            Object stageIdObj = body.has("StageID") ? body.get("StageID") : body.opt("stageId");
            Integer stageId = (stageIdObj != null && !stageIdObj.toString().isEmpty()) ? Integer.parseInt(stageIdObj.toString()) : null;

            String title = body.optString("Title", body.optString("title", "Mốc thời gian mới"));
            String description = body.optString("Description", body.optString("description", ""));
            String milestoneDate = body.optString("MilestoneDate", body.optString("milestoneDate", "2026-08-26"));
            String time = body.optString("Time", body.optString("time", "08:00"));
            String status = body.optString("Status", body.optString("status", "Pending"));
            String scope = body.optString("Scope", body.optString("scope", "Shared"));
            int isCritical = body.optInt("IsCritical", body.optInt("isCritical", 0));
            String ownerName = body.optString("OwnerName", body.optString("ownerName", "Ban Thư ký TĐG"));
            int orderIndex = body.optInt("OrderIndex", body.optInt("orderIndex", 1));

            String insertSql = "INSERT INTO dbo.qa_ex_timeline_milestones " +
                               "(TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbc.update(insertSql, timelineId, stageId, kdId, title, description, milestoneDate, time, status, scope, isCritical, orderIndex, ownerName);

            // Fetch newly created ID
            Integer newMilestoneId = jdbc.queryForObject("SELECT MAX(MilestoneID) FROM dbo.qa_ex_timeline_milestones WHERE KdID = ?", Integer.class, kdId);

            res.put("code", 200);
            res.put("status", "SUCCESS");
            res.put("message", "Milestone created successfully.");
            res.put("milestoneId", newMilestoneId != null ? newMilestoneId : 0);
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error creating timeline milestone: ", e);
            res.put("code", 500);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * POST /timeline/milestone/update-status
     * Update milestone status.
     */
    @PostMapping(value = "/milestone/update-status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateMilestoneStatus(@RequestBody String requestBody) {
        JSONObject res = new JSONObject();
        try {
            JdbcTemplate jdbc = getJdbc();
            if (jdbc == null) {
                res.put("code", 500);
                res.put("status", "ERROR");
                res.put("message", "Database connection unavailable");
                return ResponseEntity.status(500).body(res.toString());
            }

            JSONObject body = new JSONObject(requestBody);
            int milestoneId = body.optInt("milestoneId", body.optInt("MilestoneID", 0));
            String status = body.optString("status", body.optString("Status", "Completed"));

            String sql = "UPDATE dbo.qa_ex_timeline_milestones SET Status = ?, UpdatedAt = GETDATE() WHERE MilestoneID = ?";
            int rows = jdbc.update(sql, status, milestoneId);

            res.put("code", 200);
            res.put("status", "SUCCESS");
            res.put("message", rows > 0 ? "Status updated successfully." : "Milestone not found.");
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error updating milestone status: ", e);
            res.put("code", 500);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * POST /timeline/copy-template
     * Copy timeline stages and milestones from an existing timeline template or another KdID.
     */
    @PostMapping(value = "/copy-template", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> copyTimelineTemplate(@RequestBody String requestBody) {
        JSONObject res = new JSONObject();
        try {
            JdbcTemplate jdbc = getJdbc();
            if (jdbc == null) {
                res.put("code", 500);
                res.put("status", "ERROR");
                res.put("message", "Database connection unavailable");
                return ResponseEntity.status(500).body(res.toString());
            }

            JSONObject body = new JSONObject(requestBody);
            int targetKdId = body.optInt("targetKdId", body.optInt("target_kd_id", body.optInt("KdID", 1)));
            int sourceKdId = body.optInt("sourceKdId", body.optInt("source_kd_id", 1));

            // 1. Ensure target timeline record exists in dbo.qa_ex_timelines
            String checkTargetSql = "SELECT TimelineID FROM dbo.qa_ex_timelines WHERE KdID = ?";
            List<Map<String, Object>> targetTimelines = jdbc.queryForList(checkTargetSql, targetKdId);
            int targetTimelineId;
            if (targetTimelines.isEmpty()) {
                String insertTimelineSql = "INSERT INTO dbo.qa_ex_timelines (KdID, Title, Description) VALUES (?, ?, ?)";
                jdbc.update(insertTimelineSql, targetKdId, "Tiến trình Kiểm định (KdID " + targetKdId + ")", "Sao chép từ tiến trình mẫu KdID " + sourceKdId);
                targetTimelineId = jdbc.queryForObject("SELECT TimelineID FROM dbo.qa_ex_timelines WHERE KdID = ?", Integer.class, targetKdId);
            } else {
                targetTimelineId = (Integer) targetTimelines.get(0).get("TimelineID");
            }

            // 2. Fetch source stages & milestones
            List<Map<String, Object>> sourceStages = jdbc.queryForList(
                "SELECT StageID, Code, StageName, StartDate, EndDate, Color, OrderIndex, Description FROM dbo.qa_ex_timeline_stages WHERE KdID = ? ORDER BY OrderIndex ASC",
                sourceKdId
            );

            List<Map<String, Object>> sourceMilestones = jdbc.queryForList(
                "SELECT MilestoneID, StageID, Title, Description, MilestoneDate, Time, Status, Scope, UserID, IsCritical, OrderIndex, OwnerName FROM dbo.qa_ex_timeline_milestones WHERE KdID = ? ORDER BY OrderIndex ASC",
                sourceKdId
            );

            if (sourceKdId == 3) {
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", targetKdId);
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_milestones WHERE KdID = ?", targetKdId);
                seedAsiinStagesAndMilestones(jdbc, targetTimelineId, targetKdId);
            } else if (sourceStages.isEmpty() || sourceMilestones.isEmpty()) {
                // If source has no stages or milestones in DB, seed default template directly
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", targetKdId);
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_milestones WHERE KdID = ?", targetKdId);
                seedDefaultStagesAndMilestones(jdbc, targetTimelineId, targetKdId);
            } else {
                // Clear existing target stages & milestones
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", targetKdId);
                jdbc.update("DELETE FROM dbo.qa_ex_timeline_milestones WHERE KdID = ?", targetKdId);

                Map<Integer, Integer> stageIdMapping = new HashMap<>();

                // Copy Stages
                for (Map<String, Object> s : sourceStages) {
                    int oldStageId = (Integer) s.get("StageID");
                    String code = (String) s.get("Code");
                    String stageName = (String) s.get("StageName");
                    String startDate = (String) s.get("StartDate");
                    String endDate = (String) s.get("EndDate");
                    String color = (String) s.get("Color");
                    int orderIndex = (Integer) s.get("OrderIndex");
                    String description = (String) s.get("Description");

                    String insertStageSql = "INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbc.update(insertStageSql, targetTimelineId, targetKdId, stageName, code, startDate, endDate, color, orderIndex, description);

                    Integer newStageId = jdbc.queryForObject("SELECT MAX(StageID) FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", Integer.class, targetKdId);
                    if (newStageId != null) {
                        stageIdMapping.put(oldStageId, newStageId);
                    }
                }

                for (Map<String, Object> m : sourceMilestones) {
                    Integer oldStageId = (Integer) m.get("StageID");
                    Integer newStageId = oldStageId != null ? stageIdMapping.get(oldStageId) : null;
                    String title = (String) m.get("Title");
                    String description = (String) m.get("Description");
                    String milestoneDate = (String) m.get("MilestoneDate");
                    String time = (String) m.get("Time");
                    String status = "Pending"; // Reset status for copied template
                    String scope = (String) m.get("Scope");
                    Integer userId = (Integer) m.get("UserID");
                    int isCritical = m.get("IsCritical") instanceof Boolean ? ((Boolean) m.get("IsCritical") ? 1 : 0) : ((Number) m.get("IsCritical")).intValue();
                    int orderIndex = (Integer) m.get("OrderIndex");
                    String ownerName = (String) m.get("OwnerName");

                    String insertMsSql = "INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, UserID, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    jdbc.update(insertMsSql, targetTimelineId, newStageId, targetKdId, title, description, milestoneDate, time, status, scope, userId, isCritical, orderIndex, ownerName);
                }
            }

            res.put("code", 200);
            res.put("status", "SUCCESS");
            res.put("message", "Timeline template copied and applied successfully.");
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error copying timeline template: ", e);
            res.put("code", 500);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * Seed default ASIIN International Accreditation template stages and milestones for a target KdID.
     */
    private void seedAsiinStagesAndMilestones(JdbcTemplate jdbc, int timelineId, int kdId) {
        try {
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 1: Lập Báo cáo Tự đánh giá (SER - ASIIN)', 'ASIIN_SER', '2026-08-01', '2026-08-20', '#2563EB', 1, N'Hoàn thiện Self-Evaluation Report & Ma trận ILOs')", timelineId, kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 2: Đánh giá & Khảo sát Thực địa Quốc tế ASIIN', 'ASIIN_ONSITE', '2026-08-21', '2026-08-31', '#D97706', 2, N'Đoàn Chuyên gia ASIIN (Đức/Châu Âu) thẩm định')", timelineId, kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 3: Giải trình & Bổ sung theo Báo cáo Sơ bộ ASIIN', 'ASIIN_FEEDBACK', '2026-09-01', '2026-09-15', '#059669', 3, N'Tiếp nhận draft report & làm rõ khuyến nghị')", timelineId, kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 4: Thẩm định Hội đồng ASIIN & Cấp Quality Seal', 'ASIIN_SEAL', '2026-09-16', '2026-10-15', '#7C3AED', 4, N'Hội đồng Accreditation Commission cấp Giấy chứng nhận 5 năm')", timelineId, kdId);

            List<Map<String, Object>> stages = jdbc.queryForList("SELECT StageID, Code FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", kdId);
            Map<String, Integer> stageMap = new HashMap<>();
            for (Map<String, Object> s : stages) {
                stageMap.put(s.get("Code").toString(), (Integer) s.get("StageID"));
            }

            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Nộp Báo cáo Tự đánh giá (SER) tiếng Anh cho Tổ chức ASIIN', N'Chốt tài liệu ma trận chuẩn đầu ra ILOs và Hồ sơ Giảng viên', '2026-08-10', '09:00', 'Pending', 'Shared', 1, 1, N'Ban Thư ký ASIIN')", timelineId, stageMap.get("ASIIN_SER"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Phiên Khai mạc Khảo sát chính thức với Đoàn Chuyên gia Quốc tế ASIIN', N'Tập trung tại Phòng Hội thảo Quốc tế với chuyên gia Đức', '2026-08-22', '08:30', 'Pending', 'Shared', 1, 2, N'BGH & Trưởng Đoàn ASIIN')", timelineId, stageMap.get("ASIIN_ONSITE"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Phỏng vấn Sinh viên, Cựu sinh viên & Doanh nghiệp tuyển dụng', N'Đánh giá thực tế khả năng đáp ứng thị trường lao động Châu Âu', '2026-08-25', '14:00', 'Pending', 'Shared', 1, 3, N'Đoàn Kiểm định ASIIN')", timelineId, stageMap.get("ASIIN_ONSITE"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Gửi Báo cáo Giải trình phản hồi Dự thảo Báo cáo Kiểm định ASIIN', N'Bổ sung hồ sơ minh chứng bổ sung theo yêu cầu của chuyên gia', '2026-09-08', '11:00', 'Pending', 'Shared', 1, 4, N'Trung tâm ĐBCL')", timelineId, stageMap.get("ASIIN_FEEDBACK"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Công bố Quyết định & Nhận Giấy chứng nhận Đạt chuẩn ASIIN (Quality Seal)', N'Hội đồng ASIIN Accreditation Commission chính thức công nhận 5 năm', '2026-10-01', '10:00', 'Pending', 'Shared', 1, 5, N'Hội đồng ASIIN')", timelineId, stageMap.get("ASIIN_SEAL"), kdId);
        } catch (Exception e) {
            log.error("Error seeding ASIIN timeline template: ", e);
        }
    }

    /**
     * Seed default stages and milestones for a new KdID.
     */
    private void seedDefaultStagesAndMilestones(JdbcTemplate jdbc, int timelineId, int kdId) {
        try {
            // Seed 4 Default Stages
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 1: Chuẩn bị & Rà soát Tự đánh giá', 'PRE_ONSITE', '2026-08-01', '2026-08-15', '#3B82F6', 1, N'Chuẩn bị hồ sơ minh chứng')", timelineId, kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 2: Khai mạc & Đánh giá tại chỗ (Onsite)', 'ONSITE', '2026-08-16', '2026-08-25', '#F59E0B', 2, N'Đoàn ĐGN thẩm định thực địa')", timelineId, kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 3: Báo cáo sơ bộ & Phản hồi', 'PRELIMINARY_REPORT', '2026-08-26', '2026-09-05', '#10B981', 3, N'Tiếp nhận dự thảo báo cáo ĐGN')", timelineId, kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_stages (TimelineID, KdID, StageName, Code, StartDate, EndDate, Color, OrderIndex, Description) VALUES (?, ?, N'Giai đoạn 4: Hoàn thiện & Công nhận Chất lượng', 'POST_ONSITE', '2026-09-06', '2026-09-30', '#8B5CF6', 4, N'Hội đồng cấp Giấy chứng nhận')", timelineId, kdId);

            List<Map<String, Object>> stages = jdbc.queryForList("SELECT StageID, Code FROM dbo.qa_ex_timeline_stages WHERE KdID = ?", kdId);
            Map<String, Integer> stageMap = new HashMap<>();
            for (Map<String, Object> s : stages) {
                stageMap.put(s.get("Code").toString(), (Integer) s.get("StageID"));
            }

            // Seed Default Milestones
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Chốt danh mục 100% tài liệu minh chứng Tiêu chuẩn 1-11', N'Ban Thư ký hoàn thành rà soát mã hóa', '2026-08-05', '09:00', 'Completed', 'Shared', 1, 1, N'Ban Thư ký TĐG')", timelineId, stageMap.get("PRE_ONSITE"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Gửi Báo cáo TĐG chính thức cho Đoàn Đánh giá ngoài', N'Nộp bản in và truy cập kho minh chứng số hóa', '2026-08-12', '14:00', 'Completed', 'Shared', 1, 2, N'Trung tâm ĐBCL')", timelineId, stageMap.get("PRE_ONSITE"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Phiên Khai mạc Khảo sát chính thức Đoàn ĐGN', N'Tập trung tại Hội trường A1', '2026-08-18', '08:30', 'Completed', 'Shared', 1, 3, N'Ban Giám hiệu')", timelineId, stageMap.get("ONSITE"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Phỏng vấn Trưởng bộ môn & Giảng viên cơ hữu (15 người)', N'Phỏng vấn sâu về phát triển chương trình đào tạo', '2026-08-24', '09:00', 'InProgress', 'Shared', 1, 4, N'Trưởng đoàn ĐGN')", timelineId, stageMap.get("ONSITE"), kdId);
            jdbc.update("INSERT INTO dbo.qa_ex_timeline_milestones (TimelineID, StageID, KdID, Title, Description, MilestoneDate, Time, Status, Scope, IsCritical, OrderIndex, OwnerName) VALUES (?, ?, ?, N'Phiên Bế mạc & Ký Biên bản Khảo sát chính thức', N'Thông qua báo cáo sơ bộ của Đoàn ĐGN', '2026-08-25', '15:00', 'Pending', 'Shared', 1, 5, N'BGH & Đoàn ĐGN')", timelineId, stageMap.get("ONSITE"), kdId);
        } catch (Exception e) {
            log.error("Error seeding default timeline data: ", e);
        }
    }
}
