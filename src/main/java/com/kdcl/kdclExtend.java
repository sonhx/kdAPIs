package com.kdcl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;

import java.text.DecimalFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class kdclExtend {
    public final String host = Config.host;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public JSONObject KTXStats(int nam) {
        JSONObject joKTX = new JSONObject();
        String sql = "select a.*, b.Fullname from TBL_KTX a "
                + " INNER JOIN TBL_USER b on b.ID = a.CreatedBy "
                + " where a.nam = ? and (a.IsDeleted is null or a.IsDeleted =0)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, nam);
        for (Map<String, Object> row : rows) {
            joKTX.put("nam", nam);
            if (row.get("tong_dien_tich") != null && (int) row.get("tong_dien_tich") > 0)
                joKTX.put("tong_dien_tich", row.get("tong_dien_tich"));
            if (row.get("soluong_sv") != null && (int) row.get("soluong_sv") > 0)
                joKTX.put("soluong_sv", row.get("soluong_sv"));
            if (row.get("sv_co_nhu_cau") != null && (int) row.get("sv_co_nhu_cau") > 0)
                joKTX.put("sv_co_nhu_cau", row.get("sv_co_nhu_cau"));
            if (row.get("sv_o_ktx") != null && (int) row.get("sv_o_ktx") > 0)
                joKTX.put("sv_o_ktx", row.get("sv_o_ktx"));
            joKTX.put("created_time", row.get("CreatedTime"));
            joKTX.put("created_by", row.get("CreatedBy"));
            joKTX.put("creator", row.get("Fullname"));
        }
        return joKTX;
    }

    public JSONArray getCSVCStats() {
        JSONArray jar = new JSONArray();
        final DecimalFormat df = new DecimalFormat("0.00");

        List<Map<String, Object>> types = jdbcTemplate.queryForList("select * from DEF_CAMPUS_AREA_TYPE where (IsDeleted=0 or IsDeleted is null)");
        for (Map<String, Object> type : types) {
            int typeId = (int) type.get("ID");
            String typeName = (String) type.get("Name");
            if (typeId < 3) continue;

            String sql = "select * from TBL_CAMPUS_AREA where (IsDeleted=0 or IsDeleted is null) and Loai=?";
            List<Map<String, Object>> areas = jdbcTemplate.queryForList(sql, typeId);
            
            int counter = 0;
            float sohuu = 0, lienket = 0, thue = 0, kxd = 0;
            for (Map<String, Object> area : areas) {
                counter++;
                int owner = area.get("OwnerType") != null ? (int) area.get("OwnerType") : 0;
                float size = area.get("DienTich") != null ? ((Number) area.get("DienTich")).floatValue() : 0;
                if (owner == 1) sohuu += size;
                else if (owner == 2) lienket += size;
                else if (owner == 3) thue += size;
                else kxd += size;
            }

            JSONObject obj = new JSONObject();
            obj.put("area_role_value", typeId);
            obj.put("area_role_name", typeName);
            obj.put("counter", counter);
            obj.put("size_sohuu", df.format(sohuu));
            obj.put("size_lienket", df.format(lienket));
            obj.put("size_thue", df.format(thue));
            obj.put("size_khongxacdinh", df.format(kxd));
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getKTXStats() {
        JSONArray jar = new JSONArray();
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String[] labels = {"Tổng diện tích phòng (m2)", "Số lượng sinh viên", "Số sinh viên có nhu cầu ở Kí túc xá", "Số sinh viên được ở Kí túc xá", "Tỷ số diện tích trên đầu Sinh viên ở trong KTX, m2/người"};
        
        for (String label : labels) {
            JSONObject obj = new JSONObject();
            obj.put("name", label);
            JSONArray data = new JSONArray();
            for (int i = 0; i < 5; i++) {
                int year = currentYear - 4 + i;
                if (label.startsWith("Tổng diện tích")) {
                    Float size = jdbcTemplate.queryForObject("select sum(DienTich) from TBL_CAMPUS_AREA where Loai=18 and ActivedYear<=? and (IsDeleted=0 or IsDeleted is null)", Float.class, year);
                    data.put(String.format("%.2f", size != null ? size : 0));
                } else {
                    data.put(0);
                }
            }
            obj.put("data", data);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getAssetStats() {
        JSONArray jar = new JSONArray();
        List<Map<String, Object>> types = jdbcTemplate.queryForList("select * from DEF_CAMPUS_AREA_ASSET_TYPE where (IsDeleted=0 or IsDeleted is null)");
        for (Map<String, Object> type : types) {
            int val = (int) type.get("Value");
            Integer count = jdbcTemplate.queryForObject("select count(*) from TBL_CAMPUS_AREA_ASSET where (IsDeleted=0 or IsDeleted is null) and Loai=?", Integer.class, val);
            JSONObject obj = new JSONObject();
            obj.put("asset_type_value", val);
            obj.put("asset_type_name", type.get("Name"));
            obj.put("counter", count != null ? count : 0);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getNguoiHocStats() {
        JSONArray jar = new JSONArray();
        DecimalFormat df = new DecimalFormat("0.00");
        List<Map<String, Object>> levels = jdbcTemplate.queryForList("select * from DEF_TRINHDO where (IsDeleted=0 or IsDeleted is null) and Value between 3 and 5");
        int startYear = 2018;
        for (Map<String, Object> level : levels) {
            int val = (int) level.get("Value");
            JSONObject obj = new JSONObject();
            obj.put("trinhdo_name", level.get("Name"));
            obj.put("trinhdo_value", val);
            JSONArray yearData = new JSONArray();
            for (int j = 0; j < 5; j++) {
                int year = startYear + j;
                JSONObject yObj = new JSONObject();
                yObj.put("year", year);
                Integer nhapHoc = jdbcTemplate.queryForObject("select count(*) from TBL_HOCVIEN where TrinhDo=? and NamNhapHoc=?", Integer.class, val, year);
                Float diemTB = jdbcTemplate.queryForObject("select avg(DiemChuan) from TBL_CHITIEUTUYENSINH where Year=? and DiemChuan>0 and TrinhDo=?", Float.class, year, val);
                Integer svqt = jdbcTemplate.queryForObject("select count(*) from TBL_HOCVIEN where TrinhDo=? and NamNhapHoc=? and MADBQUOCTICH>0", Integer.class, val, year);
                
                yObj.put("sonhaphoc", nhapHoc != null ? nhapHoc : 0);
                yObj.put("diemtuyendauvao", df.format(diemTB != null ? diemTB : 0));
                yObj.put("soluongsvquoctenhaphoc", svqt != null ? svqt : 0);
                yObj.put("sothisinhdutuyen", 0);
                yObj.put("sotrungtuyen", 0);
                yObj.put("tylecanhtranh", 0);
                yObj.put("diemtbnguoihocduoctuyen", 0);
                yearData.put(yObj);
            }
            obj.put("year_counter", yearData);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getAccreditationStats() {
        JSONArray jar = new JSONArray();
        List<Map<String, Object>> accs = jdbcTemplate.queryForList("select * from TBL_KIEMDINH where (IsDeleted=0 or IsDeleted is null)");
        for (Map<String, Object> acc : accs) {
            JSONObject obj = new JSONObject();
            int id = (int) acc.get("ID");
            obj.put("id", id);
            obj.put("doi_tuong", acc.get("DoiTuong"));
            obj.put("bo_tieu_chuan", acc.get("BoTieuChuan"));
            obj.put("nam_hoan_thanh", acc.get("NamHoanThanhBaoCaoTDGLan1"));
            obj.put("nam_cap_nhat", acc.get("NamCapNhatBaoCaoTDG"));
            obj.put("ten_to_chuc_danh_gia", acc.get("TenToChucDanhGia"));
            obj.put("thoi_gian_danh_gia_ngoai", acc.get("ThoiGianDanhGiaNgoai"));
            obj.put("ket_qua_danh_gia", acc.get("KetQuaDanhGia"));
            obj.put("ngay_cap_chung_nhan", acc.get("NgayCapGiayChungNhan"));
            obj.put("gia_tri_den", acc.get("GiaTriDen"));
            obj.put("ma_so_giay_chung_nhan", acc.get("MaSoGiayChungNhan"));
            obj.put("url_giay_chung_nhan", acc.get("UrlGiayChungNhan"));

            JSONArray docs = new JSONArray();
            List<Map<String, Object>> docList = jdbcTemplate.queryForList("select * from TBL_KIEMDINH_DOC where KiemdinhID=? and (IsDeleted=0 or IsDeleted is null)", id);
            for (Map<String, Object> doc : docList) {
                docs.put(new JSONObject().put("id", doc.get("ID")).put("name", doc.get("Name")).put("file_url", doc.get("FileUrl")));
            }
            obj.put("doc_list", docs);
            jar.put(obj);
        }
        return jar;
    }

    public JSONObject getCBCNVStats() {
        JSONObject res = new JSONObject();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new Date());
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_CBCNV where (PlaceCode is not null) and (NgayVaoHocVien < ? and (LastStateChangedTime Is null or LastStateChangedTime > ?))", today, today);
        
        int total = rows.size();
        int counter_gv = 0, counter_gv_ts = 0, counter_ncv = 0, counter_ncv_ts = 0;
        int counter_ql = 0, counter_nv = 0;
        
        List<Map<String, Object>> ngachList = jdbcTemplate.queryForList("Select * from DEF_NGACHLUONG where (IsDeleted=0 or IsDeleted is NULL)");
        Map<Integer, String> ngachMap = new java.util.HashMap<>();
        for (Map<String, Object> n : ngachList) ngachMap.put((int) n.get("Value"), (String) n.get("Name"));

        for (Map<String, Object> row : rows) {
            int ngachId = row.get("Ngach") != null ? (int) row.get("Ngach") : -1;
            if (ngachId == 0) counter_ql++;
            else if (ngachId == 1) counter_nv++;

            int ngachLuong = row.get("NgachLuong") != null ? (int) row.get("NgachLuong") : -1;
            String ngachName = ngachMap.get(ngachLuong);
            int trinhdo = row.get("Trinhdo") != null ? (int) row.get("Trinhdo") : -1;

            if (ngachName != null) {
                if (ngachName.equals("GVCC") || ngachName.equals("GVC") || ngachName.equals("GV")) {
                    counter_gv++;
                    if (trinhdo == 5) counter_gv_ts++;
                } else if (ngachName.equals("KS") || ngachName.equals("NCVC") || ngachName.equals("NCV")) {
                    counter_ncv++;
                    if (trinhdo == 5) counter_ncv_ts++;
                }
            }
        }

        JSONArray data1 = new JSONArray();
        data1.put(new JSONObject().put("name", "Giảng viên").put("y", counter_gv).put("sum", 0).put("ts", counter_gv_ts));
        data1.put(new JSONObject().put("name", "Nghiên cứu viên").put("y", counter_ncv).put("sum", 0).put("ts", counter_ncv_ts));
        data1.put(new JSONObject().put("name", "Tổng").put("y", counter_gv + counter_ncv).put("sum", 1).put("ts", counter_gv_ts + counter_ncv_ts));
        
        JSONArray data2 = new JSONArray();
        data2.put(new JSONObject().put("name", "Quản lý").put("y", counter_ql));
        data2.put(new JSONObject().put("name", "Nhân viên").put("y", counter_nv));

        res.put("total", total);
        res.put("data1", data1);
        res.put("data2", data2);
        
        JSONArray genderList = new JSONArray();
        int m0 = 0, f0 = 0, m1 = 0, f1 = 0, m2 = 0, f2 = 0;
        for (Map<String, Object> row : rows) {
            int gender = row.get("GioiTinh") != null ? (int) row.get("GioiTinh") : 0;
            int hd = row.get("LoaiHD") != null ? (int) row.get("LoaiHD") : 0;
            if (gender == 0) {
                if (hd == 0 || hd == 7) m0++;
                else if (hd == 2 || hd == 5) m1++;
                else m2++;
            } else {
                if (hd == 0 || hd == 7) f0++;
                else if (hd == 2 || hd == 5) f1++;
                else f2++;
            }
        }
        res.put("gender_list", buildGenderStats(m0, f0, m1, f1, m2, f2));
        return res;
    }

    private JSONArray buildGenderStats(int m0, int f0, int m1, int f1, int m2, int f2) {
        JSONArray jar = new JSONArray();
        jar.put(new JSONObject().put("tt", "I").put("sum", 0).put("type", "Cán bộ cơ hữu, trong đó:").put("data", new JSONArray().put(m0+m1).put(f0+f1).put(m0+m1+f0+f1)));
        jar.put(new JSONObject().put("tt", "I.1").put("sum", 0).put("type", "Cán bộ được tuyển dụng...").put("data", new JSONArray().put(m0).put(f0).put(m0+f0)));
        jar.put(new JSONObject().put("tt", "I.2").put("sum", 0).put("type", "Cán bộ hợp đồng dài hạn").put("data", new JSONArray().put(m1).put(f1).put(m1+f1)));
        jar.put(new JSONObject().put("tt", "II").put("sum", 0).put("type", "Các cán bộ khác").put("data", new JSONArray().put(m2).put(f2).put(m2+f2)));
        jar.put(new JSONObject().put("tt", "").put("sum", 1).put("type", "Tổng cộng").put("data", new JSONArray().put(m0+m1+m2).put(f0+f1+f2).put(m0+m1+m2+f0+f1+f2)));
        return jar;
    }

    public JSONObject getGiangVienStats() {
        JSONObject res = new JSONObject();
        List<Map<String, Object>> levels = jdbcTemplate.queryForList("Select * from DEF_TRINHDO where (IsDeleted=0 or IsDeleted is NULL)");
        List<Map<String, Object>> hochams = jdbcTemplate.queryForList("Select * from DEF_HOCHAM where (IsDeleted=0 or IsDeleted is NULL)");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String today = sdf.format(new Date());
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);

        String sql = "Select * from TBL_CBCNV where (PlaceCode is not null) and (NgayVaoHocVien < ? and (LastStateChangedTime Is null or LastStateChangedTime > ?))";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, today, today);
        
        JSONArray data = new JSONArray();
        int total = 0, mTotal = 0, fTotal = 0, hdlvTotal = 0, hdldTotal = 0;
        int a30 = 0, a40 = 0, a50 = 0, a60 = 0, a60p = 0;

        for (Map<String, Object> level : levels) {
            int lVal = (int) level.get("Value");
            for (Map<String, Object> hh : (lVal == 5 || lVal == 7 ? hochams : java.util.Collections.singletonList(new java.util.HashMap<String, Object>() {{ put("Value", 0); put("Name", "no"); }}))) {
                int hhVal = (int) hh.get("Value");
                int count = 0, m = 0, f = 0, hdlv = 0, hdld = 0;
                int ta30 = 0, ta40 = 0, ta50 = 0, ta60 = 0, ta60p = 0;

                for (Map<String, Object> row : rows) {
                    if ((int)row.get("Trinhdo") == lVal && (int)row.get("HocHam") == hhVal) {
                        count++;
                        int gender = (int) row.get("GioiTinh");
                        if (gender == 0) m++; else f++;
                        
                        int age = currentYear - (int) row.get("NamSinh");
                        if (age < 30) ta30++;
                        else if (age < 41) ta40++;
                        else if (age < 51) ta50++;
                        else if (age < 61) ta60++;
                        else ta60p++;

                        int lhd = (int) row.get("LoaiHD");
                        int nl = (int) row.get("NgachLuong");
                        if (lhd == 0 && (nl == 1 || nl == 2 || nl == 5)) hdlv++;
                        if (lhd == 2 && (nl == 1 || nl == 2 || nl == 5)) hdld++;
                    }
                }

                if (count > 0) {
                    JSONObject jo = new JSONObject();
                    jo.put("name", hhVal == 0 ? level.get("Name") : (hh.get("Name") + "-" + level.get("Name")));
                    jo.put("y", count); jo.put("male", m); jo.put("female", f);
                    jo.put("gv_hdlv", hdlv); jo.put("gv_hdld", hdld);
                    jo.put("age_under_30", ta30); jo.put("age_30_to_40", ta40); jo.put("age_41_to_50", ta50);
                    jo.put("age_51_to_60", ta60); jo.put("age_over_60", ta60p);
                    data.put(jo);
                    
                    total += count; mTotal += m; fTotal += f; hdlvTotal += hdlv; hdldTotal += hdld;
                    a30 += ta30; a40 += ta40; a50 += ta50; a60 += ta60; a60p += ta60p;
                }
            }
        }
        res.put("data", data);
        res.put("sum_obj", new JSONObject().put("y", total).put("male", mTotal).put("female", fTotal)
            .put("gv_hdlv", hdlvTotal).put("gv_hdld", hdldTotal).put("age_under_30", a30)
            .put("age_30_to_40", a40).put("age_41_to_50", a50).put("age_51_to_60", a60).put("age_over_60", a60p));
        return res;
    }

    public JSONArray getDeTaiStats() {
        JSONArray jar = new JSONArray();
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String[] capLabels = {"Đề tài cấp Nhà nước", "Đề tài cấp Bộ hoặc tương đương", "Đề tài cấp Học viện"};
        for (int i = 1; i <= 3; i++) {
            JSONObject obj = new JSONObject();
            obj.put("name", capLabels[i-1]);
            JSONArray data = new JSONArray();
            for (int y = 0; y < 5; y++) {
                int year = currentYear - 4 + y;
                Integer count = jdbcTemplate.queryForObject("select count(*) from TBL_DETAI where NamBatDau=? and CapQuanLyKH=?", Integer.class, year, i);
                data.put(count != null ? count : 0);
            }
            obj.put("data", data);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getCGCNStats() {
        JSONArray jar = new JSONArray();
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        
        JSONObject obj = new JSONObject();
        obj.put("name", "Doanh thu CGCN (Tr VNĐ)");
        JSONArray data = new JSONArray();
        float sum = 0;
        for (int i = 0; i < 5; i++) {
            int year = currentYear - 4 + i;
            Float val = jdbcTemplate.queryForObject("select sum(Value) from TBL_CGCN where Year=? and (IsDeleted=0 or IsDeleted is null)", Float.class, year);
            float v = val != null ? val : 0;
            data.put(String.format("%.2f", v));
            sum += v;
        }
        data.put(String.format("%.2f", sum));
        obj.put("data", data);
        jar.put(obj);

        jar.put(new JSONObject().put("name", "Tỷ lệ doanh thu...").put("data", new JSONArray().put(0).put(0).put(0).put(0).put(0).put(0)));
        jar.put(new JSONObject().put("name", "Doanh thu CGCN trên cán bộ...").put("data", new JSONArray().put(0).put(0).put(0).put(0).put(0).put(0)));
        return jar;
    }

    public JSONArray getBaiBaoStats() {
        JSONArray jar = new JSONArray();
        int cy = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        List<Map<String, Object>> types = jdbcTemplate.queryForList("Select * from DEF_LOAITAPCHI where (IsDeleted=0 or IsDeleted is NULL)");
        for (Map<String, Object> t : types) {
            int id = (int) t.get("ID");
            JSONObject obj = new JSONObject();
            obj.put("name", t.get("Name"));
            JSONArray data = new JSONArray();
            for (int i = 0; i < 5; i++) {
                int year = cy - 4 + i;
                Integer count = jdbcTemplate.queryForObject("Select count(*) from TBL_BAIBAO where (IsDeleted=0 or IsDeleted is NULL) and LoaiTapChi=? and NamXuatBan=?", Integer.class, id, year);
                data.put(count != null ? count : 0);
            }
            obj.put("data", data);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getKinhPhiStats() {
        JSONArray jar = new JSONArray();
        int cy = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        int sy = cy - 4;
        String[] codes = {"KPI01", "KPI02", "KPI05"};
        String[] names = {"Tổng kinh phí...", "Tổng thu học phí...", "Tổng thu từ NCKH..."};
        int[] indices = {41, 42, 44};
        
        for (int i = 0; i < codes.length; i++) {
            JSONObject obj = new JSONObject();
            obj.put("name", names[i]);
            obj.put("index", indices[i]);
            JSONArray data = new JSONArray();
            Map<String, Object> kpi = jdbcTemplate.queryForMap("Select * from DEF_KPI_TC where Code=?", codes[i]);
            for (int y = 0; y < 5; y++) {
                Object val = kpi.get(String.valueOf(sy + y));
                data.put(String.format("%.2f", val instanceof Number ? ((Number) val).floatValue() : 0));
            }
            obj.put("data", data);
            jar.put(obj);
        }
        return jar;
    }

    public JSONArray getXuatBanSachStats() {
        JSONArray jar = new JSONArray();
        int sy = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 4;
        List<Map<String, Object>> types = jdbcTemplate.queryForList("select * from DEF_BOOKTYPE where (IsDeleted is null or IsDeleted=0)");
        for (Map<String, Object> t : types) {
            int id = (int) t.get("ID");
            JSONObject obj = new JSONObject();
            obj.put("name", t.get("Name"));
            JSONArray data = new JSONArray();
            for (int y = 0; y < 5; y++) {
                Integer count = jdbcTemplate.queryForObject("select count(*) from TBL_BOOK where Type=? and PublicYear=?", Integer.class, id, sy + y);
                data.put(count != null ? count : 0);
            }
            obj.put("data", data);
            jar.put(obj);
        }
        return jar;
    }

    public JSONObject getVietSachStats() {
        JSONObject res = new JSONObject();
        List<Map<String, Object>> types = jdbcTemplate.queryForList("SELECT * from DEF_BOOKTYPE");
        List<String> authors = jdbcTemplate.queryForList("SELECT DISTINCT Name from TBL_BOOK_AUTHOR", String.class);
        
        JSONArray typeList = new JSONArray();
        for (Map<String, Object> t : types) typeList.put(t.get("Name"));
        res.put("book_typet_list", typeList);

        String[] labels = {"Từ 1 đến 3 cuốn sách", "Từ 4 đến 6 cuốn sách", "Trên 6 cuốn sách"};
        int[] mins = {1, 4, 7}, maxs = {3, 6, 100};
        JSONArray jar = new JSONArray();
        
        for (int i = 0; i < labels.length; i++) {
            JSONObject obj = new JSONObject();
            obj.put("name", labels[i]);
            JSONArray data = new JSONArray();
            for (Map<String, Object> t : types) {
                int tid = (int) t.get("ID");
                int totalAuthors = 0;
                for (String author : authors) {
                    Integer count = jdbcTemplate.queryForObject("select count(*) from TBL_BOOK_AUTHOR where Name=? and BookTypeID=?", Integer.class, author, tid);
                    if (count != null && count >= mins[i] && count <= maxs[i]) totalAuthors++;
                }
                data.put(new JSONObject().put("name", t.get("Name")).put("counter", totalAuthors));
            }
            obj.put("data", data);
            jar.put(obj);
        }
        res.put("book_author_stat_list", jar);
        return res;
    }

    public JSONArray getLibraryBookStats() {
        JSONArray jar = new JSONArray();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from TBL_BOOK_STORE_STAT where (IsDeleted=0 or IsDeleted Is NULL)");
        for (Map<String, Object> row : rows) {
            jar.put(new JSONObject().put("id", row.get("ID")).put("nhom_nganh", row.get("NhomNganh")).put("dau_sach", row.get("DauSach")).put("ban_sach", row.get("BanSach")));
        }
        return jar;
    }

    public JSONArray getMainAssetList(int asset_type_id) {
        JSONArray jar = new JSONArray();
        DecimalFormat df = new DecimalFormat("0.00");
        String hierarchySql = "WITH Hierarchy(ChildId, ChildName) AS (SELECT Id, Ten FROM TBL_CAMPUS_AREA WHERE id=1 UNION ALL SELECT n.Id, n.Ten FROM TBL_CAMPUS_AREA n INNER JOIN Hierarchy h ON n.ParentId = h.ChildId where (n.isDeleted=0 or n.isDeleted is null)) select * from Hierarchy";
        List<Map<String, Object>> areas = jdbcTemplate.queryForList(hierarchySql);
        
        for (Map<String, Object> area : areas) {
            int areaId = (int) area.get("ChildId");
            String sql = "select * from TBL_CAMPUS_AREA_ASSET where CampusAreaID=? and (IsDeleted is null or IsDeleted='0')";
            if (asset_type_id != 0) sql += " and Loai=" + asset_type_id;
            
            List<Map<String, Object>> assets = jdbcTemplate.queryForList(sql, areaId);
            for (Map<String, Object> asset : assets) {
                JSONObject obj = new JSONObject();
                int aid = (int) asset.get("ID");
                obj.put("id", aid);
                obj.put("campus_area_name", getFullCampusAreaName(areaId));
                obj.put("so_luong", asset.get("SoLuong"));
                obj.put("ten", asset.get("Ten"));
                obj.put("doi_tuong", "Giảng viên/Sinh viên");
                
                Float size = jdbcTemplate.queryForObject("select DienTich from TBL_CAMPUS_AREA where ID=?", Float.class, areaId);
                obj.put("dien_tich", df.format(size != null ? size : 0));
                
                int owner = asset.get("OwnerType") != null ? (int) asset.get("OwnerType") : 0;
                obj.put("so_huu", owner == 1 ? "x" : "");
                obj.put("lien_ket", owner == 2 ? "x" : "");
                obj.put("thue", owner == 3 ? "x" : "");
                jar.put(obj);
            }
        }
        return jar;
    }

    private String getFullCampusAreaName(int id) {
        List<String> names = new java.util.ArrayList<>();
        Integer currentId = id;
        while (currentId != null && currentId != 0) {
            Map<String, Object> area = jdbcTemplate.queryForMap("select ID, Ten, ParentId from TBL_CAMPUS_AREA where ID=?", currentId);
            names.add((String) area.get("Ten"));
            currentId = (Integer) area.get("ParentId");
        }
        java.util.Collections.reverse(names);
        return String.join("-", names);
    }
}
