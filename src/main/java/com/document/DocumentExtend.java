package com.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // 1. CATEGORIES (Danh mục phân cấp)
    // -------------------------------------------------------------------------
    public JSONArray listCategories() {
        JSONArray arr = new JSONArray();
        String sql = 
            "SELECT category_id, category_code, parent_id, category_name, description, icon, sort_order, is_active " +
            "FROM dbo.doc_categories " +
            "WHERE is_active = 1 " +
            "ORDER BY sort_order ASC, category_name ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> r : rows) {
            JSONObject obj = new JSONObject();
            obj.put("category_id", r.get("category_id"));
            obj.put("category_code", r.get("category_code"));
            obj.put("parent_id", r.get("parent_id") != null ? r.get("parent_id") : JSONObject.NULL);
            obj.put("category_name", r.get("category_name"));
            obj.put("description", r.get("description") != null ? r.get("description") : "");
            obj.put("icon", r.get("icon") != null ? r.get("icon") : "folder");
            obj.put("sort_order", r.get("sort_order"));
            arr.put(obj);
        }
        return arr;
    }

    // -------------------------------------------------------------------------
    // 2. TAGS (Thẻ nhãn đa chiều)
    // -------------------------------------------------------------------------
    public JSONArray listTags() {
        JSONArray arr = new JSONArray();
        String sql = "SELECT tag_id, tag_code, tag_name, tag_group, color FROM dbo.document_tags ORDER BY tag_name ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> r : rows) {
            JSONObject obj = new JSONObject();
            obj.put("tag_id", r.get("tag_id"));
            obj.put("tag_code", r.get("tag_code"));
            obj.put("tag_name", r.get("tag_name"));
            obj.put("tag_group", r.get("tag_group") != null ? r.get("tag_group") : "GENERAL");
            obj.put("color", r.get("color") != null ? r.get("color") : "#3b82f6");
            arr.put(obj);
        }
        return arr;
    }

    public JSONObject createTag(String tagCode, String tagName, String tagGroup, String color) {
        if (tagName == null || tagName.isBlank()) return null;
        tagName = tagName.trim();
        if (!tagName.startsWith("#")) tagName = "#" + tagName;

        if (tagCode == null || tagCode.isBlank()) {
            tagCode = tagName.replace("#", "")
                             .replaceAll("[^a-zA-Z0-9_]", "_")
                             .toUpperCase();
        } else {
            tagCode = tagCode.trim().toUpperCase();
        }

        if (tagGroup == null || tagGroup.isBlank()) tagGroup = "GENERAL";
        if (color == null || color.isBlank()) color = "#0d9488";

        // Check if code or name already exists
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.document_tags WHERE tag_code = ? OR tag_name = ?",
            Integer.class, tagCode, tagName
        );
        if (count != null && count > 0) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT tag_id, tag_code, tag_name, tag_group, color FROM dbo.document_tags WHERE tag_code = ? OR tag_name = ?",
                tagCode, tagName
            );
            if (!existing.isEmpty()) {
                Map<String, Object> r = existing.get(0);
                JSONObject out = new JSONObject();
                out.put("tag_id", r.get("tag_id"));
                out.put("tag_code", r.get("tag_code"));
                out.put("tag_name", r.get("tag_name"));
                out.put("tag_group", r.get("tag_group"));
                out.put("color", r.get("color"));
                return out;
            }
        }

        String sql = "INSERT INTO dbo.document_tags (tag_code, tag_name, tag_group, color) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, tagCode, tagName, tagGroup, color);

        Integer newId = jdbcTemplate.queryForObject("SELECT @@IDENTITY", Integer.class);
        JSONObject obj = new JSONObject();
        obj.put("tag_id", newId != null ? newId : 0);
        obj.put("tag_code", tagCode);
        obj.put("tag_name", tagName);
        obj.put("tag_group", tagGroup);
        obj.put("color", color);
        return obj;
    }

    public boolean deleteTag(int tagId) {
        jdbcTemplate.update("DELETE FROM dbo.document_tag_map WHERE tag_id = ?", tagId);
        int rows = jdbcTemplate.update("DELETE FROM dbo.document_tags WHERE tag_id = ?", tagId);
        return rows > 0;
    }

    // -------------------------------------------------------------------------
    // 3. LIST DOCUMENTS
    // -------------------------------------------------------------------------
    public JSONArray listDocuments(Integer categoryId, String status, String docType, String keyword, String tagCode, String scope) {
        return listDocuments(categoryId, status, docType, keyword, tagCode, scope, null, "AND");
    }

    public JSONArray listDocuments(Integer categoryId, String status, String docType, String keyword, String tagCode, String scope, String tagsParam, String tagMode) {
        StringBuilder sql = new StringBuilder(
            "SELECT d.document_id, d.doc_code, d.title, d.summary, d.category_id, c.category_name, c.category_code, " +
            "       d.doc_type, d.issuing_body, d.signer, " +
            "       CONVERT(VARCHAR(10), d.issue_date, 120) AS issue_date, " +
            "       CONVERT(VARCHAR(10), d.effective_date, 120) AS effective_date, " +
            "       CONVERT(VARCHAR(10), d.expire_date, 120) AS expire_date, " +
            "       d.status, d.current_version, d.visibility_scope, d.superseded_by_id, " +
            "       d.created_by, d.approved_by, " +
            "       CONVERT(VARCHAR(19), d.approved_date, 120) AS approved_date, " +
            "       d.view_count, d.download_count, " +
            "       CONVERT(VARCHAR(19), d.created_at, 120) AS created_at, " +
            "       v.file_name AS primary_file_name, v.file_path AS primary_file_path, v.file_size AS primary_file_size, v.file_ext AS primary_file_ext " +
            "FROM dbo.documents d " +
            "INNER JOIN dbo.doc_categories c ON d.category_id = c.category_id " +
            "LEFT JOIN dbo.document_versions v ON d.document_id = v.document_id AND v.is_current = 1 " +
            "WHERE d.is_deleted = 0 "
        );

        List<Object> params = new ArrayList<>();

        if (categoryId != null && categoryId > 0) {
            sql.append("AND (d.category_id = ? OR c.parent_id = ?) ");
            params.add(categoryId);
            params.add(categoryId);
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND d.status = ? ");
            params.add(status.trim());
        }

        if (docType != null && !docType.isBlank() && !"ALL".equalsIgnoreCase(docType)) {
            sql.append("AND d.doc_type = ? ");
            params.add(docType.trim());
        }

        if (scope != null && !scope.isBlank() && !"ALL".equalsIgnoreCase(scope)) {
            sql.append("AND d.visibility_scope = ? ");
            params.add(scope.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (d.doc_code LIKE ? OR d.title LIKE ? OR d.summary LIKE ? OR d.signer LIKE ? OR d.issuing_body LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw); params.add(kw); params.add(kw);
        }

        // Parse multi-tags from tagsParam or tagCode (supports comma-separated)
        List<String> tagList = new ArrayList<>();
        if (tagsParam != null && !tagsParam.isBlank()) {
            for (String t : tagsParam.split(",")) {
                String cleanT = t.trim();
                if (!cleanT.isEmpty() && !tagList.contains(cleanT)) tagList.add(cleanT);
            }
        }
        if (tagCode != null && !tagCode.isBlank()) {
            for (String t : tagCode.split(",")) {
                String cleanT = t.trim();
                if (!cleanT.isEmpty() && !tagList.contains(cleanT)) tagList.add(cleanT);
            }
        }

        if (!tagList.isEmpty()) {
            if (tagList.size() == 1) {
                sql.append("AND d.document_id IN (SELECT tm.document_id FROM dbo.document_tag_map tm JOIN dbo.document_tags t ON tm.tag_id = t.tag_id WHERE t.tag_code = ?) ");
                params.add(tagList.get(0));
            } else {
                String placeholders = String.join(",", Collections.nCopies(tagList.size(), "?"));
                if ("OR".equalsIgnoreCase(tagMode)) {
                    // Match ANY of the tags
                    sql.append("AND d.document_id IN (SELECT tm.document_id FROM dbo.document_tag_map tm JOIN dbo.document_tags t ON tm.tag_id = t.tag_id WHERE t.tag_code IN (")
                       .append(placeholders)
                       .append(")) ");
                    params.addAll(tagList);
                } else {
                    // Default AND: Match ALL of the tags
                    sql.append("AND d.document_id IN (SELECT tm.document_id FROM dbo.document_tag_map tm JOIN dbo.document_tags t ON tm.tag_id = t.tag_id WHERE t.tag_code IN (")
                       .append(placeholders)
                       .append(") GROUP BY tm.document_id HAVING COUNT(DISTINCT t.tag_code) = ?) ");
                    params.addAll(tagList);
                    params.add(tagList.size());
                }
            }
        }

        sql.append("ORDER BY d.created_at DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        JSONArray arr = new JSONArray();

        for (Map<String, Object> r : rows) {
            JSONObject obj = new JSONObject();
            int docId = (int) r.get("document_id");
            obj.put("document_id", docId);
            obj.put("doc_code", r.get("doc_code"));
            obj.put("title", r.get("title"));
            obj.put("summary", r.get("summary") != null ? r.get("summary") : "");
            obj.put("category_id", r.get("category_id"));
            obj.put("category_name", r.get("category_name"));
            obj.put("category_code", r.get("category_code"));
            obj.put("doc_type", r.get("doc_type"));
            obj.put("issuing_body", r.get("issuing_body") != null ? r.get("issuing_body") : "");
            obj.put("signer", r.get("signer") != null ? r.get("signer") : "");
            obj.put("issue_date", r.get("issue_date") != null ? r.get("issue_date") : JSONObject.NULL);
            obj.put("effective_date", r.get("effective_date") != null ? r.get("effective_date") : JSONObject.NULL);
            obj.put("expire_date", r.get("expire_date") != null ? r.get("expire_date") : JSONObject.NULL);
            obj.put("status", r.get("status"));
            obj.put("current_version", r.get("current_version"));
            obj.put("visibility_scope", r.get("visibility_scope"));
            obj.put("superseded_by_id", r.get("superseded_by_id") != null ? r.get("superseded_by_id") : JSONObject.NULL);
            obj.put("created_by", r.get("created_by") != null ? r.get("created_by") : "");
            obj.put("approved_by", r.get("approved_by") != null ? r.get("approved_by") : JSONObject.NULL);
            obj.put("approved_date", r.get("approved_date") != null ? r.get("approved_date") : JSONObject.NULL);
            obj.put("view_count", r.get("view_count") != null ? r.get("view_count") : 0);
            obj.put("download_count", r.get("download_count") != null ? r.get("download_count") : 0);
            obj.put("created_at", r.get("created_at"));

            // Primary file info
            obj.put("primary_file_name", r.get("primary_file_name") != null ? r.get("primary_file_name") : "");
            obj.put("primary_file_path", r.get("primary_file_path") != null ? r.get("primary_file_path") : "");
            obj.put("primary_file_size", r.get("primary_file_size") != null ? r.get("primary_file_size") : 0);
            obj.put("primary_file_ext", r.get("primary_file_ext") != null ? r.get("primary_file_ext") : "pdf");

            // Attachments count
            Integer attCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.document_attachments WHERE document_id = ? AND is_deleted = 0",
                Integer.class, docId
            );
            obj.put("attachments_count", attCount != null ? attCount : 0);

            // Tags array
            JSONArray tagsArr = getDocumentTags(docId);
            obj.put("tags", tagsArr);

            arr.put(obj);
        }

        return arr;
    }

    private JSONArray getDocumentTags(int documentId) {
        JSONArray tagsArr = new JSONArray();
        String sql = 
            "SELECT t.tag_id, t.tag_code, t.tag_name, t.color " +
            "FROM dbo.document_tag_map tm " +
            "JOIN dbo.document_tags t ON tm.tag_id = t.tag_id " +
            "WHERE tm.document_id = ?";
        List<Map<String, Object>> tagRows = jdbcTemplate.queryForList(sql, documentId);
        for (Map<String, Object> tr : tagRows) {
            JSONObject to = new JSONObject();
            to.put("tag_id", tr.get("tag_id"));
            to.put("tag_code", tr.get("tag_code"));
            to.put("tag_name", tr.get("tag_name"));
            to.put("color", tr.get("color") != null ? tr.get("color") : "#3b82f6");
            tagsArr.put(to);
        }
        return tagsArr;
    }

    // -------------------------------------------------------------------------
    // 4. GET DOCUMENT DETAIL
    // -------------------------------------------------------------------------
    public JSONObject getDocumentById(int documentId) {
        String sql = 
            "SELECT d.document_id, d.doc_code, d.title, d.summary, d.category_id, c.category_name, c.category_code, " +
            "       d.doc_type, d.issuing_body, d.signer, " +
            "       CONVERT(VARCHAR(10), d.issue_date, 120) AS issue_date, " +
            "       CONVERT(VARCHAR(10), d.effective_date, 120) AS effective_date, " +
            "       CONVERT(VARCHAR(10), d.expire_date, 120) AS expire_date, " +
            "       d.status, d.current_version, d.visibility_scope, d.superseded_by_id, " +
            "       d.created_by, d.approved_by, " +
            "       CONVERT(VARCHAR(19), d.approved_date, 120) AS approved_date, " +
            "       d.view_count, d.download_count, " +
            "       CONVERT(VARCHAR(19), d.created_at, 120) AS created_at " +
            "FROM dbo.documents d " +
            "INNER JOIN dbo.doc_categories c ON d.category_id = c.category_id " +
            "WHERE d.document_id = ? AND d.is_deleted = 0";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, documentId);
        if (rows.isEmpty()) return null;

        Map<String, Object> r = rows.get(0);
        JSONObject obj = new JSONObject();
        obj.put("document_id", documentId);
        obj.put("doc_code", r.get("doc_code"));
        obj.put("title", r.get("title"));
        obj.put("summary", r.get("summary") != null ? r.get("summary") : "");
        obj.put("category_id", r.get("category_id"));
        obj.put("category_name", r.get("category_name"));
        obj.put("category_code", r.get("category_code"));
        obj.put("doc_type", r.get("doc_type"));
        obj.put("issuing_body", r.get("issuing_body") != null ? r.get("issuing_body") : "");
        obj.put("signer", r.get("signer") != null ? r.get("signer") : "");
        obj.put("issue_date", r.get("issue_date") != null ? r.get("issue_date") : JSONObject.NULL);
        obj.put("effective_date", r.get("effective_date") != null ? r.get("effective_date") : JSONObject.NULL);
        obj.put("expire_date", r.get("expire_date") != null ? r.get("expire_date") : JSONObject.NULL);
        obj.put("status", r.get("status"));
        obj.put("current_version", r.get("current_version"));
        obj.put("visibility_scope", r.get("visibility_scope"));
        obj.put("created_by", r.get("created_by") != null ? r.get("created_by") : "");
        obj.put("approved_by", r.get("approved_by") != null ? r.get("approved_by") : JSONObject.NULL);
        obj.put("approved_date", r.get("approved_date") != null ? r.get("approved_date") : JSONObject.NULL);
        obj.put("view_count", r.get("view_count") != null ? r.get("view_count") : 0);
        obj.put("download_count", r.get("download_count") != null ? r.get("download_count") : 0);
        obj.put("created_at", r.get("created_at"));

        // If superseded, fetch replacement doc code & title
        Object supIdObj = r.get("superseded_by_id");
        if (supIdObj != null) {
            int supId = (int) supIdObj;
            obj.put("superseded_by_id", supId);
            try {
                Map<String, Object> supDoc = jdbcTemplate.queryForMap(
                    "SELECT doc_code, title FROM dbo.documents WHERE document_id = ?", supId
                );
                obj.put("superseded_by_code", supDoc.get("doc_code"));
                obj.put("superseded_by_title", supDoc.get("title"));
            } catch (Exception ignored) {}
        } else {
            obj.put("superseded_by_id", JSONObject.NULL);
        }

        // Versions
        JSONArray versArr = new JSONArray();
        String sqlVers = 
            "SELECT version_id, version_num, file_name, file_path, file_size, file_ext, change_summary, is_current, uploaded_by, " +
            "       CONVERT(VARCHAR(19), uploaded_at, 120) AS uploaded_at " +
            "FROM dbo.document_versions WHERE document_id = ? ORDER BY uploaded_at DESC";
        List<Map<String, Object>> vRows = jdbcTemplate.queryForList(sqlVers, documentId);
        for (Map<String, Object> vr : vRows) {
            JSONObject vo = new JSONObject();
            vo.put("version_id", vr.get("version_id"));
            vo.put("version_num", vr.get("version_num"));
            vo.put("file_name", vr.get("file_name"));
            vo.put("file_path", vr.get("file_path"));
            vo.put("file_size", vr.get("file_size") != null ? vr.get("file_size") : 0);
            vo.put("file_ext", vr.get("file_ext") != null ? vr.get("file_ext") : "pdf");
            vo.put("change_summary", vr.get("change_summary") != null ? vr.get("change_summary") : "");
            vo.put("is_current", (boolean) vr.get("is_current"));
            vo.put("uploaded_by", vr.get("uploaded_by") != null ? vr.get("uploaded_by") : "");
            vo.put("uploaded_at", vr.get("uploaded_at"));
            versArr.put(vo);
        }
        obj.put("versions", versArr);

        // Attachments
        JSONArray attArr = new JSONArray();
        String sqlAtt = 
            "SELECT attachment_id, version_id, file_name, file_path, file_size, file_ext, attachment_type, sort_order, uploaded_by, " +
            "       CONVERT(VARCHAR(19), uploaded_at, 120) AS uploaded_at " +
            "FROM dbo.document_attachments WHERE document_id = ? AND is_deleted = 0 ORDER BY sort_order ASC, uploaded_at ASC";
        List<Map<String, Object>> aRows = jdbcTemplate.queryForList(sqlAtt, documentId);
        for (Map<String, Object> ar : aRows) {
            JSONObject ao = new JSONObject();
            ao.put("attachment_id", ar.get("attachment_id"));
            ao.put("file_name", ar.get("file_name"));
            ao.put("file_path", ar.get("file_path"));
            ao.put("file_size", ar.get("file_size") != null ? ar.get("file_size") : 0);
            ao.put("file_ext", ar.get("file_ext") != null ? ar.get("file_ext") : "docx");
            ao.put("attachment_type", ar.get("attachment_type") != null ? ar.get("attachment_type") : "Phụ lục");
            ao.put("uploaded_by", ar.get("uploaded_by") != null ? ar.get("uploaded_by") : "");
            ao.put("uploaded_at", ar.get("uploaded_at"));
            attArr.put(ao);
        }
        obj.put("attachments", attArr);

        // Tags
        obj.put("tags", getDocumentTags(documentId));

        // Approvals history
        JSONArray appArr = new JSONArray();
        String sqlApp = 
            "SELECT approval_id, version_num, action, approver_id, approver_name, comments, " +
            "       CONVERT(VARCHAR(19), created_at, 120) AS created_at " +
            "FROM dbo.document_approvals WHERE document_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> apRows = jdbcTemplate.queryForList(sqlApp, documentId);
        for (Map<String, Object> apr : apRows) {
            JSONObject apo = new JSONObject();
            apo.put("approval_id", apr.get("approval_id"));
            apo.put("version_num", apr.get("version_num"));
            apo.put("action", apr.get("action"));
            apo.put("approver_name", apr.get("approver_name") != null ? apr.get("approver_name") : "");
            apo.put("comments", apr.get("comments") != null ? apr.get("comments") : "");
            apo.put("created_at", apr.get("created_at"));
            appArr.put(apo);
        }
        obj.put("approvals", appArr);

        // Increment view count asynchronously
        try {
            jdbcTemplate.update("UPDATE dbo.documents SET view_count = view_count + 1 WHERE document_id = ?", documentId);
        } catch (Exception ignored) {}

        return obj;
    }

    public boolean incrementDownload(int documentId) {
        try {
            int n = jdbcTemplate.update("UPDATE dbo.documents SET download_count = download_count + 1 WHERE document_id = ?", documentId);
            return n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // 5. CREATE DOCUMENT
    // -------------------------------------------------------------------------
    public int createDocument(String docCode, String title, String summary, int categoryId,
                              String docType, String issuingBody, String signer,
                              String issueDate, String effectiveDate, String expireDate,
                              String status, String visibilityScope, String createdBy,
                              String fileName, String filePath, Long fileSize, String fileExt,
                              List<Integer> tagIds, JSONArray attachments) {
        String st = (status != null && !status.isBlank()) ? status : "EFFECTIVE";
        String sc = (visibilityScope != null && !visibilityScope.isBlank()) ? visibilityScope : "INSTITUTE_PUBLIC";

        String sql = 
            "INSERT INTO dbo.documents " +
            "(doc_code, title, summary, category_id, doc_type, issuing_body, signer, " +
            " issue_date, effective_date, expire_date, status, current_version, visibility_scope, created_by, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, " +
            " TRY_CONVERT(DATE, ?, 120), TRY_CONVERT(DATE, ?, 120), TRY_CONVERT(DATE, ?, 120), " +
            " ?, 'v1.0', ?, ?, 0, GETDATE(), GETDATE()); " +
            "SELECT SCOPE_IDENTITY();";

        Integer docId = jdbcTemplate.queryForObject(sql, Integer.class,
            docCode, title, summary, categoryId, docType, issuingBody, signer,
            issueDate, effectiveDate, expireDate,
            st, sc, createdBy
        );

        if (docId != null && docId > 0) {
            // Version 1.0
            if (fileName != null && !fileName.isBlank()) {
                String sqlVer = 
                    "INSERT INTO dbo.document_versions " +
                    "(document_id, version_num, file_name, file_path, file_size, file_ext, change_summary, is_current, uploaded_by, uploaded_at) " +
                    "VALUES (?, 'v1.0', ?, ?, ?, ?, N'Ban hành ban đầu', 1, ?, GETDATE()); " +
                    "SELECT SCOPE_IDENTITY();";
                Integer verId = jdbcTemplate.queryForObject(sqlVer, Integer.class,
                    docId, fileName, (filePath != null ? filePath : "/documents/files/" + fileName),
                    (fileSize != null ? fileSize : 1024L), (fileExt != null ? fileExt : "pdf"), createdBy
                );

                // Insert Attachments if provided
                if (attachments != null && attachments.length() > 0) {
                    for (int i = 0; i < attachments.length(); i++) {
                        JSONObject att = attachments.getJSONObject(i);
                        String aName = att.optString("file_name", "Phu_luc.docx");
                        String aPath = att.optString("file_path", "/documents/attachments/" + aName);
                        long aSize = att.optLong("file_size", 512000L);
                        String aExt = att.optString("file_ext", "docx");
                        String aType = att.optString("attachment_type", "Phụ lục");

                        jdbcTemplate.update(
                            "INSERT INTO dbo.document_attachments (document_id, version_id, file_name, file_path, file_size, file_ext, attachment_type, sort_order, is_deleted, uploaded_by, uploaded_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, GETDATE())",
                            docId, verId, aName, aPath, aSize, aExt, aType, (i + 1), createdBy
                        );
                    }
                }
            }

            // Map Tags
            if (tagIds != null) {
                for (Integer tId : tagIds) {
                    try {
                        jdbcTemplate.update("INSERT INTO dbo.document_tag_map (document_id, tag_id, created_at) VALUES (?, ?, GETDATE())", docId, tId);
                    } catch (Exception ignored) {}
                }
            }

            // Audit log
            logAudit(docId, createdBy, "UPLOAD", "Khởi tạo hồ sơ văn bản: " + docCode + " - " + title);
            return docId;
        }

        return -1;
    }

    // -------------------------------------------------------------------------
    // 6. APPROVE & REVERT WORKFLOW
    // -------------------------------------------------------------------------
    public boolean approveDocument(int documentId, String approverId, String approverName, String comments) {
        String sql = 
            "UPDATE dbo.documents SET " +
            "  status = 'EFFECTIVE', " +
            "  approved_by = ?, " +
            "  approved_date = GETDATE(), " +
            "  effective_date = COALESCE(effective_date, CAST(GETDATE() AS DATE)), " +
            "  updated_at = GETDATE() " +
            "WHERE document_id = ? AND is_deleted = 0";

        int rows = jdbcTemplate.update(sql, approverName != null ? approverName : approverId, documentId);
        if (rows > 0) {
            jdbcTemplate.update(
                "INSERT INTO dbo.document_approvals (document_id, version_num, action, approver_id, approver_name, comments, created_at) " +
                "VALUES (?, 'v1.0', 'APPROVE', ?, ?, ?, GETDATE())",
                documentId, approverId, approverName, comments
            );
            logAudit(documentId, approverName, "APPROVE", "Phê duyệt & ban hành văn bản. Ý kiến: " + (comments != null ? comments : "Đồng ý"));
            return true;
        }
        return false;
    }

    public boolean revertDocument(int documentId, String approverId, String approverName, String comments) {
        String sql = 
            "UPDATE dbo.documents SET " +
            "  status = 'DRAFT', " +
            "  updated_at = GETDATE() " +
            "WHERE document_id = ? AND is_deleted = 0";

        int rows = jdbcTemplate.update(sql, documentId);
        if (rows > 0) {
            jdbcTemplate.update(
                "INSERT INTO dbo.document_approvals (document_id, version_num, action, approver_id, approver_name, comments, created_at) " +
                "VALUES (?, 'v1.0', 'REJECT', ?, ?, ?, GETDATE())",
                documentId, approverId, approverName, comments
            );
            logAudit(documentId, approverName, "REJECT", "Yêu cầu chỉnh sửa văn bản. Ý kiến phản hồi: " + comments);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // 7. DELETE
    // -------------------------------------------------------------------------
    public boolean deleteDocument(int documentId, String user) {
        int rows = jdbcTemplate.update("UPDATE dbo.documents SET is_deleted = 1, updated_at = GETDATE() WHERE document_id = ?", documentId);
        if (rows > 0) {
            logAudit(documentId, user, "DELETE", "Xóa văn bản (is_deleted = 1)");
            return true;
        }
        return false;
    }

    private void logAudit(int documentId, String user, String action, String details) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.document_audit_logs (document_id, user_id, user_name, action, details, ip_address, created_at) " +
                "VALUES (?, ?, ?, ?, ?, '127.0.0.1', GETDATE())",
                documentId, user, user, action, details
            );
        } catch (Exception ignored) {}
    }
}
