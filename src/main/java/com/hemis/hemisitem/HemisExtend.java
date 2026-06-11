package com.hemis.hemisitem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.json.JSONObject;

import com.config.Config;
import com.hanhdv.jdbc.ConnectionUtils;
import org.springframework.stereotype.Service;

@Service
public class HemisExtend {
	public static String DB = "jdbc/KD";
	public static final String host = Config.host;
	
	public static int assignFillingForm(int item_id, int org_id, int created_by){
		String sql = "MERGE INTO TBL_HEMIS_ASSIGNMENT a "
					+ " USING (VALUES (?, ?, GETDATE(), ?)) AS source (ITEM_ID, ORG_ID, CreatedTime, CreatedBy) "
					+ " ON a.ITEM_ID = source.ITEM_ID "
					+ " WHEN MATCHED THEN "
  						+ " UPDATE SET ORG_ID = source.ORG_ID, "
  									+ " CreatedTime=source.CreatedTime,"
  									+ " CreatedBy = source.CreatedBy  "
					+ " WHEN NOT MATCHED THEN "
  						+ " INSERT (ITEM_ID, ORG_ID, CreatedTime, CreatedBy) "
  						+ " VALUES (source.ITEM_ID, source.ORG_ID,source.CreatedTime, source.CreatedBy);";
		try (Connection conn = ConnectionUtils.getMyConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, item_id);
			ps.setInt(2, org_id);
			ps.setInt(3, created_by);
			return ps.executeUpdate();
		} catch (Exception e) { e.printStackTrace(); }
		return 0;
	}
	
	public static JSONObject KTXStats(int nam){
		JSONObject joKTX =  new JSONObject();
		String sql = "select a.*, b.Fullname from TBL_KTX a "
				+ " INNER JOIN TBL_USER b on b.ID = a.CreatedBy "
				+ " where a.nam = ?"
				+ " and (a.IsDeleted is null or a.IsDeleted =0)";
		try (Connection conn = ConnectionUtils.getMyConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, nam);
			try (ResultSet rs = ps.executeQuery()) {
				while(rs.next()){
					joKTX.put("", nam);
					if(rs.getInt("tong_dien_tich")>0) 	joKTX.put("tong_dien_tich", rs.getInt("tong_dien_tich"));
					if(rs.getInt("soluong_sv")>0) 		joKTX.put("soluong_sv", rs.getInt("soluong_sv"));
					if(rs.getInt("sv_co_nhu_cau")>0) 	joKTX.put("sv_co_nhu_cau", rs.getInt("sv_co_nhu_cau"));
					if(rs.getInt("sv_o_ktx")>0) 		joKTX.put("sv_o_ktx", rs.getInt("sv_o_ktx"));
					joKTX.put("created_time", rs.getDate("CreatedTime"));
					joKTX.put("created_by", rs.getInt("CreatedBy"));
					joKTX.put("creator", rs.getString("Fullname"));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return joKTX;
	}
}
