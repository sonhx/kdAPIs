package com.daotao;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Calendar;

import org.json.JSONArray;
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
@RequestMapping("/daotao")
public class DaotaoService {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private DaotaoExtend daotaoExtend;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	@org.springframework.beans.factory.annotation.Qualifier("evidenceJdbcTemplate")
	private JdbcTemplate evidenceJdbcTemplate;

	private final int MAXTRINHDO = 10;
	private final int MAXHEDAOTAO = 10;
	private final int MAX_SECTOR = 100;
	private final int MAXYEAR = 10;
	private final int STARTYEAR = 2015;
	private final int MAXTSYEAR = 10;

	// ---------------------------------------------------------------------------------------
	private int fn_get_trinh_do(String trinh_do) {
		String sql = "select Value from dbo.DEF_TRINHDO WHERE UPPER(Name) LIKE UPPER(?)";
		List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("Value"), "%" + trinh_do + "%");
		return list.isEmpty() ? -1 : list.get(0);
	}

	// ---------------------------------------------------------------------------------------
	private int fn_get_he_dao_tao(String hdt) {
		String sql = "select Value from dbo.DEF_HEDAOTAO WHERE UPPER(Code) LIKE UPPER(?)";
		List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("Value"), "%" + hdt + "%");
		return list.isEmpty() ? -1 : list.get(0);
	}

	private int fn_get_org_csdt(String co_so) {
		String sql = "select ID from dbo.TBL_ORG WHERE UPPER(Code) LIKE UPPER(?)";
		List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("ID"), "%" + co_so + "%");
		return list.isEmpty() ? -1 : list.get(0);
	}

	// ---------------------------------------------------------------------------------------
	private String fn_trinhdo_name(int trinhdo_id) {
		String sql = "select Name from dbo.DEF_TRINHDO where Value = ?";
		List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Name"), trinhdo_id);
		return list.isEmpty() ? "unknown" : list.get(0);
	}

	// ---------------------------------------------------------------------------------------
	private String fn_nganhhoc_name(String ma_nganh) {
		String sql = "select Ten from dbo.DEF_NGANHHOC where MaNganh = ?";
		List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Ten"), ma_nganh);
		return list.isEmpty() ? "unknown" : list.get(0);
	}

	// ---------------------------------------------------------------------------------------
	private String fn_place_name(int place_id) {
		String sql = "select Name from dbo.TBL_ORG where ID = ?";
		List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Name"), place_id);
		return list.isEmpty() ? "unknown" : list.get(0);
	}

	// ---------------------------------------------------------------------------------------
	private String fn_trangthaihv_name(int trangthai) {
		String sql = "select Name from dbo.DEF_TRANGTHAI_HOCVIEN where Value = ?";
		List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Name"), trangthai);
		return list.isEmpty() ? "unknown" : list.get(0);
	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/listhedaotao")
	public String listHeDaoTao(@RequestBody String sReq) {

		System.out.println("----------listHeDaoTao:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			// JSONObject jsonobjReq = new JSONObject(sReq); // Not used in original

			List<JSONObject> list = jdbcTemplate.query(
					"select * from DEF_HEDAOTAO where (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("value", rs.getInt("Value"));
							obj.put("name", rs.getString("Name"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					});

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("hedaotao_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listHeDaoTao):" + jout.toString());

		return jout.toString();
	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/listtrangthaihocvien")
	public String listTrangThaiHocVien(@RequestBody String sReq) {

		System.out.println("----------listTrangThaiHocVien:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			List<JSONObject> list = jdbcTemplate.query(
					"select * from DEF_TRANGTHAI_HOCVIEN where (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("value", rs.getInt("Value"));
							obj.put("name", rs.getString("Name"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					});

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("thangthaihocvien_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listTrangThaiHocVien):" + jout.toString());

		return jout.toString();

	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/listtrinhdo")
	public String listTrinhDo(@RequestBody String sReq) {

		System.out.println("----------listTrinhDo:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			List<JSONObject> list = jdbcTemplate.query(
					"select * from DEF_TRINHDO where (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("value", rs.getInt("Value"));
							obj.put("name", rs.getString("Name"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					});

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("trinhdo_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listTrinhDo):" + jout.toString());

		return jout.toString();

	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/listquoctich")
	public String listQuocTich(@RequestBody String sReq) {

		System.out.println("----------listQuocTich:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			List<JSONObject> list = jdbcTemplate.query(
					"select * from DEF_QUOCTICH where (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("value", rs.getInt("Value"));
							obj.put("name", rs.getString("Name"));
							obj.put("code", rs.getString("Code"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					});

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("trinhdo_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listQuocTich):" + jout.toString());

		return jout.toString();

	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/listnganhhoc")
	public String listNganhHoc(@RequestBody String sReq) {

		System.out.println("----------listNganhHoc:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			int trinh_do = jsonobjReq.getInt("trinh_do");

			List<JSONObject> list = jdbcTemplate.query(
					"select * from DEF_NGANHHOC where TrinhDo=? and (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("trinhdo", rs.getString("TrinhDo"));
							obj.put("manganh", rs.getString("MaNganh"));
							obj.put("ten", rs.getString("Ten"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					}, trinh_do);

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("nganhhoc_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listNganhHoc):" + jout.toString());

		return jout.toString();
	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/listchitieutuyensinh")
	public String listChiTieuTuyenSinh(@RequestBody String sReq) {

		System.out.println("----------listChiTieuTuyenSinh:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			int year = 0;
			if (jsonobjReq.has("year"))
				year = jsonobjReq.getInt("year");
			int trinhdo = 0;
			if (jsonobjReq.has("trinhdo"))
				trinhdo = jsonobjReq.getInt("trinhdo");
			String manganh = "";
			if (jsonobjReq.has("manganh"))
				manganh = jsonobjReq.getString("manganh");
			int place_id = 0;
			if (jsonobjReq.has("place_id"))
				place_id = jsonobjReq.getInt("place_id");
			int hedaotao = 0;
			if (jsonobjReq.has("hedaotao"))
				hedaotao = jsonobjReq.getInt("hedaotao");

			StringBuilder sqlStr = new StringBuilder(
					"select * from TBL_CHITIEUTUYENSINH where (IsDeleted=0 or IsDeleted Is NULL)");
			List<Object> params = new ArrayList<>();

			if (!manganh.isEmpty()) {
				sqlStr.append(" and MaNganh = ?");
				params.add(manganh);
			}
			if (year > 0) {
				sqlStr.append(" and Year = ?");
				params.add(year);
			}
			if (trinhdo > 0) {
				sqlStr.append(" and TrinhDo = ?");
				params.add(trinhdo);
			}
			if (place_id > 0) {
				sqlStr.append(" and PlaceID = ?");
				params.add(place_id);
			}
			if (hedaotao > 0) {
				sqlStr.append(" and HeDaoTao = ?");
				params.add(hedaotao);
			}

			System.out.println(sqlStr.toString());

			final DecimalFormat df = new DecimalFormat("0.00");
			List<JSONObject> list = jdbcTemplate.query(sqlStr.toString(), (rs, rowNum) -> {
				JSONObject obj = new JSONObject();
				try {
					obj.put("id", rs.getInt("ID"));
					obj.put("ten_trinh_do", fn_trinhdo_name(rs.getInt("TrinhDo")));
					obj.put("place_name", fn_place_name(rs.getInt("PlaceID")));
					obj.put("place_id", rs.getInt("PlaceID"));
					obj.put("ma_nganh_hoc", rs.getString("MaNganh"));
					obj.put("ten_nganh_hoc", fn_nganhhoc_name(rs.getString("MaNganh")));
					obj.put("year", rs.getInt("Year"));
					obj.put("chi_tieu", rs.getInt("ChiTieu"));
					obj.put("diem_chuan", df.format(rs.getFloat("DiemChuan")));
					obj.put("so_ho_so", rs.getInt("HoSoDuTuyen"));
				} catch (JSONException | SQLException e) {
					e.printStackTrace();
				}
				return obj;
			}, params.toArray());

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("chitieudaotao_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		return jout.toString();
	}

	// --------------------------------------------------------------------------------------------------

	private class struct_sector_cnt_elm {
		int sector_id = 0;
		String sector_name = "";
		String sector_code = "";
		int north_counter = 0;
		int south_counter = 0;
		int chi_tieu_north = 0;
		int ho_so_north = 0;
		float diem_chuan_north = 0;
		int chi_tieu_south = 0;
		int ho_so_south = 0;
		float diem_chuan_south = 0;
	}

	private class struct_one_year_mix_cnt_elm {
		int year = 0;
		struct_sector_cnt_elm counter_list[] = null;
	}

	// ------------------------------------------------------------------
	@PostMapping("/thongkequymodaotaotonghop")
	public String ThongKeQuyMoDaoTaoTongHop(@RequestBody String sReq) {

		System.out.println("----------ThongKeQuyMoDaoTaoTongHop:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			int hedaotao = 0;
			if (jsonobjReq.has("hedaotao"))
				hedaotao = jsonobjReq.getInt("hedaotao");
			int trinhdo = 0;
			if (jsonobjReq.has("trinhdo"))
				trinhdo = jsonobjReq.getInt("trinhdo");

			// -------------Innit
			// mem--------------------------------------------------------------
			struct_one_year_mix_cnt_elm mix_list[] = new struct_one_year_mix_cnt_elm[MAXYEAR];

			for (int i = 0; i < MAXYEAR; i++) {
				mix_list[i] = new struct_one_year_mix_cnt_elm();
				mix_list[i].year = STARTYEAR + i;
				mix_list[i].counter_list = new struct_sector_cnt_elm[MAX_SECTOR];
				for (int j = 0; j < MAX_SECTOR; j++) {
					mix_list[i].counter_list[j] = new struct_sector_cnt_elm();
				}
			}

			// ------------------Fill init
			// data---------------------------------------------------------------
			String sql_nganh = "select * from DEF_NGANHHOC where (IsDeleted=0 or IsDeleted is null)";
			List<Object> nganh_params = new ArrayList<>();
			if (trinhdo > 0) {
				sql_nganh += " and TrinhDo=?";
				nganh_params.add(trinhdo);
			}

			final int finalHedaotao = hedaotao;
			List<Map<String, Object>> nganhRows = jdbcTemplate.queryForList(sql_nganh,
					nganh_params.toArray());
			for (Map<String, Object> rs_nganh : nganhRows) {
				int sector_id = (int) rs_nganh.get("ID");
				String ma_nganh = (String) rs_nganh.get("MaNganh");
				String ten_nganh = (String) rs_nganh.get("Ten");

				for (int i = 0; i < MAXYEAR; i++) {
					for (int j = 0; j < MAX_SECTOR; j++) {
						if (mix_list[i].counter_list[j].sector_id == 0) {
							mix_list[i].counter_list[j].sector_id = sector_id;
							mix_list[i].counter_list[j].sector_name = ten_nganh;
							mix_list[i].counter_list[j].sector_code = ma_nganh;

							StringBuilder sql_chi_tieu = new StringBuilder(
									"select * from TBL_CHITIEUTUYENSINH where (IsDeleted=0 or IsDeleted is null) and Year=? and MaNganh=?");
							List<Object> ct_params = new ArrayList<>();
							ct_params.add(mix_list[i].year);
							ct_params.add(ma_nganh);
							if (finalHedaotao > 0) {
								sql_chi_tieu.append(" and HeDaoTao=?");
								ct_params.add(finalHedaotao);
							}

							final int finalI = i;
							final int finalJ = j;
							List<Map<String, Object>> ctRows = jdbcTemplate.queryForList(sql_chi_tieu.toString(),
									ct_params.toArray());
							for (Map<String, Object> rs_ct : ctRows) {
								int placeId = (int) rs_ct.get("PlaceID");
								if (placeId == 2) {
									mix_list[finalI].counter_list[finalJ].chi_tieu_north = (int) rs_ct.get("ChiTieu");
									mix_list[finalI].counter_list[finalJ].ho_so_north = (int) rs_ct
											.get("HoSoDuTuyen");
									mix_list[finalI].counter_list[finalJ].diem_chuan_north = ((Number) rs_ct
											.get("DiemChuan")).floatValue();
								} else if (placeId == 3) {
									mix_list[finalI].counter_list[finalJ].chi_tieu_south = (int) rs_ct.get("ChiTieu");
									mix_list[finalI].counter_list[finalJ].ho_so_south = (int) rs_ct
											.get("HoSoDuTuyen");
									mix_list[finalI].counter_list[finalJ].diem_chuan_south = ((Number) rs_ct
											.get("DiemChuan")).floatValue();
								}
							}
							break;
						}
					}
				}
			}

			// ---------------Fill
			// data------------------------------------------------------------------------
			for (int i = 0; i < MAXYEAR; i++) {
				if (mix_list[i].year == 0)
					break;
				for (int j = 0; j < MAX_SECTOR; j++) {
					if (mix_list[i].counter_list[j].sector_id == 0)
						break;

					String sql_base = "select count(*) as counter from TBL_HOCVIEN where (IsDeleted=0 or IsDeleted is null) and NamNhapHoc=? and NganhHoc=?";

					// North
					StringBuilder sql_n = new StringBuilder(sql_base);
					List<Object> params_n = new ArrayList<>();
					params_n.add(mix_list[i].year);
					params_n.add(mix_list[i].counter_list[j].sector_id);
					if (finalHedaotao > 0) {
						sql_n.append(" and HeDaoTao=?");
						params_n.add(finalHedaotao);
					}
					sql_n.append(" and CoSoDaoTao=2");

					Integer n_count = jdbcTemplate.queryForObject(sql_n.toString(), Integer.class, params_n.toArray());
					mix_list[i].counter_list[j].north_counter = (n_count != null) ? n_count : 0;

					// South
					StringBuilder sql_s = new StringBuilder(sql_base);
					List<Object> params_s = new ArrayList<>();
					params_s.add(mix_list[i].year);
					params_s.add(mix_list[i].counter_list[j].sector_id);
					if (finalHedaotao > 0) {
						sql_s.append(" and HeDaoTao=?");
						params_s.add(finalHedaotao);
					}
					sql_s.append(" and CoSoDaoTao=3");

					Integer s_count = jdbcTemplate.queryForObject(sql_s.toString(), Integer.class, params_s.toArray());
					mix_list[i].counter_list[j].south_counter = (s_count != null) ? s_count : 0;
				}
			}

			// ----------------Dump-------------------------------------------------------
			JSONArray jar = new JSONArray();
			JSONObject obj;
			final DecimalFormat df = new DecimalFormat("0.00");
			for (int i = MAXYEAR - 1; i >= 0; i--) {
				if (mix_list[i].year == 0)
					break;

				obj = new JSONObject();
				obj.put("year", mix_list[i].year);

				JSONArray jar1 = new JSONArray();
				JSONObject obj1;

				for (int j = 0; j < MAX_SECTOR; j++) {
					if (mix_list[i].counter_list[j].sector_id == 0)
						break;

					obj1 = new JSONObject();
					obj1.put("sector_id", mix_list[i].counter_list[j].sector_id);
					obj1.put("sector_name", mix_list[i].counter_list[j].sector_name);
					obj1.put("sector_code", mix_list[i].counter_list[j].sector_code);

					obj1.put("north_counter", mix_list[i].counter_list[j].north_counter);
					obj1.put("south_counter", mix_list[i].counter_list[j].south_counter);
					obj1.put("total_counter", mix_list[i].counter_list[j].north_counter
							+ mix_list[i].counter_list[j].south_counter);

					obj1.put("chitieu_north", mix_list[i].counter_list[j].chi_tieu_north);
					obj1.put("chitieu_south", mix_list[i].counter_list[j].chi_tieu_south);
					obj1.put("chitieu_total", mix_list[i].counter_list[j].chi_tieu_north
							+ mix_list[i].counter_list[j].chi_tieu_south);

					obj1.put("so_ho_so_north", mix_list[i].counter_list[j].ho_so_north);
					obj1.put("so_ho_so_south", mix_list[i].counter_list[j].ho_so_south);

					obj1.put("diem_chuan_north", df.format(mix_list[i].counter_list[j].diem_chuan_north));
					obj1.put("diem_chuan_south", df.format(mix_list[i].counter_list[j].diem_chuan_south));

					jar1.put(obj1);
				}
				obj.put("sector_list", jar1);

				jar.put(obj);
			}

			// ---------------------------------------------------------------------------
			jout.put("thongketonghop_list", jar);
			if (hedaotao > 0)
				jout.put("hedaotao", hedaotao);
			jout.put("code", 200);

		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\": 801, \"description\": \"Error\"}";
		}
		System.out.println("RES(ThongKeQuyMoDaoTaoTongHop):" + jout.toString());

		return jout.toString();

	}

	// ---------------------------------------------------------------------------------------------------
	private class struct_one_year_sector_cnt_elm {
		int year = 0;
		int chi_tieu = 0;
		int north_counter = 0;
		int south_counter = 0;
	}

	// --------------------------------------------------------------------------------------------------
	@PostMapping("/thongkequymodaotaotheonganh")
	public String ThongKeQuyMoDaoTaoTheoNganh(@RequestBody String sReq) {

		System.out.println("----------ThongKeQuyMoDaoTaoTheoNganh:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			int trinhdo = 0;
			if (jsonobjReq.has("trinhdo"))
				trinhdo = jsonobjReq.getInt("trinhdo");
			int nganh = 0;
			if (jsonobjReq.has("nganh"))
				nganh = jsonobjReq.getInt("nganh");
			int hedaotao = 0;
			if (jsonobjReq.has("hedaotao"))
				hedaotao = jsonobjReq.getInt("hedaotao");

			struct_one_year_sector_cnt_elm sector_list[] = new struct_one_year_sector_cnt_elm[MAXYEAR];

			for (int i = 0; i < MAXYEAR; i++) {
				sector_list[i] = new struct_one_year_sector_cnt_elm();
				sector_list[i].year = STARTYEAR + i;

				StringBuilder sqlBuilder = new StringBuilder(
						"select * from TBL_CHITIEUTUYENSINH where (IsDeleted=0 or IsDeleted is null) and Year=?");
				List<Object> ct_params = new ArrayList<>();
				ct_params.add(sector_list[i].year);

				if (hedaotao > 0) {
					sqlBuilder.append(" and HeDaoTao=?");
					ct_params.add(hedaotao);
				}
				if (trinhdo > 0) {
					sqlBuilder.append(" and TrinhDo=?");
					ct_params.add(trinhdo);
				}
				if (nganh > 0) {
					String maNganh = jdbcTemplate.queryForObject("select MaNganh from DEF_NGANHHOC where ID=?",
							String.class, nganh);
					if (maNganh != null) {
						sqlBuilder.append(" and MaNganh=?");
						ct_params.add(maNganh);
					}
				}

				List<Integer> chiTieuList = jdbcTemplate.query(sqlBuilder.toString(),
						(rs, rowNum) -> rs.getInt("ChiTieu"), ct_params.toArray());
				int totalChiTieu = chiTieuList.stream().mapToInt(Integer::intValue).sum();
				sector_list[i].chi_tieu = totalChiTieu;
			}

			// ------------------Fill
			// data------------------------------------------------------------------------
			for (int i = 0; i < MAXYEAR; i++) {
				if (sector_list[i].year == 0)
					break;

				StringBuilder sqlBase = new StringBuilder(
						"select count(*) as counter from TBL_HOCVIEN where (IsDeleted=0 or IsDeleted is null) and NamNhapHoc=?");
				List<Object> hv_params = new ArrayList<>();
				hv_params.add(sector_list[i].year);

				if (hedaotao > 0) {
					sqlBase.append(" and HeDaoTao=?");
					hv_params.add(hedaotao);
				}
				if (trinhdo > 0) {
					sqlBase.append(" and TrinhDo=?");
					hv_params.add(trinhdo);
				}
				if (nganh > 0) {
					sqlBase.append(" and NganhHoc=?");
					hv_params.add(nganh);
				}

				// North
				StringBuilder sqlN = new StringBuilder(sqlBase).append(" and CoSoDaoTao=2");
				Integer nCount = jdbcTemplate.queryForObject(sqlN.toString(), Integer.class, hv_params.toArray());
				sector_list[i].north_counter = (nCount != null) ? nCount : 0;

				// South
				StringBuilder sqlS = new StringBuilder(sqlBase).append(" and CoSoDaoTao=3");
				Integer sCount = jdbcTemplate.queryForObject(sqlS.toString(), Integer.class, hv_params.toArray());
				sector_list[i].south_counter = (sCount != null) ? sCount : 0;
			}
			// ------------Dump-------------------------------------------------------------

			JSONArray jar_north = new JSONArray();
			JSONArray jar_south = new JSONArray();
			JSONArray jar_ptit = new JSONArray();
			JSONArray jar_chitieu = new JSONArray();
			JSONObject obj;
			for (int i = 0; i < MAXYEAR; i++) {
				if (sector_list[i].year == 0)
					break;

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].north_counter);
				jar_north.put(obj);

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].south_counter);
				jar_south.put(obj);

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].north_counter + sector_list[i].south_counter);
				jar_ptit.put(obj);

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].chi_tieu);
				jar_chitieu.put(obj);

			}
			// -------------------------------------------------------------------------
			jout.put("north_list", jar_north);
			jout.put("south_list", jar_south);
			jout.put("ptit_list", jar_ptit);
			jout.put("chitieu_list", jar_chitieu);
			jout.put("code", 200);

		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\": 801, \"description\": \"Error\"}";
		}
		System.out.println("RES(ThongKeQuyMoDaoTaoTheoNganh):" + jout.toString());

		return jout.toString();

	}

	// ---------------------------------------------------------------------------------------------
	@PostMapping("/thongketuyensinh")
	public String ThongKeTuyenSinh(@RequestBody String sReq) {

		System.out.println("----------ThongKeTuyenSinh:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			int nganh = 0;
			if (jsonobjReq.has("nganh"))
				nganh = jsonobjReq.getInt("nganh");
			int hedaotao = 0;
			if (jsonobjReq.has("hedaotao"))
				hedaotao = jsonobjReq.getInt("hedaotao");

			struct_one_year_sector_cnt_elm sector_list[] = new struct_one_year_sector_cnt_elm[MAXTSYEAR];

			for (int i = 0; i < MAXTSYEAR; i++) {
				sector_list[i] = new struct_one_year_sector_cnt_elm();
				sector_list[i].year = STARTYEAR + i;

				StringBuilder sqlBuilder = new StringBuilder(
						"select * from TBL_CHITIEUTUYENSINH where (IsDeleted=0 or IsDeleted is null) and Year=?");
				List<Object> ct_params = new ArrayList<>();
				ct_params.add(sector_list[i].year);

				if (hedaotao > 0) {
					sqlBuilder.append(" and HeDaoTao=?");
					ct_params.add(hedaotao);
				}

				if (nganh > 0) {
					String maNganh = jdbcTemplate.queryForObject("select MaNganh from DEF_NGANHHOC where ID=?",
							String.class, nganh);
					if (maNganh != null) {
						sqlBuilder.append(" and MaNganh=?");
						ct_params.add(maNganh);
					}
				}

				int n_count = 0;
				int s_count = 0;
				List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlBuilder.toString(),
						(Object[]) ct_params.toArray());
				for (Map<String, Object> row : rows) {
					int placeId = (int) row.get("PlaceID");
					int chiTieu = (int) row.get("ChiTieu");
					if (placeId == 2)
						n_count += chiTieu;
					else if (placeId == 3)
						s_count += chiTieu;
				}

				sector_list[i].north_counter = n_count;
				sector_list[i].south_counter = s_count;
			}

			Calendar cal = Calendar.getInstance();
			int this_year = cal.get(Calendar.YEAR);

			JSONArray jar_north = new JSONArray();
			JSONArray jar_south = new JSONArray();
			JSONArray jar_ptit = new JSONArray();
			JSONObject obj;

			int n_this_year_counter = 0, s_this_year_counter = 0;

			for (int i = 0; i < MAXTSYEAR; i++) {
				if (sector_list[i].year == 0)
					break;

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].north_counter);
				jar_north.put(obj);

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].south_counter);
				jar_south.put(obj);

				obj = new JSONObject();
				obj.put("name", sector_list[i].year);
				obj.put("y", sector_list[i].north_counter + sector_list[i].south_counter);
				jar_ptit.put(obj);

				if (sector_list[i].year == this_year) {
					n_this_year_counter = sector_list[i].north_counter;
					s_this_year_counter = sector_list[i].south_counter;
				}
			}

			jout.put("north_list", jar_north);
			jout.put("south_list", jar_south);
			jout.put("ptit_list", jar_ptit);

			// ---PIE chart------------------------------------
			Integer total_this_year = jdbcTemplate.queryForObject(
					"select sum(ChiTieu) from TBL_CHITIEUTUYENSINH where (IsDeleted=0 or IsDeleted is null) and Year=?",
					Integer.class, this_year);
			if (total_this_year == null)
				total_this_year = 0;

			obj = new JSONObject();
			obj.put("n_counter", n_this_year_counter);
			obj.put("s_counter", s_this_year_counter);
			obj.put("others_counter", total_this_year - n_this_year_counter - s_this_year_counter);
			obj.put("name", "Tỷ lệ chỉ tiêu năm: " + this_year);

			jout.put("this_year_obj", obj);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(ThongKeTuyenSinh):" + jout.toString());

		return jout.toString();
	}

	// ---------------------------------------------------------------------------------------------
	// ---------------------------------------------------------------------------------------------
	// From HanhDV
	// ---------------------------------------------------------------------------------------------
	/*
	 * @Path("/chi_tieu/importfromexcel")
	 * 
	 * @POST
	 * 
	 * @Produces("application/json;charset=UTF-8")
	 * public String importUser(String req) {
	 * return DaotaoExtend.importChiTieu(req);
	 * }
	 */
	/*
	 * @Path("/sinhvientinhthanh/importfromexcel")
	 * 
	 * @POST
	 * 
	 * @Produces("application/json;charset=UTF-8")
	 * public String ImportSinhVienTinhThanh(String req) {
	 * return DaotaoExtend.importSinhVienTinhThanh(req);
	 * }
	 */
	/*
	 * @Path("/quimodaotao/importfromexcel")
	 * 
	 * @POST
	 * 
	 * @Produces("application/json;charset=UTF-8")
	 * public String ImportQuiMoTS(String req) {
	 * return DaotaoExtend.ImportQuiMoTS(req);
	 * }
	 */
	/*
	 * @Path("/nguonluc/importfromexcel")
	 * 
	 * @POST
	 * 
	 * @Produces("application/json;charset=UTF-8")
	 * public String importNguonLucHv(String req) {
	 * return DaotaoExtend.importNguonLucHV(req);
	 * }
	 */
	// ------------------------------------------
	@PostMapping("/listorg")
	public String listOrg(@RequestBody String sReq) {

		System.out.println("----------listOrg:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			List<JSONObject> list = jdbcTemplate.query(
					"select * from TBL_ORG where (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("code", rs.getString("Code"));
							obj.put("name", rs.getString("Name"));
							obj.put("url", rs.getString("Url"));
							obj.put("logo", rs.getString("Logo"));
							obj.put("hotline", rs.getString("Hotline"));
							obj.put("email", rs.getString("Email"));
							obj.put("place_id", rs.getInt("PlaceID"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					});

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("org_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println(" RES(listOrg):" + jout.toString());

		return jout.toString();

	}

	// ------------------------------------------
	@PostMapping("/listplace")
	public String listPlace(@RequestBody String sReq) {

		System.out.println("----------listPlace:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);

			// verify user
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			List<JSONObject> list = jdbcTemplate.query(
					"select * from TBL_PLACE where (IsDeleted is null or IsDeleted='0')",
					(rs, rowNum) -> {
						JSONObject obj = new JSONObject();
						try {
							obj.put("id", rs.getInt("ID"));
							obj.put("code", rs.getString("Code"));
							obj.put("name", rs.getString("Name"));
							obj.put("url", rs.getString("Url"));
						} catch (JSONException | SQLException e) {
							e.printStackTrace();
						}
						return obj;
					});

			for (JSONObject obj : list) {
				jaout.put(obj);
			}

			jout.put("place_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println(" RES(listPlace):" + jout.toString());

		return jout.toString();

	}

	@PostMapping("/addchitieutuyensinh")
	public String addChiTieuTuyenSinh(@RequestBody String sReq) {

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");

			// verify user
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String manganh = jsonobjReq.getString("manganh");
			int trinhdo = jsonobjReq.getInt("trinhdo");
			int year = jsonobjReq.getInt("year");
			int place_id = jsonobjReq.getInt("place_id");
			int sochitieu = jsonobjReq.getInt("sochitieu");
			int hedaotao = jsonobjReq.getInt("hedaotao");

			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
			String createdTime = dateFormat.format(new Date());

			String sql = "insert into TBL_CHITIEUTUYENSINH (Year, TrinhDo, MaNganh, PlaceID, ChiTieu, HeDaoTao, CreatedTime, CreatedBy) values (?, ?, ?, ?, ?, ?, ?, ?)";
			jdbcTemplate.update(sql, year, trinhdo, manganh, place_id, sochitieu, hedaotao, createdTime, sst.UserID);

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(addChiTieuTuyenSinh):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/addchitieutuyensinhfile")
	public String addChiTieuTuyenSinhFile(@RequestBody String sReq) {

		System.out.println("----------addChiTieuTuyenSinhFile:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			JSONArray jarr = jsonobjReq.getJSONArray("data");

			// verify user
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
			String createdTime = dateFormat.format(new Date());

			for (int i = 0; i < jarr.length(); i++) {
				JSONObject obj = jarr.getJSONObject(i);
				System.out.println("ROW:---->" + i + obj.toString());

				int nam_ts = obj.getInt("nam_ts");
				String trinh_do = obj.getString("trinh_do");
				String csdt = obj.getString("co_so");
				String ma_nganh = obj.getString("ma_nganh");
				int chi_tieu = obj.has("chi_tieu") ? obj.getInt("chi_tieu") : 0;
				float diem_chuan = obj.has("diem_chuan") ? (float) obj.getDouble("diem_chuan") : 0f;
				String hdt = obj.getString("he_dao_tao");
				int hdt_value = fn_get_he_dao_tao(hdt);

				int trinhdo_value = fn_get_trinh_do(trinh_do);
				int place_id = fn_get_org_csdt(csdt);

				if (place_id > 0 && trinhdo_value > 0) {
					// Check if exists
					String checkSql = "select count(*) from TBL_CHITIEUTUYENSINH where Year=? and TrinhDo=? and MaNganh=? and PlaceID=? and HeDaoTao=? and IsDeleted=0";
					Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, nam_ts, trinhdo_value,
							ma_nganh, place_id, hdt_value);

					if (count == null || count == 0) {
						String insertSql = "insert into TBL_CHITIEUTUYENSINH (Year, TrinhDo, MaNganh, PlaceID, ChiTieu, DiemChuan, HeDaoTao, CreatedTime, CreatedBy) values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
						jdbcTemplate.update(insertSql, nam_ts, trinhdo_value, ma_nganh, place_id, chi_tieu, diem_chuan,
								hdt_value, createdTime, sst.UserID);
					}
				}
			}
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(addChiTieuTuyenSinhFile):" + jout.toString());
		return jout.toString();
	}

	// -------------------------------------------------------------------------
	@PostMapping("/addptitnganhscodefile")
	public String addPtitNganhSCodeFile(@RequestBody String sReq) {

		System.out.println("----------addPtitNganhSCodeFile:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			JSONArray jarr = jsonobjReq.getJSONArray("data");

			// verify user
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			for (int i = 0; i < jarr.length(); i++) {
				JSONObject obj = jarr.getJSONObject(i);
				String ma_nganh = obj.getString("ma_nganh");

				Integer count = jdbcTemplate.queryForObject("select count(*) from def_nganhhoc where MaNganh=?",
						Integer.class, ma_nganh);
				if (count != null && count > 0)
					continue;

				String ten_nganh = obj.getString("ten_nganh");
				String ptit_scode = obj.getString("ptit_code");
				String trinh_do = obj.getString("trinh_do");
				int value_trinhdo = fn_get_trinh_do(trinh_do);

				if (ma_nganh.length() > 0 && ptit_scode.length() > 0) {
					jdbcTemplate.update(
							"insert into def_nganhhoc (TrinhDo, MaNganh, Ten, PTITCode) values (?, ?, ?, ?)",
							value_trinhdo, ma_nganh, ten_nganh, ptit_scode);
				}
			}
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(addPtitNganhSCodeFile):" + jout.toString());
		return jout.toString();
	}

	// -----------------------------------------------------------------------------------------
	@PostMapping("/addgradfile")
	public String addGraduateFile(@RequestBody String sReq) {

		System.out.println("----------addGraduateFile:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			JSONArray jarr = jsonobjReq.getJSONArray("data");

			// verify user
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			for (int i = 1; i < jarr.length(); i++) {
				JSONObject obj = jarr.getJSONObject(i);
				System.out.println("----------------------------->" + i + "--" + obj.toString());
				// Logic is currently commented out in legacy code
			}

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(addGraduateFile):" + jout.toString());
		return jout.toString();
	}

	// ====================================================================
	@PostMapping("/delchitieutuyensinh")
	public String delChitieu(@RequestBody String sReq) {

		System.out.println("----------delChitieu:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			int chitieu_id = jsonobjReq.getInt("chitieu_id");

			// verify user
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			jdbcTemplate.update("update TBL_CHITIEUTUYENSINH set IsDeleted=1 where ID=?", chitieu_id);

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thieu tham so?" + e + "\"}";
		}
		System.out.println("RES(delChitieu):" + jout.toString());

		return jout.toString();
	}

	// ====================================================================
	@PostMapping("/updatechitieutuyensinh")
	public String updateChitieu(@RequestBody String sReq) {

		System.out.println("----------updateChitieu:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			int chitieu_id = jsonobjReq.getInt("chitieu_id");

			// verify user
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			int target = -1;
			if (jsonobjReq.has("target"))
				target = jsonobjReq.getInt("target");

			if (target >= 0) {
				jdbcTemplate.update("update TBL_CHITIEUTUYENSINH set ChiTieu=? where ID=?", target, chitieu_id);
			}

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thieu tham so?" + e + "\"}";
		}
		System.out.println("RES(updateChitieu):" + jout.toString());

		return jout.toString();
	}

	// ----------------------------------------------------------------------------------------------------
	private class _cnt_elm {
		int he_dao_tao_value = 0;
		String he_dao_tao_name = "";
		int target = 0;
		int archieved = 0;
	}

	private class struct_trinhdo_elm {
		int trinh_do_value = 0;
		String trinh_do_name = "";
		_cnt_elm cnt_list[] = new _cnt_elm[MAXHEDAOTAO];
	}

	@PostMapping("/kehoachdaotao")
	public String KeHoachDaoTao(@RequestBody String sReq) {
		System.out.println("----------KeHoachDaoTao:" + sReq);

		JSONObject jout = new JSONObject();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			int selected_year = jsonobjReq.getInt("year");

			// -------------------Init mem----------------------------
			struct_trinhdo_elm td_list[] = new struct_trinhdo_elm[MAXTRINHDO];
			for (int i = 0; i < MAXTRINHDO; i++) {
				td_list[i] = new struct_trinhdo_elm();
				for (int j = 0; j < MAXHEDAOTAO; j++) {
					td_list[i].cnt_list[j] = new _cnt_elm();
				}
			}

			// -------------------Init Data----------------------------
			List<Map<String, Object>> trinhDoRows = jdbcTemplate
					.queryForList("select * from DEF_TRINHDO where (IsDeleted is null or IsDeleted=0)");
			int td_idx = 0;
			for (Map<String, Object> tdRow : trinhDoRows) {
				if (td_idx >= MAXTRINHDO)
					break;
				int val = (int) tdRow.get("Value");
				td_list[td_idx].trinh_do_value = val;
				td_list[td_idx].trinh_do_name = (String) tdRow.get("Name");

				List<Map<String, Object>> hdtRows = jdbcTemplate
						.queryForList("select * from DEF_HEDAOTAO where (IsDeleted is null or IsDeleted=0)");
				int hdt_idx = 0;
				for (Map<String, Object> hdtRow : hdtRows) {
					if (hdt_idx >= MAXHEDAOTAO)
						break;
					td_list[td_idx].cnt_list[hdt_idx].he_dao_tao_value = (int) hdtRow.get("Value");
					td_list[td_idx].cnt_list[hdt_idx].he_dao_tao_name = (String) hdtRow.get("Name");
					hdt_idx++;
				}
				td_idx++;
			}

			// -------------------Get Target Data----------------------------
			for (int i = 0; i < MAXTRINHDO; i++) {
				if (td_list[i].trinh_do_value == 0)
					continue;
				List<Map<String, Object>> ctRows = jdbcTemplate.queryForList(
						"select * from TBL_CHITIEUTUYENSINH where Year=? and TrinhDo=?", selected_year,
						td_list[i].trinh_do_value);
				for (Map<String, Object> ctRow : ctRows) {
					int _chi_tieu = (int) ctRow.get("ChiTieu");
					int _hdt = (int) ctRow.get("HeDaoTao");

					for (int j = 0; j < MAXHEDAOTAO; j++) {
						if (td_list[i].cnt_list[j].he_dao_tao_value == _hdt) {
							td_list[i].cnt_list[j].target += _chi_tieu;
						}
					}
				}
			}

			// -------------------Get archieved Data----------------------------
			for (int i = 0; i < MAXTRINHDO; i++) {
				if (td_list[i].trinh_do_value == 0)
					continue;
				for (int j = 0; j < MAXHEDAOTAO; j++) {
					if (td_list[i].cnt_list[j].he_dao_tao_value == 0)
						continue;
					Integer count = jdbcTemplate.queryForObject(
							"select count(*) from TBL_HOCVIEN where NamNhapHoc=? and TrinhDo=? and HeDaoTao=?",
							Integer.class, selected_year, td_list[i].trinh_do_value,
							td_list[i].cnt_list[j].he_dao_tao_value);
					td_list[i].cnt_list[j].archieved = (count != null) ? count : 0;
				}
			}

			// -------------Dump--------------------------
			JSONArray jar = new JSONArray();
			JSONObject obj;

			// Define ordering logic as in original code
			int[] order = { 5, 4 }; // TS then ThS
			int current_id = 1;
			for (int target_td : order) {
				for (int i = 0; i < MAXTRINHDO; i++) {
					if (td_list[i].trinh_do_value == target_td) {
						obj = new JSONObject();
						obj.put("id", current_id++);
						obj.put("trinh_do", td_list[i].trinh_do_name);
						for (int j = 0; j < MAXHEDAOTAO; j++) {
							if (td_list[i].cnt_list[j].he_dao_tao_value == 1) {
								obj.put("he_dao_tao", td_list[i].cnt_list[j].he_dao_tao_name);
								obj.put("target", td_list[i].cnt_list[j].target);
								obj.put("archieved", td_list[i].cnt_list[j].archieved);
							}
						}
						jar.put(obj);
						break;
					}
				}
			}

			// DH section
			for (int i = 0; i < MAXTRINHDO; i++) {
				if (td_list[i].trinh_do_value == 3) {
					int[][] hdtOrder = { { 1, 3 }, { 5, 4 }, { 2, 5 }, { 4, 6 } }; // {hdt_value, id}
					for (int[] h : hdtOrder) {
						obj = new JSONObject();
						obj.put("id", h[1]);
						obj.put("trinh_do", td_list[i].trinh_do_name);
						for (int j = 0; j < MAXHEDAOTAO; j++) {
							if (td_list[i].cnt_list[j].he_dao_tao_value == h[0]) {
								obj.put("he_dao_tao", td_list[i].cnt_list[j].he_dao_tao_name);
								obj.put("target", td_list[i].cnt_list[j].target);
								obj.put("archieved", td_list[i].cnt_list[j].archieved);
							}
						}
						jar.put(obj);
					}
					break;
				}
			}
			jout.put("khdt_list", jar);

			// ----------------KE HOACH MO NGANH-----------------
			JSONArray jarMN = new JSONArray();
			List<Map<String, Object>> mnRows = jdbcTemplate.queryForList(
					"select * from TBL_MONGANH where NamDuKienThucHien=? and (IsDeleted=0 or IsDeleted is null)",
					selected_year);
			for (Map<String, Object> mnRow : mnRows) {
				obj = new JSONObject();
				obj.put("id", mnRow.get("id"));
				obj.put("ten", mnRow.get("Ten"));
				obj.put("nam_du_kien", mnRow.get("NamDuKienThucHien"));
				obj.put("nam_hoan_thanh", mnRow.get("NamHoanThanh"));
				obj.put("trang_thai", mnRow.get("TrangThai"));
				jarMN.put(obj);
			}
			jout.put("khmn_list", jarMN);

			// ----------------KE HOACH KIEM DINH-----------------
			JSONArray jarKD = new JSONArray();
			List<Map<String, Object>> kdRows = evidenceJdbcTemplate.queryForList(
					"select * from TBL_KIEMDINH where NamDuKienThucHien=? and (IsDeleted=0 or IsDeleted is null)",
					selected_year);
			for (Map<String, Object> kdRow : kdRows) {
				obj = new JSONObject();
				obj.put("id", kdRow.get("id"));
				obj.put("ten", kdRow.get("DoiTuong"));
				obj.put("nam_du_kien", kdRow.get("NamDuKienThucHien"));
				obj.put("nam_hoan_thanh", kdRow.get("NamHoanThanhBaoCaoTDGLan1"));
				obj.put("trang_thai", kdRow.get("KetQuaDanhGia"));
				jarKD.put(obj);
			}
			jout.put("khkd_list", jarKD);

			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		return jout.toString();
	}

}
