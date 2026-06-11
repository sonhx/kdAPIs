package com.campusarea;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CampusAreaExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final DecimalFormat df = new DecimalFormat("0.00");

    public JSONArray getCampusAreaTree(int rootId) {
        JSONArray jar = new JSONArray();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from TBL_CAMPUS_AREA where ParentID=? and (IsDeleted is null or IsDeleted='0')", rootId);
        for (Map<String, Object> row : rows) {
            JSONObject c = new JSONObject();
            int areaId = (int) row.get("ID");
            c.put("id", areaId);
            c.put("label", row.get("Ten"));
            c.put("name", row.get("Ten"));
            c.put("ma_noi_bo", row.get("ma"));
            c.put("ma_quan_ly", row.get("MaQuanLy"));
            
            int loai = (int) row.get("Loai");
            c.put("loai", loai);
            c.put("ten_loai", getLoaiName(loai));
            
            c.put("object_id", row.get("ObjectID"));
            c.put("image_url", row.get("ImageUrl"));
            c.put("dai", row.get("Dai"));
            c.put("rong", row.get("Rong"));
            c.put("dien_tich", row.get("DienTich"));
            c.put("so_tang", row.get("SoTang"));
            c.put("cap_nha", row.get("CapNha"));
            c.put("nam_xay_dung", row.get("NamXayDung"));
            c.put("nam_cai_tao", row.get("NamCaiTao"));
            c.put("nam_khai_thac", row.get("ActivedYear"));
            c.put("url", row.get("Url"));
            c.put("logo", row.get("Logo"));
            c.put("qr_code", row.get("QrCode"));
            c.put("ip_camera_url", row.get("IpCameraUrl"));
            c.put("owner_type", row.get("OwnerType"));
            c.put("owner_type_name", getOwnerTypeName((int) row.get("OwnerType")));
            c.put("don_vi_quan_ly", getOrgName((int) row.get("DonViQuanLy")));
            c.put("don_vi_su_dung", getOrgName((int) row.get("DonViSuDung")));
            c.put("is_booking_enabled", row.get("IsBookEnabled"));

            c.put("children", getCampusAreaTree(areaId));
            jar.put(c);
        }
        return jar;
    }

    public void deleteCampusArea(int id) {
        List<Integer> children = jdbcTemplate.queryForList("select ID from TBL_CAMPUS_AREA where ParentID=?", Integer.class, id);
        for (Integer childId : children) {
            deleteCampusArea(childId);
        }
        jdbcTemplate.update("update TBL_CAMPUS_AREA set IsDeleted=1 where ID=?", id);
    }

    public void updateCampusArea(JSONObject jo) throws Exception {
        int id = jo.getInt("campus_area_id");
        if (jo.has("campus_area_name")) jdbcTemplate.update("Update tbl_campus_area set Ten=? where ID=?", jo.getString("campus_area_name"), id);
        if (jo.has("campus_area_code")) jdbcTemplate.update("Update tbl_campus_area set Code=? where ID=?", jo.getString("campus_area_code"), id);
        if (jo.has("campus_area_objectid")) jdbcTemplate.update("Update tbl_campus_area set ObjectID=? where ID=?", jo.getString("campus_area_objectid"), id);
        if (jo.has("qr_code")) {
            String qr = jo.getString("qr_code");
            Integer exists = jdbcTemplate.queryForObject("select count(*) from tbl_campus_area where QrCode=? and ID!=? and (IsDeleted=0 or IsDeleted is null)", Integer.class, qr, id);
            if (exists != null && exists > 0) throw new Exception("QR code exists");
            jdbcTemplate.update("Update tbl_campus_area set QrCode=? where ID=?", qr, id);
        }
        if (jo.has("campus_area_ownertype")) jdbcTemplate.update("Update tbl_campus_area set OwnerType=? where ID=?", jo.getInt("campus_area_ownertype"), id);
        if (jo.has("campus_area_booking_enable")) jdbcTemplate.update("Update tbl_campus_area set IsBookEnabled=? where ID=?", jo.getInt("campus_area_booking_enable"), id);
    }

    public JSONArray getAssetStatList(int rootId) {
        JSONArray jar = new JSONArray();
        List<Map<String, Object>> types = jdbcTemplate.queryForList("select * from DEF_CAMPUS_AREA_ASSET_TYPE where (IsDeleted=0 or IsDeleted is null)");
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        
        List<Integer> allAreaIds = getAllSubAreaIds(rootId);
        allAreaIds.add(rootId);
        
        for (Map<String, Object> type : types) {
            int typeVal = (int) type.get("Value");
            JSONObject obj = new JSONObject();
            obj.put("asset_type_id", typeVal);
            obj.put("asset_type_name", type.get("Name"));

            String sql = "select * from TBL_CAMPUS_AREA_ASSET where Loai=? and (IsDeleted is null or IsDeleted='0') and CampusAreaID IN (" + String.join(",", allAreaIds.stream().map(String::valueOf).toArray(String[]::new)) + ")";
            List<Map<String, Object>> assets = jdbcTemplate.queryForList(sql, typeVal);
            
            int total = 0, under5 = 0, upper5 = 0;
            Map<Integer, int[]> orgStats = new java.util.HashMap<>(); // Map<OrgID, [total, under5, upper5]>

            for (Map<String, Object> asset : assets) {
                total++;
                int usageYear = asset.get("NamSuDung") != null ? (int) asset.get("NamSuDung") : currentYear;
                boolean isUpper5 = (currentYear - usageYear) > 5;
                if (isUpper5) upper5++; else under5++;

                int orgId = asset.get("DonViSuDung") != null ? (int) asset.get("DonViSuDung") : 0;
                if (orgId > 0) {
                    int[] stats = orgStats.computeIfAbsent(orgId, k -> new int[3]);
                    stats[0]++;
                    if (isUpper5) stats[2]++; else stats[1]++;
                }
            }

            obj.put("counter", total);
            obj.put("under5_counter", under5);
            obj.put("upper5_counter", upper5);

            JSONArray orgList = new JSONArray();
            for (Map.Entry<Integer, int[]> entry : orgStats.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("org_id", entry.getKey());
                o.put("org_name", getOrgName(entry.getKey()));
                o.put("org_code", getOrgCode(entry.getKey()));
                o.put("counter", entry.getValue()[0]);
                o.put("under5_counter", entry.getValue()[1]);
                o.put("upper5_counter", entry.getValue()[2]);
                orgList.put(o);
            }
            obj.put("org_list", orgList);
            jar.put(obj);
        }
        return jar;
    }

    private List<Integer> getAllSubAreaIds(int parentId) {
        List<Integer> ids = new ArrayList<>();
        List<Integer> children = jdbcTemplate.queryForList("select ID from TBL_CAMPUS_AREA where ParentID=? and (IsDeleted=0 or IsDeleted is null)", Integer.class, parentId);
        for (Integer childId : children) {
            ids.add(childId);
            ids.addAll(getAllSubAreaIds(childId));
        }
        return ids;
    }

    private String getLoaiName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from DEF_CAMPUS_AREA_TYPE where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    private String getOwnerTypeName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from DEF_CAMPUS_AREA_OWNERTYPE where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    private String getOrgName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from TBL_ORG where ID=?", String.class, id); } catch (Exception e) { return ""; }
    }

    private String getOrgCode(int id) {
        try { return jdbcTemplate.queryForObject("select Code from TBL_ORG where ID=?", String.class, id); } catch (Exception e) { return "UKN"; }
    }

    public JSONArray getFeedbackList(int areaId, String start, String end) {
        JSONArray jar = new JSONArray();
        String sql = "select * from TBL_CAMPUS_AREA_FEEDBACK where (isDeleted is null or IsDeleted=0)";
        List<Object> params = new ArrayList<>();
        if (areaId > 0) { sql += " and CampusAreaID=?"; params.add(areaId); }
        if (start != null && !start.isEmpty()) { sql += " and CreatedTime >= ?"; params.add(start); }
        if (end != null && !end.isEmpty()) { sql += " and CreatedTime <= ?"; params.add(end); }
        sql += " order by CreatedTime desc";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("campus_area_id", row.get("CampusAreaID"));
            obj.put("campus_area_name", getAreaName((int) row.get("CampusAreaID")));
            obj.put("created_by_name", getUserName((int) row.get("CreatedBy")));
            obj.put("type_name", getFeedbackTypeName((int) row.get("FeedbackTypeValue")));
            obj.put("comment", row.get("Comment"));
            obj.put("image_url", row.get("ImageUrl"));
            obj.put("created_time", row.get("CreatedTime"));
            jar.put(obj);
        }
        return jar;
    }

    private String getAreaName(int id) {
        try { return jdbcTemplate.queryForObject("select Ten from TBL_CAMPUS_AREA where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    private String getUserName(int id) {
        try { return jdbcTemplate.queryForObject("select Fullname from TBL_USER where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    private String getFeedbackTypeName(int val) {
        try { return jdbcTemplate.queryForObject("select Name from DEF_CAMPUS_AREA_FEEDBACK_TYPE where Value=?", String.class, val); } catch (Exception e) { return "n/a"; }
    }
}
