package com.tckt;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/taichinh")
public class TCKTService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private TCKTExtend tcktExtend;

    @PostMapping("/importfromexcel")
    public String importTaiChinh(@RequestBody String req) {
        return tcktExtend.importTaiChinh(req);
    }

    private final int MAXYEAR = 9;
    private final int STARTYEAR = 2016;

    private static class struct_one_year_cnt_elm {
        int year = 0;
        float chi_tieu_tong_thu = 0;
        float tong_thu = 0;
        float thu_hoc_phi = 0;
        float thu_hdsn = 0;
        float chi_dao_tao = 0;
        float chi_phat_trien_doi_ngu = 0;
        float chi_hdsn = 0;
        float chi_ket_noi_tu_van = 0;
        float loi_nhuan = 0;
    }

    @PostMapping("/listtaichinh")
    public String listTaiChinhTable(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        JSONArray jaout = new JSONArray();

        try {
            struct_one_year_cnt_elm[] year_list = new struct_one_year_cnt_elm[MAXYEAR];
            for (int i = 0; i < MAXYEAR; i++) {
                year_list[i] = new struct_one_year_cnt_elm();
                year_list[i].year = STARTYEAR + i;
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from TBL_TAICHINH where (IsDeleted is null or IsDeleted=0)");
            for (Map<String, Object> row : rows) {
                int year = (int) row.get("Nam");
                for (int i = 0; i < MAXYEAR; i++) {
                    if (year_list[i].year == year) {
                        year_list[i].chi_tieu_tong_thu = getFloat(row, "ChiTieuTongThu");
                        year_list[i].tong_thu = getFloat(row, "TongThu");
                        year_list[i].thu_hoc_phi = getFloat(row, "ThuTuHocPhi");
                        year_list[i].thu_hdsn = getFloat(row, "ThuTuHoatDongSuNghiep");
                        year_list[i].chi_dao_tao = getFloat(row, "ChiHoatDongDaoTao");
                        year_list[i].chi_phat_trien_doi_ngu = getFloat(row, "ChiPhatTrienDoiNgu");
                        year_list[i].chi_hdsn = getFloat(row, "ChiHoatDongSuNghiep");
                        year_list[i].chi_ket_noi_tu_van = getFloat(row, "ChiHoatDongKetNoiTuVan");
                        break;
                    }
                }
            }

            for (int i = 0; i < MAXYEAR; i++) {
                JSONObject obj = new JSONObject();
                obj.put("id", i);
                obj.put("year", year_list[i].year);
                obj.put("chi_tieu_tong_thu", year_list[i].chi_tieu_tong_thu);
                obj.put("tong_thu", year_list[i].tong_thu);
                obj.put("thu_hoc_phi", year_list[i].thu_hoc_phi);
                obj.put("thu_hdsn", year_list[i].thu_hdsn);
                obj.put("chi_dao_tao", year_list[i].chi_dao_tao);
                obj.put("chi_phat_trien_doi_ngu", year_list[i].chi_phat_trien_doi_ngu);
                obj.put("chi_hdsn", year_list[i].chi_hdsn);
                obj.put("chi_ket_noi_tu_van", year_list[i].chi_ket_noi_tu_van);
                jaout.put(obj);
            }

            jout.put("taichinh_list", jaout);
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    @PostMapping("/listthongketaichinh")
    public String listThongKeTaiChinh(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jsonobjReq.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            struct_one_year_cnt_elm[] year_list = new struct_one_year_cnt_elm[MAXYEAR];
            for (int i = 0; i < MAXYEAR; i++) {
                year_list[i] = new struct_one_year_cnt_elm();
                year_list[i].year = STARTYEAR + i;
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from TBL_TAICHINH where (IsDeleted is null or IsDeleted=0)");
            for (Map<String, Object> row : rows) {
                int year = (int) row.get("Nam");
                for (int i = 0; i < MAXYEAR; i++) {
                    if (year_list[i].year == year) {
                        year_list[i].chi_tieu_tong_thu = getFloat(row, "ChiTieuTongThu");
                        year_list[i].tong_thu = getFloat(row, "TongThu");
                        year_list[i].thu_hoc_phi = getFloat(row, "ThuTuHocPhi");
                        year_list[i].thu_hdsn = getFloat(row, "ThuTuHoatDongSuNghiep");
                        year_list[i].chi_dao_tao = getFloat(row, "ChiHoatDongDaoTao");
                        year_list[i].chi_phat_trien_doi_ngu = getFloat(row, "ChiPhatTrienDoiNgu");
                        year_list[i].chi_hdsn = getFloat(row, "ChiHoatDongSuNghiep");
                        year_list[i].chi_ket_noi_tu_van = getFloat(row, "ChiHoatDongKetNoiTuVan");
                        year_list[i].loi_nhuan = getFloat(row, "LoiNhuan");
                        break;
                    }
                }
            }

            JSONArray jar_chi_tieu_tong_thu = new JSONArray();
            JSONArray jar_tong_thu = new JSONArray();
            JSONArray jar_thu_hoc_phi = new JSONArray();
            JSONArray jar_thu_hdsn = new JSONArray();
            JSONArray jar_chi_dao_tao = new JSONArray();
            JSONArray jar_chi_luong = new JSONArray();
            JSONArray jar_chi_hdsn = new JSONArray();
            JSONArray jar_chi_ket_noi_tu_van = new JSONArray();
            JSONArray jar_loi_nhuan = new JSONArray();

            for (int i = 0; i < MAXYEAR; i++) {
                jar_chi_tieu_tong_thu.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].chi_tieu_tong_thu));
                jar_tong_thu.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].tong_thu));
                jar_thu_hoc_phi.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].thu_hoc_phi));
                jar_thu_hdsn.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].thu_hdsn));
                jar_chi_dao_tao.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].chi_dao_tao));
                jar_chi_luong.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].chi_phat_trien_doi_ngu));
                jar_chi_hdsn.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].chi_hdsn));
                jar_chi_ket_noi_tu_van.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].chi_ket_noi_tu_van));
                jar_loi_nhuan.put(new JSONObject().put("name", year_list[i].year).put("y", year_list[i].loi_nhuan));
            }

            jout.put("chi_tieu_tong_thu_list", jar_chi_tieu_tong_thu);
            jout.put("tong_thu_list", jar_tong_thu);
            jout.put("thu_hoc_phi_list", jar_thu_hoc_phi);
            jout.put("thu_hdsn_list", jar_thu_hdsn);
            jout.put("chi_dao_tao_list", jar_chi_dao_tao);
            jout.put("chi_hdsn_list", jar_chi_hdsn);
            jout.put("chi_luong_list", jar_chi_luong);
            jout.put("chi_ket_noi_tu_van_list", jar_chi_ket_noi_tu_van);
            jout.put("loi_nhuan", jar_loi_nhuan);
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    private float getFloat(Map<String, Object> row, String column) {
        Object val = row.get(column);
        if (val instanceof Number) return ((Number) val).floatValue();
        return 0.0f;
    }
}
