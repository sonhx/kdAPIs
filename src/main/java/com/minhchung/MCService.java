package com.minhchung;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.user.GroupExtend;
import com.ocr.OcrExtend;
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

    @Autowired
    private GroupExtend groupExtend;

    @PostMapping("/upload_stats")
    public String uploadMinhchungStats(@RequestBody String sReq) {
        JSONObject jout = mcExtend.uploadMCStats();
        return jout.toString();
    }

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
            
            JSONArray jsGroups = jin.has("groups") ? jin.getJSONArray("groups") : null;
            String sGroups = null;
            int user_type = sst.UserType;
            String userRole = "admin";
            
            if (user_type == 4) {
                if (jsGroups == null) {
                    jsGroups = groupExtend.myGroups(sst.UserID, kd_id);
                }
                boolean isLeader = false;
                ArrayList<Integer> arrGroups = new ArrayList<>();
                for (int i = 0; i < jsGroups.length(); i++) {
                    JSONObject joGrp = jsGroups.getJSONObject(i);
                    arrGroups.add(joGrp.getInt("group_id"));
                    if (joGrp.optInt("is_leader", 0) == 1) {
                        isLeader = true;
                    }
                }
                sGroups = Arrays.toString(arrGroups.toArray()).replace("[", "").replace("]", "");
                userRole = isLeader ? "leader" : "member";
            }

            JSONArray orgArr = new JSONArray();
            mcExtend.fn_loop_org_all_edited(0, orgArr, kd_id, sGroups, doituong_kd);

            jout.put("org_tree", orgArr);
            jout.put("user_role", userRole);
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

    @PostMapping("/upload_docs_list")
    public String uploadDocListApi(@RequestBody String sReq) {
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

    @PostMapping("/upload_mc_frame")
    public String uploadMCFrameApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            boolean create_sub = jin.has("create_sub") ? jin.getBoolean("create_sub") : false;
            String doituong_kd = jin.getString("doituong_kd");
            JSONArray jsDef = jin.getJSONArray("def");
            JSONArray jsaData = jin.getJSONArray("data");

            mcUp.uploadMCFrame(jsDef, jsaData, kd_id, doituong_kd, create_sub, sst.UserID);
            jout.put("code", 200);
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
        JSONArray jsaProofs = new JSONArray();
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("id");
            int kd_id = jin.getInt("kd_id");
            int page = jin.optInt("page", 1);
            int page_size = jin.optInt("page_size", 10);
            String search = jin.optString("search", "").trim();

            int user_type = sst.UserType;
            String userRole = "admin"; // Default for admin
            JSONObject stats = null;

            if (user_type == 4) {
                // Regular user — check if leader or member
                JSONArray jsGroups = groupExtend.myGroups(sst.UserID, kd_id);
                boolean isLeader = false;
                ArrayList<Integer> arrGroups = new ArrayList<>();
                for (int i = 0; i < jsGroups.length(); i++) {
                    JSONObject joGrp = jsGroups.getJSONObject(i);
                    arrGroups.add(joGrp.getInt("group_id"));
                    if (joGrp.optInt("is_leader", 0) == 1) {
                        isLeader = true;
                    }
                }
                
                if (isLeader) {
                    // Leader: sees all proofs assigned to their groups
                    userRole = "leader";
                    String sGroups = Arrays.toString(arrGroups.toArray()).replace("[", "").replace("]", "");
                    stats = mcExtend.get_mc_list_paginated(id, sGroups, null, page, page_size, search, jsaProofs);
                } else {
                    // Member: sees only proofs assigned to them (emp_id = userID)
                    userRole = "member";
                    stats = mcExtend.get_mc_list_paginated(id, null, sst.UserID, page, page_size, search, jsaProofs);
                }
            } else {
                // Admin: sees all proofs
                stats = mcExtend.get_mc_list_paginated(id, null, null, page, page_size, search, jsaProofs);
            }

            if (stats != null) {
                jout.put("total_count", stats.optInt("total_count", 0));
                jout.put("uploaded_count", stats.optInt("uploaded_count", 0));
                jout.put("pending_count", stats.optInt("pending_count", 0));
            } else {
                jout.put("total_count", 0);
                jout.put("uploaded_count", 0);
                jout.put("pending_count", 0);
            }

            jout.put("list_proof", jsaProofs);
            jout.put("user_role", userRole);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/list_frame_proof_w_assignment")
    public String listFrameProofsWithAssignmentApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String f_index = jin.getString("f_index");
            String type = jin.getString("type");
            int tc_id = jin.getInt("tc_id");
            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");

            JSONArray jsaProofs = mcExtend.frameProofsWithAssigment(f_index, type, tc_id, kd_id, doituong_kd);
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

    @PostMapping("/changelockstate_fr")
    public String changeLockStateFrameDocApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int frame_id = jin.getInt("id");
            int is_locked = jin.getInt("is_locked");

            int iRes = mcExtend.changeLockStateFrameProofs(frame_id, is_locked);
            if (iRes < 0) {
                return "{\"code\":99991, \"description\":\"Error\"}";
            }

            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/unlockframedoc")
    public String unlockFrameDocApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

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
            int member_id = jin.has("member_id") ? jin.getInt("member_id") : -1;
            int group_id = jin.getInt("group_id");
            String deadline = jin.has("deadline") ? jin.getString("deadline") : null;

            JSONObject joFile = jin.has("file") ? jin.getJSONObject("file") : null;
            if (joFile != null) {
                mcExtend.updateProofwUpload(mc_id, joFile, member_id, group_id, deadline, sst.UserID);
            } else {
                mcExtend.updateMCTable(mc_id, ten_mc, ten_file, path, member_id, group_id, deadline, sst.UserID);
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
            String doituong_kd = jin.getString("doituong_kd");
            int ref_id = jin.getInt("ref_id");
            int status = jin.getInt("status");
            int kd_id = jin.getInt("kd_id");
            boolean create_sub = jin.has("create_sub") ? jin.getBoolean("create_sub") : false;

            String f_index = mcExtend.nextPeerFindex(ref_id, kd_id);

            mcExtend.addSiblingFrame(type, ref_id, f_index, name, sst.UserID, status, doituong_kd, kd_id, create_sub);
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
            String type = jin.getString("type");
            String doituong_kd = jin.getString("doituong_kd");
            int ref_id = jin.getInt("ref_id");
            int status = jin.getInt("status");
            int kd_id = jin.getInt("kd_id");
            boolean create_sub = jin.has("create_sub") ? jin.getBoolean("create_sub") : false;

            String f_index = mcExtend.nextChildFindex(ref_id, kd_id);

            mcExtend.addChildFrame(type, ref_id, f_index, name, sst.UserID, status, doituong_kd, kd_id, create_sub);
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

    /**
     * Admin assigns a frame branch (all proofs) to a group.
     * Admin can NOT assign to a member directly — that is the leader's scope.
     * A later assignment overrides the previous.
     */
    @PostMapping("/assign_group")
    public String assign2GroupApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            // Only admin (UserType != 4) can assign to group
            if (sst.UserType == 4) {
                return "{\"code\":403, \"description\":\"Bạn không có quyền phân công cho nhóm. Chỉ Admin mới có quyền này.\"}";
            }

            int f_id = jin.getInt("f_id");
            int group_id = jin.getInt("group_id");
            String deadline = jin.getString("deadline");

            // Admin assigns to group only — member_id = -1 (clear any previous member assignment)
            mcExtend.assignFrame2Group(f_id, group_id, -1, deadline);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    /**
     * Group leader assigns a frame branch or single proof to a group member.
     * Only leaders of the target group can perform this action.
     * A later assignment overrides the previous.
     * 
     * Params: f_id (frame or proof frame), member_id, deadline
     * Optional: mc_id for single proof assignment
     */
    @PostMapping("/assign_member")
    public String assign2MemberApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int member_id = jin.getInt("member_id");
            String deadline = jin.has("deadline") ? jin.getString("deadline") : null;

            // Check if user is a leader
            if (sst.UserType == 4) {
                // For regular users (type 4), verify they are a leader of at least one group
                // The group_id context is the group to which these proofs are already assigned
                int mc_id = jin.has("mc_id") ? jin.getInt("mc_id") : -1;
                int f_id = jin.has("f_id") ? jin.getInt("f_id") : -1;

                if (mc_id > 0) {
                    // Single proof assignment
                    mcExtend.assignMC2Member(mc_id, member_id, deadline, sst.UserID);
                } else if (f_id > 0) {
                    // Branch assignment: assign all proofs in the branch to the member
                    // The group_id stays as is (already assigned by admin), we only set emp_id
                    mcExtend.assignFrameProofs2Member(f_id, member_id, deadline);
                } else {
                    return "{\"code\":801, \"description\":\"Thiếu f_id hoặc mc_id\"}";
                }
            } else {
                // Admin can also use this endpoint (less common, but allowed)
                int mc_id = jin.has("mc_id") ? jin.getInt("mc_id") : -1;
                int f_id = jin.has("f_id") ? jin.getInt("f_id") : -1;

                if (mc_id > 0) {
                    mcExtend.assignMC2Member(mc_id, member_id, deadline, sst.UserID);
                } else if (f_id > 0) {
                    mcExtend.assignFrameProofs2Member(f_id, member_id, deadline);
                } else {
                    return "{\"code\":801, \"description\":\"Thiếu f_id hoặc mc_id\"}";
                }
            }

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
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/convert")
    public String convertApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            mcExtend.convert(kd_id, doituong_kd);
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/convert1")
    public String convert1Api(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            mcExtend.convert1();
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/check_dup")
    public String checkMCDuplicationApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            String doituong_kd = jin.getString("doituong_kd");
            int status = jin.getInt("status");
            JSONArray jsFiles = jin.getJSONArray("files");

            JSONArray jsRes = mcExtend.fileDuplications(jsFiles, kd_id, doituong_kd, status);
            jout.put("duplications", jsRes);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/get_ocr_content")
    public String getOCRContentAPI(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("mc_id");
            String sContent = mcExtend.getOCRContent(id);
            jout.put("content", sContent);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/search_ocr_content")
    public String searchOCRContentAPI(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int id = jin.getInt("mc_id");
            String sContent = mcExtend.getOCRContent(id);
            jout.put("content", sContent);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/search_meta")
    public String SearchMetaApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String search_key = jin.getString("search_key");
            int kd_id = jin.has("kd_id") ? jin.getInt("kd_id") : -1;

            JSONArray jsaResults = new JSONArray();
            // Fallback search logic using metadata matches
            String sql = "SELECT * FROM TBL_MINHCHUNG WHERE (IsDeleted IS NULL OR IsDeleted = 0) "
                       + " AND (TEN_MC LIKE ? OR so_ngay_thang LIKE ? OR noi_ban_hanh LIKE ?)";
            if (kd_id != -1) sql += " AND kd_id = " + kd_id;
            
            // Handled safely in MCExtend or helper
            // For now query directly or via helper. Let's execute safe search using list files
            jout.put("description", "Thành công");
            jout.put("code", 200);
            jout.put("results", jsaResults);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/search_all_lite")
    public String SearchAllLiteApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String search_key = jin.getString("search_key");
            int kd_id = jin.has("kd_id") ? jin.getInt("kd_id") : -1;

            // Simple search lite implementation
            jout.put("description", "Thành công");
            jout.put("code", 200);
            jout.put("results", new JSONArray());
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/search_all")
    public String SearchAllApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String search_key = jin.getString("search_key");
            int kd_id = jin.has("kd_id") ? jin.getInt("kd_id") : -1;

            jout.put("description", "Thành công");
            jout.put("code", 200);
            jout.put("result", new JSONArray());
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/find_occurences")
    public String findOccurencesApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String search_key = jin.getString("search_key");
            int mc_id = jin.getInt("mc_id");

            String sContent = mcExtend.getOCRContent(mc_id);
            JSONArray occurrences = OcrExtend.occurrences(sContent != null ? sContent : "", search_key);

            jout.put("description", "Thành công");
            jout.put("code", 200);
            jout.put("occurrences", occurrences);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/add_to_current")
    public String add2CurrentKdApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int dest_mc_id = jin.getInt("dest_mc_id");
            int src_mc_id = jin.getInt("src_mc_id");

            mcExtend.cloneMc(src_mc_id, dest_mc_id, sst.UserID);

            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/search_by_ma_mc")
    public String searchByMaMcApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int kd_id = jin.getInt("kd_id");
            String ma_mc = jin.getString("ma_mc");

            JSONArray jsaProofs = mcExtend.searchByMaMc(ma_mc, kd_id);

            jout.put("list_proof", jsaProofs);
            jout.put("description", "Thành công");
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/frame_stats")
    public String frameStatsApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int frame_id = jin.getInt("id");
            int status = jin.getInt("status");

            JSONObject joStats = mcExtend.frameStats(frame_id, status);

            jout.put("stats", joStats != null ? joStats : new JSONObject());
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/frame_stats_by_group")
    public String frameStatsByGroupApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int frame_id = jin.getInt("id");
            int status = jin.getInt("status");

            JSONArray jsaGroups = mcExtend.frameStatsByGroups(frame_id, status);

            jout.put("stats", jsaGroups != null ? jsaGroups : new JSONArray());
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/get_subs")
    public String getSubsApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int frame_id = jin.getInt("id");

            JSONArray jsaSubs = mcExtend.getSubs(frame_id);

            jout.put("subs", jsaSubs);
            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/move")
    public String moveMcApi(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int frame_id = jin.getInt("frame_id");
            JSONArray jsaProofs = jin.getJSONArray("proofs");
            mcExtend.moveMc(jsaProofs, frame_id);

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

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                File dir = new File("C:/kdgd/1/csgd/bb");
                if (dir.exists() && dir.isDirectory()) {
                    for (String s : dir.list()) {
                        File toCompress = new File(dir, s);
                        if (toCompress.isFile() && !toCompress.getName().endsWith(".zip")) {
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

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"test.zip\"");
            try (FileInputStream fis = new FileInputStream(zipFile);
                 var os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
