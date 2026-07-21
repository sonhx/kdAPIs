package com.kd;

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
@RequestMapping("/kd")
public class KdService {

	@Autowired
	private KdExtend kdExtend;

	@Autowired
	private SessionService sessionService;

	@PostMapping("/list")
	public String listKdApi(@RequestBody String sReq) {
		System.out.println("-------listKdApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int loai_hinh = jin.has("loai_hinh") ? jin.getInt("loai_hinh") : -1;
			int ct_id = jin.has("ct_id") ? jin.getInt("ct_id") : -1;

			JSONArray jsaKds = kdExtend.listKdbyCT(loai_hinh, ct_id);
			jout.put("list_kd", jsaKds);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listKdApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/list_cycle_ed")
	public String listCycleApi_ed(@RequestBody String sReq) {
		System.out.println("-------listCycleApi_ed:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			int loai_hinh = jin.has("loai_hinh") ? jin.getInt("loai_hinh") : -1;
			int ct_id = jin.has("ct_id") ? jin.getInt("ct_id") : -1;

			JSONArray jsaCycles = kdExtend.listKdbyCT(loai_hinh, ct_id);
			jout.put("list_cycle", jsaCycles);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listCycleApi_ed):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/list_nganh_dt")
	public String listNganhDTApi(@RequestBody String sReq) {
		System.out.println("-------listNganhDTApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			int kd_id = jin.has("kd_id") ? jin.getInt("kd_id") : -1;

			JSONArray jsaCTKds = kdExtend.listNganhDT(kd_id);
			jout.put("list_ct", jsaCTKds);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listNganhDTApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/edit")
	public String editKdApi(@RequestBody String sReq) {
		System.out.println("-------editKdApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			int loai_hinh_id = jin.getInt("loai_hinh_id");
			int FromYear = jin.getInt("_from");
			int ToYear = jin.getInt("_to");
			int cycle = jin.getInt("cycle");
			int standard_id = jin.getInt("standard_id");
			int status = jin.getInt("status");

			kdExtend.updateKd(id, loai_hinh_id, FromYear, ToYear, cycle, standard_id, status, sst.UserID);
			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(editKdApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/delete")
	public String deleteKdApi(@RequestBody String sReq) {
		System.out.println("-------deleteKdApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			kdExtend.deleteKd(id);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(deleteKdApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/is_deletable")
	public String isKDDeletableApi(@RequestBody String sReq) {
		System.out.println("-------isKDDeletableApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			if (kdExtend.isDeletable(id)) {
				jout.put("is_deletable", true);
			} else {
				jout.put("is_deletable", false);
			}
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(isKDDeletableApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/setcurrent")
	public String setCurrent(@RequestBody String sReq) {
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session = jin.getString("session_id");
			int id = jin.getInt("id");

			struct_session sst = sessionService.getSessionInfo(session);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			kdExtend.setCurrentKd(id, sst.UserID);
			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error:" + e + "\"}";
		}
		return jout.toString();
	}

	@PostMapping("/add")
	public String addKdApi(@RequestBody String sReq) {
		System.out.println("-------addKdApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String ten = jin.has("ten") ? jin.getString("ten") : "";
			String ghi_chu = jin.has("ghi_chu") ? jin.getString("ghi_chu") : "";
			int loai_hinh_id = jin.getInt("loai_hinh_id");
			int FromYear = jin.getInt("_from");
			int ToYear = jin.getInt("_to");
			int ct_id = jin.getInt("ct_id");
			int standard_id = jin.getInt("standard_id");

			kdExtend.addKd(ten, loai_hinh_id, FromYear, ToYear, ct_id, standard_id, ghi_chu, sst.UserID);
			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(addKdApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/create_standard")
	public String createStandardApi(@RequestBody String sReq) {
		System.out.println("-------createStandardApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String ten = jin.getString("ten");
			String abbr = jin.has("abbr") ? jin.getString("abbr") : "";
			int loaihinh_id = jin.getInt("loaihinh_id");

			kdExtend.createStandard(ten, abbr, loaihinh_id, sst.UserID);
			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(createStandardApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/list_standard")
	public String listStandardApi(@RequestBody String sReq) {
		System.out.println("-------listStandardApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			int loaihinh_id = jin.has("loaihinh_id") ? jin.getInt("loaihinh_id") : -1;

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			JSONArray jsaStandards = kdExtend.listStandard(loaihinh_id);
			jout.put("list_standard", jsaStandards);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listStandardApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/list_loaihinh")
	public String listLoaihinhApi(@RequestBody String sReq) {
		System.out.println("-------listLoaihinhApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			JSONArray jsaLoaihinh = kdExtend.listLoaihinh();
			jout.put("list", jsaLoaihinh);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listLoaihinhApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/edit_standard")
	public String editStandardApi(@RequestBody String sReq) {
		System.out.println("-------editStandardApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String ten = jin.getString("ten");
			String abbr = jin.getString("abbr");
			int id = jin.getInt("id");

			kdExtend.updateStandard(id, ten, abbr, sst.UserID);
			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(editStandardApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/delete_standard")
	public String deleteStandardApi(@RequestBody String sReq) {
		System.out.println("-------deleteStandardApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			kdExtend.deleteStandard(id);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(deleteStandardApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/create_cycle")
	public String createCycleApi(@RequestBody String sReq) {
		System.out.println("-------createCycleApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String ten = jin.getString("ten");
			int standard_id = jin.getInt("standard_id");
			int _from = jin.getInt("from");
			int _to = jin.getInt("to");

			kdExtend.createCycle(ten, standard_id, _from, _to, sst.UserID);
			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(createCycleApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/list_cycle_by_standard")
	public String listCycleByStandardApi(@RequestBody String sReq) {
		System.out.println("-------listCycleByStandardApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int standard_id = jin.has("standard_id") ? jin.getInt("standard_id") : -1;
			JSONArray jsaCycles = kdExtend.listCycleByStandard(standard_id);

			jout.put("list_cycle", jsaCycles);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listCycleByStandardApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/list_cycle_by_loaihinh")
	public String listCycleByLoaihinhApi(@RequestBody String sReq) {
		System.out.println("-------listCycleByLoaihinhApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");

			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int loaihinh_id = jin.has("loaihinh_id") ? jin.getInt("loaihinh_id") : -1;
			JSONArray jsaCycles = kdExtend.listCycleByLoaihinh(loaihinh_id);

			jout.put("list_cycle", jsaCycles);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(listCycleByLoaihinhApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/edit_cycle")
	public String editCycleApi(@RequestBody String sReq) {
		System.out.println("-------editCycleApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String ten = jin.getString("ten");
			int id = jin.getInt("id");
			int _from = jin.getInt("from");
			int _to = jin.getInt("to");

			kdExtend.updateCycle(id, ten, _from, _to, sst.UserID);
			jout.put("description", "Thành công");
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(editCycleApi):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/delete_cycle")
	public String deleteCycleApi(@RequestBody String sReq) {
		System.out.println("-------deleteCycleApi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String session_id = jin.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int id = jin.getInt("id");
			kdExtend.deleteCycle(id);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số? " + e.getMessage() + "\"}";
		}
		System.out.println("RES(deleteCycleApi):" + jout.toString());
		return jout.toString();
	}
}
