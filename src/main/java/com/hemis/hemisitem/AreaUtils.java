package com.hemis.hemisitem;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service("hemisAreaUtils")
public class AreaUtils {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public int getRootSite(int user_id, int user_type) {
        int area_id = 0, root_area_id = 0;
        try {
            String sql = (user_type == 1) ? "select areaid from dbo.tenantarea where tenantid=?" : "select areaid from dbo.userarea where userid=?";
            List<Integer> ids = jdbcTemplate.queryForList(sql, Integer.class, user_id);
            if (!ids.isEmpty()) area_id = ids.get(0);

            if (area_id > 0) {
                root_area_id = getRootSiteByArea(area_id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return root_area_id;
    }

    public int getRootSiteByArea(int area_id) {
        int level_from_root = 10, root_area_id = 0;
        try {
            while (level_from_root > 0 && area_id > 0) {
                Map<String, Object> row = jdbcTemplate.queryForMap("select LevelFromRoot, ParentID from dbo.area where areaid=?", area_id);
                level_from_root = (int) row.get("LevelFromRoot");
                root_area_id = area_id;
                area_id = (int) row.get("ParentID");
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return root_area_id;
    }

    public JSONArray getAreaList(int root_area_id, int area_type) {
        JSONArray jaout = new JSONArray();
        try {
            scanSubAreas(root_area_id, area_type, jaout, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jaout;
    }

    private void scanSubAreas(int parentId, int areaType, JSONArray jaout, int depth) {
        if (depth > 4) return;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select areaid, area, AreaTypeId, LevelFromRoot from dbo.area where ParentID=?", parentId);
        for (Map<String, Object> row : rows) {
            int areaId = (int) row.get("areaid");
            if ((int) row.get("AreaTypeId") == areaType) {
                JSONObject obj = new JSONObject();
                obj.put("area_id", areaId);
                obj.put("area_name", row.get("area"));
                jaout.put(obj);
            }
            scanSubAreas(areaId, areaType, jaout, depth + 1);
        }
    }
}
