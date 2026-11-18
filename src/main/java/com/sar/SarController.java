package com.sar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.config.Config;
import com.file.UploadBase64;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/sar")
public class SarController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SarExtend sarExtend;

    /**
     * 1. ONLYOFFICE calls this endpoint to download the .docx file
     */
    @GetMapping("/download/{docId}")
    public ResponseEntity<Resource> downloadSarDoc(@PathVariable int docId) {
        try {
            Map<String, Object> sar = sarExtend.getSarById(docId);
            if (sar == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            String relPath = sar.get("path") == null ? "" : sar.get("path").toString();
            String filePath = relPath.startsWith(Config.homePath) 
                    ? relPath.replaceFirst(Config.homePath, Config.homeDir) 
                    : relPath;

            File file = new File(filePath);
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            org.springframework.http.ContentDisposition contentDisposition = org.springframework.http.ContentDisposition.attachment()
                    .filename(file.getName(), java.nio.charset.StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .headers(headers -> headers.setContentDisposition(contentDisposition))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 2. ONLYOFFICE webhook callback when users edit / save the document
     */
    @PostMapping(value = "/callback/{docId}", produces = "application/json")
    public String sarDocumentCallback(@PathVariable int docId, @RequestBody String sReq) {
        try {
            JSONObject body = new JSONObject(sReq);
            int status = body.getInt("status");

            // Status 2 = document ready for saving (all users closed the document)
            // Status 6 = force save triggered
            if (status == 2 || status == 6) {
                if (body.has("url")) {
                    String downloadUrl = body.getString("url");
                    Map<String, Object> sar = sarExtend.getSarById(docId);
                    if (sar != null) {
                        String relPath = sar.get("path") == null ? "" : sar.get("path").toString();
                        String filePath = relPath.startsWith(Config.homePath) 
                                ? relPath.replaceFirst(Config.homePath, Config.homeDir) 
                                : relPath;

                        File file = new File(filePath);
                        if (!file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                        }

                        // Download new content from ONLYOFFICE and overwrite local file
                        try (InputStream in = new URL(downloadUrl).openStream()) {
                            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("ONLYOFFICE: Document ID " + docId + " saved successfully from " + downloadUrl);
                        }

                        // ONLY rotate document_key when all users closed the document (status 2).
                        // In status 6 (force save during active editing), keep the same document_key
                        // so active co-editing session remains valid and does not freeze!
                        if (status == 2) {
                            String newKey = "sar_" + docId + "_" + System.currentTimeMillis();
                            sarExtend.updateDocumentKey(docId, newKey);
                        }
                    }
                }
            }
            return "{\"error\": 0}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": 0}"; // ONLYOFFICE expects error 0 to acknowledge receipt
        }
    }

    /**
     * 3. List SAR reports for a specific accreditation cycle
     */
    @PostMapping("/list")
    public String listSar(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.optString("session_id", "");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Unauthorized\"}";
            }

            int kdId = jin.getInt("kd_id");
            String doituongKd = jin.getString("doituong_kd");

            JSONArray list = sarExtend.listSar(kdId, doituongKd);
            jout.put("code", 200);
            jout.put("list_sar", list);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    /**
     * 4. Create a new SAR report with initial .docx file
     */
    @PostMapping("/create")
    public String createSar(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.optString("session_id", "");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Unauthorized\"}";
            }

            int kdId = jin.getInt("kd_id");
            String doituongKd = jin.getString("doituong_kd");
            String ten = jin.getString("ten");
            String ghiChu = jin.optString("ghi_chu", "");
            String filename = jin.getString("filename");
            String base64Data = jin.getString("base64");

            // Ensure filename ends with .docx
            if (!filename.toLowerCase().endsWith(".docx")) {
                filename += ".docx";
            }

            // Target storage directory
            String relDir = UploadBase64.fPath(kdId, doituongKd, "tdg", null);
            String absDir = relDir.replaceFirst(Config.homePath, Config.homeDir);
            File dir = new File(absDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Unique filename with timestamp to avoid collision
            String safeFilename = System.currentTimeMillis() + "_" + filename;
            String absFile = absDir + File.separator + safeFilename;
            String relFile = relDir + "/" + safeFilename;

            // Clean Base64 string if data URL prefix exists
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            try (OutputStream os = new FileOutputStream(absFile)) {
                os.write(decodedBytes);
            }

            String initialDocKey = "sar_init_" + System.currentTimeMillis();
            int newId = sarExtend.createSar(ten, ghiChu, relFile, sst.UserID, kdId, doituongKd, initialDocKey);

            jout.put("code", 200);
            jout.put("id", newId);
            jout.put("description", "Tạo báo cáo TĐG thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    /**
     * 5. Finalize SAR report (sets status = 1, locked for editing)
     */
    @PostMapping("/finalize")
    public String finalizeSar(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.optString("session_id", "");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Unauthorized\"}";
            }

            int id = jin.getInt("id");
            sarExtend.updateStatus(id, 1);

            jout.put("code", 200);
            jout.put("description", "Báo cáo TĐG đã hoàn thành");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    /**
     * 6. Reopen SAR report for editing (sets status = 0)
     */
    @PostMapping("/reopen")
    public String reopenSar(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.optString("session_id", "");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Unauthorized\"}";
            }

            int id = jin.getInt("id");
            sarExtend.updateStatus(id, 0);

            jout.put("code", 200);
            jout.put("description", "Đã mở lại báo cáo để soạn thảo");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    /**
     * 7. Delete SAR report (soft delete)
     */
    @PostMapping("/delete")
    public String deleteSar(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.optString("session_id", "");
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Unauthorized\"}";
            }

            int id = jin.getInt("id");
            sarExtend.deleteSar(id);

            jout.put("code", 200);
            jout.put("description", "Xóa báo cáo thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }
}

