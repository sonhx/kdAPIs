package com.bieumau;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/bm")
public class BmService {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private BmExtend bmExtend;

	@PostMapping("/list")
	public String listBMApi(@RequestBody String sReq) {
		System.out.println("-------listBMApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

			int kd_id = jin.getInt("kd_id");
			String doituong_kd = jin.getString("doituong_kd");

			JSONArray jsaBMs = bmExtend.listBm(kd_id, doituong_kd);
			jout.put("list_bm", jsaBMs);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
		}
		return jout.toString();
	}

	@PostMapping("/edit")
	public String editBmApi(@RequestBody String sReq) {
		System.out.println("-------editBmApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

			String so_vb = jin.has("so_vb") ? jin.getString("so_vb") : "";
			String ngay_bh = jin.has("ngay_bh") ? jin.getString("ngay_bh") : null;
			String ghi_chu = jin.getString("ghi_chu");
			int bm_id = jin.getInt("bm_id");

			bmExtend.updateBm(so_vb, ngay_bh, bm_id, ghi_chu, sst.UserID);
			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
		}
		return jout.toString();
	}

	@PostMapping("/delete")
	public String deleteBmApi(@RequestBody String sReq) {
		System.out.println("-------deleteBmApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

			int id = jin.getInt("id");
			bmExtend.deleteBm(id);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":800, \"description\":\"JSON error: Thieu tham so?\"}";
		}
		return jout.toString();
	}
}
