package com.minhchung;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/mc")
public class MCService {

    @Autowired
    private MCExtend mcExtend;

    @Autowired
    private MCUp mcUp;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/upload")
    public String uploadMinhchung(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            JSONArray jsFrame = jsonobjReq.getJSONArray("frame");
            mcExtend.UploadFrame(jsFrame);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/frame_tree")
    public String getFrameTreeApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            int status = jin.getInt("status");

            JSONArray orgArr = new JSONArray();
            mcExtend.fn_loop_org_all(0, orgArr, kd_id, doituong_kd, status);

            jout.put("org_tree", orgArr);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/frame_tree_w_assignment")
    public String getFrameTreeWithAssignmentApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            int tc_id = jin.getInt("tc_id");

            JSONArray orgArr = new JSONArray();
            mcExtend.fn_loop_org_all_with_assigment(0, orgArr, tc_id, kd_id, doituong_kd);

            jout.put("org_tree", orgArr);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/add2list")
    public String addProof2ListApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String ma_mc = jin.getString("ma_mc");
            String ten_mc = jin.getString("ten_mc");
            int org_id = jin.getInt("org_id");

            if (mcExtend.isProofExisted(ma_mc, ten_mc)) {
                return "{\"code\":805, \"description\":\"Minh chứng đã tồn tại trong hệ thống\"}";
            }

            mcExtend.addProof2List(org_id, ma_mc, ten_mc);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/mc_files")
    public String listFilesApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            String kd_type = jin.getString("kd_type").equals("cs") ? "csgd" : "ctdt/" + jin.getString("kd_type");
            String ma_mc = jin.getString("ma_mc");

            JSONArray jsFiles = mcExtend.listFilesbyMc(kd_type, kd_id, ma_mc);
            jout.put("files", jsFiles);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/list_frame_proof")
    public String listFrameProofsApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("id");
            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            int status = jin.getInt("status");

            JSONArray jsaProofs = mcExtend.frameProofs(id, kd_id, doituong_kd, status);
            jout.put("list_proof", jsaProofs);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/delete")
    public String deleteProofApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int proof_id = jin.getInt("id");
            mcExtend.deleteProof(proof_id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/edit")
    public String editProofApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String ten_mc = jin.getString("ten_mc");
            String ten_file = jin.has("ten_file") ? jin.getString("ten_file") : null;
            String path = jin.has("path") ? jin.getString("path") : null;
            int mc_id = jin.getInt("mc_id");
            int emp_id = jin.has("emp_id") ? jin.getInt("emp_id") : -1;

            JSONObject joFile = jin.has("file") ? jin.getJSONObject("file") : null;
            if (joFile != null) {
                mcExtend.updateProofwUpload(mc_id, joFile, emp_id, sst.UserID);
            } else {
                mcExtend.updateMCTable(mc_id, ten_mc, ten_file, path, emp_id, sst.UserID);
            }
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/add_sibling")
    public String addSiblingApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String name = jin.getString("name");
            String type = jin.getString("type");
            String f_index = jin.getString("f_index");
            String doituong_kd = jin.getString("doituong_kd");
            int ref_id = jin.getInt("ref_id");
            int status = jin.getInt("status");
            int kd_id = jin.getInt("kd_id");

            if (mcExtend.isFrameIndexExisted(type, f_index)) {
                return "{\"code\":805, \"description\":\"Nhánh đã tồn tại trong hệ thống\"}";
            }

            mcExtend.addSiblingFrame(type, ref_id, f_index, name, sst.UserID, status, doituong_kd, kd_id);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/add_child")
    public String addChildApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String name = jin.getString("name");
            String type1 = jin.getString("type");
            String type = type1.equals("lv") ? "tc" : "tieuchi";
            String f_index = jin.getString("f_index");
            String doituong_kd = jin.getString("doituong_kd");
            int ref_id = jin.getInt("ref_id");
            int status = jin.getInt("status");
            int kd_id = jin.getInt("kd_id");

            if (mcExtend.isFrameIndexExisted(type, f_index)) {
                return "{\"code\":805, \"description\":\"Nhánh đã tồn tại trong hệ thống\"}";
            }

            mcExtend.addChildFrame(type, ref_id, f_index, name, sst.UserID, status, doituong_kd, kd_id);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/f_edit")
    public String editFrameApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("id");
            String label = jin.getString("label");
            mcExtend.updateFrame(id, label);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/f_delete")
    public String deleteFrameApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("id");
            mcExtend.deleteFrame(id);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/toggle")
    public String toggleLockStateApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("id");
            int is_locked = jin.getInt("is_locked");
            mcExtend.updateState(id, is_locked);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/upload_mc_frame")
    public String uploadMCFrameApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            int status = jin.getInt("status");
            String doituong_kd = jin.getString("doituong_kd");
            JSONArray jsDef = jin.getJSONArray("def");
            JSONArray jsaData = jin.getJSONArray("data");

            mcUp.uploadMCFrame(jsDef, jsaData, kd_id, doituong_kd, status, sst.UserID);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @GetMapping("/exportZip")
    public void download(HttpServletResponse response) {
        try {
            String archiveName = "C:/kdgd/1/csgd/bb/test.zip";
            File zipFile = new File(archiveName);
            if (!zipFile.getParentFile().exists()) zipFile.getParentFile().mkdirs();
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archiveName))) {
                File dir = new File("C:/kdgd/1/csgd/bb");
                if (dir.exists() && dir.isDirectory()) {
                    for (String s : dir.list()) {
                        File toCompress = new File(dir, s);
                        if (toCompress.isFile()) {
                            try (FileInputStream fis = new FileInputStream(toCompress)) {
                                zos.putNextEntry(new ZipEntry(s));
                                byte[] buf = new byte[1024];
                                int len;
                                while ((len = fis.read(buf)) > 0) {
                                    zos.write(buf, 0, len);
                                }
                            }
                        }
                    }
                }
            }
            // Logic to send file back via HttpServletResponse if needed
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
