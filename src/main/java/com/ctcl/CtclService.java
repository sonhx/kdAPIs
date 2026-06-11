package com.ctcl;

import java.io.File;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.config.Config;
import com.file.UploadBase64;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/ctcl")
public class CtclService {

	@Autowired
	private CtclExtend ctclExtend;

	@Autowired
	private SessionService sessionService;

	@PostMapping("/list")
	public String listCtclApi(@RequestBody String sReq) {
		JSONArray jsa = new JSONArray();
		System.out.println("-------listCtclApi:" + sReq);

		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int kd_id = jin.getInt("kd_id");
			String doituong_kd = jin.getString("doituong_kd");
			String user_type = jin.getString("user_type");

			if (user_type.equalsIgnoreCase("phongban")) {
				int org_id = ctclExtend.UserOrg(sst.UserID);
				jsa = ctclExtend.listCtclWithOrg(kd_id, doituong_kd, org_id);
			} else {
				jsa = ctclExtend.listCtcl(kd_id, doituong_kd);
			}

			jout.put("list_ctcl", jsa);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		}

		System.out.println("RES(listCtclApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/edit")
	public String editCtclApi(@RequestBody String sReq) {
		System.out.println("-------editCtclApi:" + sReq);

		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String ghi_chu = jin.getString("ghi_chu");
			int id = jin.getInt("id");
			int org_id = jin.has("org_id") ? jin.getInt("org_id") : -1;
			String so_vb = jin.has("so_vb") ? jin.getString("so_vb") : "";
			String ngay_bh = jin.has("ngay_bh") ? jin.getString("ngay_bh") : null;

			String filename = "", sFile = "";
			String path = null;
			if (jin.has("file")) {
				JSONObject joDetails = ctclExtend.getDetails(id);
				int kd_id = joDetails.getInt("kd_id");
				String doituong_kd = joDetails.getString("doituong_kd");
				path = UploadBase64.fPath(kd_id, doituong_kd, "ctcl", null);
				path += "/";
				String fdir = path.replaceFirst(Config.homePath, Config.homeDir);
				File dir = new File(fdir);
				if (!dir.exists()) {
					dir.mkdirs();
				}

				JSONObject joFile = jin.getJSONObject("file");
				filename = joFile.getString("filename");

				sFile = fdir + File.separator + filename;
				File file = new File(sFile);
				if (!file.exists()) {
					file.createNewFile();
				}
				String b64 = joFile.getString("base64");
				UploadBase64.b64Decode(b64, sFile);
			}
			ctclExtend.updateCtcl(id, org_id, so_vb, ngay_bh, path, ghi_chu, sst.UserID);

			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("RES(editCtclApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/toggle")
	public String toggleLockStateApi(@RequestBody String sReq) {
		System.out.println("-------toggleLockStateApi:" + sReq);

		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			int is_locked = jin.getInt("is_locked");
			ctclExtend.updateStateCtcl(id, is_locked);

			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		}

		System.out.println("RES(toggleLockStateApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/assign")
	public String assignCtclApi(@RequestBody String sReq) {
		System.out.println("-------assignCtclApi:" + sReq);

		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String noi_dung = jin.getString("noi_dung");
			String ghi_chu = jin.has("ghi_chu") ? jin.getString("ghi_chu") : "";
			int kd_id = jin.getInt("kd_id");
			String doituong_kd = jin.getString("doituong_kd");
			String mode = jin.getString("mode");
			if (mode.equals("assign")) {
				int org_id = jin.getInt("org_id");
				String thoi_han = jin.has("thoi_han") ? jin.getString("thoi_han") : null;
				ctclExtend.assignCtcl(org_id, noi_dung, thoi_han, ghi_chu, sst.UserID, kd_id, doituong_kd);
			} else {
				String filename = "", sFile = "";
				String path = null;

				String so_vb = jin.has("so_vb") ? jin.getString("so_vb") : "";
				String ngay_bh = jin.has("ngay_bh") ? jin.getString("ngay_bh") : null;

				path = UploadBase64.fPath(kd_id, doituong_kd, "ctcl", null);
				path += "/";
				String fdir = path.replaceFirst(Config.homePath, Config.homeDir);
				File dir = new File(fdir);
				if (!dir.exists()) {
					dir.mkdirs();
				}

				JSONObject joFile = jin.getJSONObject("file");
				filename = joFile.getString("filename");
				path += filename;
				sFile = fdir + File.separator + filename;
				File file = new File(sFile);
				if (!file.exists()) {
					file.createNewFile();
				}
				String b64 = joFile.getString("base64");
				UploadBase64.b64Decode(b64, sFile);

				ctclExtend.assignCtcl_upload(noi_dung, filename, so_vb, ngay_bh, path, ghi_chu, sst.UserID, kd_id, doituong_kd);
			}

			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("RES(assignCtclApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/delete")
	public String deleteCtclApi(@RequestBody String sReq) {
		System.out.println("-------deleteCtclApi:" + sReq);

		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			ctclExtend.deleteCtcl(id);

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		}

		System.out.println("RES(deleteCtclApi):" + jout.toString());
		return jout.toString();
	}
}


