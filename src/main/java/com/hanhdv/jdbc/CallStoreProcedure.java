package com.hanhdv.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CallStoreProcedure {

	public static void main(String[] args) {
		try {
			Connection con= ConnectionUtils.getMyConnection();
			String sql = "{CALL getUserById(?)}";
			CallableStatement cs = con.prepareCall(sql);
			cs.setInt(1, 3);
			cs.executeQuery();
			ResultSet rs = cs.getResultSet();
			while (rs.next()) {
			  System.out.println(rs.getInt(1) + "  " + rs.getString(2) + "  " + rs.getString(3));
			}
			
		} catch (ClassNotFoundException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
