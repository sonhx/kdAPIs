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
		String uuid = UUID.randomUUID().toString();
		
		if(user_id > 0){
			// Invalidate existing sessions
			jdbcTemplate.update("update tbl_session set isdeleted = 1 where userid = ? and isdeleted = 0", user_id);
		}

		jdbcTemplate.update("insert into tbl_session (userid, isdeleted, createtime, sessionid) values (?, 0, GETDATE(), ?)", 
				user_id, uuid);
		
		return uuid;
	}

	public struct_session getSessionInfo(String session_id){	
		List<struct_session> sessions = jdbcTemplate.query(
				"select * from dbo.tbl_session where sessionid = ? and isdeleted = 0",
				new RowMapper<struct_session>() {
					@Override
					public struct_session mapRow(ResultSet rs1, int rowNum) throws SQLException {
						struct_session ss = new struct_session();
						ss.UserID = rs1.getInt("UserID");
						ss.State = rs1.getInt("IsDeleted");
						
						if(ss.UserID > 0){
							List<struct_session> users = jdbcTemplate.query(
									"select * from dbo.tbl_user where Id = ?",
									(rs2, rowNum2) -> {
										struct_session userSs = new struct_session();
										userSs.OrgID = rs2.getInt("OrgID");
										return userSs;
									}, ss.UserID);
							
							if(!users.isEmpty()){
								ss.OrgID = users.get(0).OrgID;
								ss.RootOrgID = fn_get_root_org(ss.OrgID);
							} else {
								ss.UserType = -1;
								System.out.println("getSessionInfo error: UserID-" + ss.UserID + "-Not exist");
							}
						} else {
							ss.UserType = 0;
						}
						return ss;
					}
				}, session_id);

		return sessions.isEmpty() ? null : sessions.get(0);
	}
}
