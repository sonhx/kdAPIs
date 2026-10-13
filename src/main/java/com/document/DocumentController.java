package com.document;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/document")
public class DocumentController {

    @Autowired
    private DocumentExtend documentExtend;

    @Autowired
    private SessionService sessionService;

    /**
     * GET /document/categories
     */
    @GetMapping(value = "/categories", produces = "application/json;charset=UTF-8")
    public String getCategories() {
        JSONObject jout = new JSONObject();
        try {
            JSONArray arr = documentExtend.listCategories();
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("categories", arr);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi máy chủ: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /document/tags
     */
    @GetMapping(value = "/tags", produces = "application/json;charset=UTF-8")
    public String getTags() {
        JSONObject jout = new JSONObject();
        try {
            JSONArray arr = documentExtend.listTags();
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("tags", arr);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi máy chủ: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/tag/create
     */
    @PostMapping(value = "/tag/create", produces = "application/json;charset=UTF-8")
    public String createTag(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String tagName = jin.optString("tag_name", "").trim();
            String tagCode = jin.optString("tag_code", "").trim();
            String tagGroup = jin.optString("tag_group", "GENERAL").trim();
            String color = jin.optString("color", "#0d9488").trim();

            if (tagName.isEmpty()) {
                jout.put("code", 400);
                jout.put("description", "Tên thẻ nhãn không được để trống!");
                return jout.toString();
            }

            JSONObject newTag = documentExtend.createTag(tagCode, tagName, tagGroup, color);
            if (newTag != null) {
                jout.put("code", 200);
                jout.put("description", "Tạo thẻ nhãn thành công!");
                jout.put("tag", newTag);
            } else {
                jout.put("code", 500);
                jout.put("description", "Không thể tạo thẻ nhãn");
            }
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi tạo thẻ: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/tag/delete
     */
    @PostMapping(value = "/tag/delete", produces = "application/json;charset=UTF-8")
    public String deleteTag(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int tagId = jin.optInt("tag_id", 0);
            if (tagId <= 0) {
                jout.put("code", 400);
                jout.put("description", "ID thẻ nhãn không hợp lệ!");
                return jout.toString();
            }

            boolean ok = documentExtend.deleteTag(tagId);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Đã xóa thẻ nhãn thành công!" : "Không tìm thấy thẻ nhãn");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi xóa thẻ: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /document/list
     */
    @GetMapping(value = "/list", produces = "application/json;charset=UTF-8")
    public String listDocuments(
            @RequestParam(name = "categoryId", required = false) Integer categoryId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "docType", required = false) String docType,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "tagMode", required = false, defaultValue = "AND") String tagMode,
            @RequestParam(name = "scope", required = false) String scope) {
        JSONObject jout = new JSONObject();
        try {
            JSONArray list = documentExtend.listDocuments(categoryId, status, docType, keyword, tag, scope, tags, tagMode);
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", list);
            jout.put("total", list.length());
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi máy chủ: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * GET /document/{id}
     */
    @GetMapping(value = "/{id}", produces = "application/json;charset=UTF-8")
    public String getDocumentDetail(@PathVariable("id") Integer id) {
        JSONObject jout = new JSONObject();
        try {
            if (id == null || id <= 0) {
                jout.put("code", 400);
                jout.put("description", "ID văn bản không hợp lệ");
                return jout.toString();
            }
            JSONObject doc = documentExtend.getDocumentById(id);
            if (doc == null) {
                jout.put("code", 404);
                jout.put("description", "Không tìm thấy hồ sơ văn bản");
                return jout.toString();
            }
            jout.put("code", 200);
            jout.put("description", "Thành công");
            jout.put("data", doc);
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi máy chủ: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/create
     */
    @PostMapping(value = "/create", produces = "application/json;charset=UTF-8")
    public String createDocument(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String docCode = jin.optString("doc_code", "").trim();
            String title = jin.optString("title", "").trim();
            if (docCode.isEmpty() || title.isEmpty()) {
                jout.put("code", 400);
                jout.put("description", "Mã hiệu và Tiêu đề văn bản không được để trống!");
                return jout.toString();
            }

            int categoryId = jin.optInt("category_id", 1);
            String summary = jin.optString("summary", "");
            String docType = jin.optString("doc_type", "Quy định");
            String issuingBody = jin.optString("issuing_body", "TTKT");
            String signer = jin.optString("signer", "");
            String issueDate = jin.optString("issue_date", null);
            String effectiveDate = jin.optString("effective_date", null);
            String expireDate = jin.optString("expire_date", null);
            String status = jin.optString("status", "EFFECTIVE");
            String visibilityScope = jin.optString("visibility_scope", "INSTITUTE_PUBLIC");

            String sessionId = jin.optString("session_id", "");
            String user = resolveUser(sessionId);

            String fileName = jin.optString("file_name", "van_ban.pdf");
            String filePath = jin.optString("file_path", "/documents/files/" + fileName);
            Long fileSize = jin.optLong("file_size", 1024000L);
            String fileExt = jin.optString("file_ext", "pdf");

            List<Integer> tagIds = new ArrayList<>();
            if (jin.has("tag_ids")) {
                JSONArray tArr = jin.getJSONArray("tag_ids");
                for (int i = 0; i < tArr.length(); i++) {
                    tagIds.add(tArr.getInt(i));
                }
            }

            JSONArray attachments = jin.optJSONArray("attachments");

            int newId = documentExtend.createDocument(
                docCode, title, summary, categoryId, docType, issuingBody, signer,
                issueDate, effectiveDate, expireDate, status, visibilityScope, user,
                fileName, filePath, fileSize, fileExt, tagIds, attachments
            );

            if (newId > 0) {
                jout.put("code", 200);
                jout.put("description", "Tạo mới hồ sơ văn bản thành công!");
                jout.put("document_id", newId);
            } else {
                jout.put("code", 500);
                jout.put("description", "Không thể tạo hồ sơ văn bản trên CSDL");
            }

        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi tạo văn bản: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/approve
     */
    @PostMapping(value = "/approve", produces = "application/json;charset=UTF-8")
    public String approveDocument(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int documentId = jin.getInt("document_id");
            String comments = jin.optString("comments", "Phê duyệt ban hành chính thức");
            String sessionId = jin.optString("session_id", "");
            String user = resolveUser(sessionId);

            boolean ok = documentExtend.approveDocument(documentId, user, user, comments);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Đã phê duyệt và ban hành văn bản thành công!" : "Không tìm thấy văn bản");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi phê duyệt: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/revert
     */
    @PostMapping(value = "/revert", produces = "application/json;charset=UTF-8")
    public String revertDocument(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int documentId = jin.getInt("document_id");
            String comments = jin.optString("comments", "Yêu cầu chỉnh sửa lại");
            String sessionId = jin.optString("session_id", "");
            String user = resolveUser(sessionId);

            boolean ok = documentExtend.revertDocument(documentId, user, user, comments);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Đã trả lại dự thảo yêu cầu chỉnh sửa!" : "Không tìm thấy văn bản");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi trả lại dự thảo: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/delete
     */
    @PostMapping(value = "/delete", produces = "application/json;charset=UTF-8")
    public String deleteDocument(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int documentId = jin.getInt("document_id");
            String sessionId = jin.optString("session_id", "");
            String user = resolveUser(sessionId);

            boolean ok = documentExtend.deleteDocument(documentId, user);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Xóa văn bản thành công!" : "Không tìm thấy văn bản");
        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("description", "Lỗi xóa văn bản: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /document/download
     */
    @PostMapping(value = "/download", produces = "application/json;charset=UTF-8")
    public String trackDownload(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int documentId = jin.getInt("document_id");
            boolean ok = documentExtend.incrementDownload(documentId);
            jout.put("code", ok ? 200 : 404);
            jout.put("description", ok ? "Cập nhật lượt tải thành công!" : "Không tìm thấy văn bản");
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi: " + e.getMessage());
        }
        return jout.toString();
    }

    private String resolveUser(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "admin@ptit.edu.vn";
        try {
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst != null) {
                return (sst.sUserId != null && !sst.sUserId.isBlank()) ? sst.sUserId : String.valueOf(sst.UserID);
            }
        } catch (Exception ignored) {}
        return "admin@ptit.edu.vn";
    }
}
