package com.common;

import java.sql.SQLException;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/common")
public class CommonService {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ------------------------------
	@PostMapping("/hello")
	public String Hello(@RequestBody String sReq) {

		System.out.println("-------Hello:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			int param = jsonobjReq.getInt("param");

			jout.put("counter1", 1234);
			jout.put("counter2", 2345);
			jout.put("counter3", 3456);
			jout.put("counter4", 4567);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + e + "\"}";
		}

		System.out.println("RES(Hello):" + jout.toString());
		return jout.toString();
	}

	// ------------------------------------------------------------------------------------
	@PostMapping("/getmyorginfo")
	public String GetMyOrgInfo(@RequestBody String sReq) {
		System.out.println("ezCityAPIs---------GetMyOrgInfo:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\""
						+ "Người sử dụng chưa đăng nhập" + "\"}";

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(GetMyOrgInfo):" + jout.toString());
		return jout.toString();
	}

	// ------------------------------
	@PostMapping("/listwsclient")
	public String listWsClient(@RequestBody String sReq) {

		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\""
						+ "Người sử dụng chưa đăng nhập" + "\"}";

			if (sst.UserType != 1 && sst.UserType != 2)
				return "{\"code\":" + 701 + ", \"description\":\""
						+ "Không có quyền" + "\"}";

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + e + "\"}";
		}

		return jout.toString();
	}

	// ============================================================================================================
	@PostMapping("/getdashboardcounter")
	public String getDashboardCounter(@RequestBody String sReq) {
		System.out.println("-------getDashboardCounter:" + sReq);

		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "ChÆ°a Ä‘Äƒng nháº­p" + "\"}";

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"success\":" + false + ", \"description\":\"" + "JSON parse error" + "\"}";
		}

		System.out.println(" RES(getDashboardCounter):" + jout.toString());
		return jout.toString();
	}
}
