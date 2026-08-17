package com.session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	public int fn_get_root_org(int org_id){
	   int root_org_id = 0;
	   java.util.Set<Integer> visited = new java.util.HashSet<>();
	   while(org_id > 0){
		   if (visited.contains(org_id)) {
			   System.out.println("Cycle detected in fn_get_root_org for org_id: " + org_id);
			   break;
		   }
		   visited.add(org_id);
		   final int current_org_id = org_id;
		   List<Integer> parentIds = jdbcTemplate.query("select ParentID from tbl_org where ID = ?", 
				   (rs, rowNum) -> rs.getInt("ParentID"), current_org_id);
		   
		   if(!parentIds.isEmpty()){
			   Integer parentId = parentIds.get(0);
			   if (parentId == null || parentId == org_id) {
				   root_org_id = org_id;
				   break;
			   }
			   root_org_id = org_id;
			   org_id = parentId;
           } else {
        	   root_org_id = -1;
        	   org_id = -1;
           }
	   }
	   return root_org_id;
	}

	public String createSession(int user_id){
		return createSession((Object) user_id);
	}

	public String createSession(Object user_id){
		String uuid = UUID.randomUUID().toString();
		String userIdStr = user_id != null ? user_id.toString() : "";
		
		if(!userIdStr.isEmpty()){
			// Invalidate existing sessions
			try {
				jdbcTemplate.update("update tbl_session set isdeleted = 1 where userid = ? and isdeleted = 0", userIdStr);
			} catch (Exception e) {
				// Notice updating sessions
			}
		}

		jdbcTemplate.update("insert into tbl_session (userid, isdeleted, createtime, sessionid) values (?, 0, GETDATE(), ?)", 
				userIdStr, uuid);
		
		return uuid;
	}

	public struct_session getSessionInfo(String session_id){	
		if (session_id == null || session_id.isBlank()) {
			return null;
		}
		try {
			List<struct_session> sessions = jdbcTemplate.query(
					"select * from dbo.tbl_session where sessionid = ? and (isdeleted = 0 or isdeleted is null)",
					(rs1, rowNum) -> {
						struct_session ss = new struct_session();
						Object rawUserId = rs1.getObject("UserID");
						String userIdStr = rawUserId != null ? rawUserId.toString() : "";
						ss.sUserId = userIdStr;
						ss.UserID = rawUserId != null ? (rawUserId instanceof Number ? ((Number) rawUserId).intValue() : 1) : 0;
						ss.State = rs1.getInt("IsDeleted");
						
						if(!userIdStr.isEmpty()){
							ss.OrgID = 0;
							ss.RootOrgID = 0;
						} else {
							ss.UserType = 0;
						}
						return ss;
					}, session_id);

			return sessions.isEmpty() ? null : sessions.get(0);
		} catch (Exception e) {
			System.err.println("Notice: Database connection retry in getSessionInfo: " + e.getMessage());
			try {
				List<struct_session> sessions = jdbcTemplate.query(
						"select * from dbo.tbl_session where sessionid = ? and (isdeleted = 0 or isdeleted is null)",
						(rs1, rowNum) -> {
							struct_session ss = new struct_session();
							Object rawUserId = rs1.getObject("UserID");
							String userIdStr = rawUserId != null ? rawUserId.toString() : "";
							ss.sUserId = userIdStr;
							ss.UserID = rawUserId != null ? (rawUserId instanceof Number ? ((Number) rawUserId).intValue() : 1) : 0;
							ss.State = rs1.getInt("IsDeleted");
							
							if(!userIdStr.isEmpty()){
								ss.OrgID = 0;
								ss.RootOrgID = 0;
							} else {
								ss.UserType = 0;
							}
							return ss;
						}, session_id);

				return sessions.isEmpty() ? null : sessions.get(0);
			} catch (Exception ex) {
				System.err.println("Error fetching session info after retry: " + ex.getMessage());
				return null;
			}
		}
	}
}
