package com.hanhdv.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class QueryData {
	
	public static void main(String[] args) throws ClassNotFoundException,
			SQLException {
		// Lay doi tuong connection ket noi
		Connection conn = ConnectionUtils.getMyConnection();
        System.out.println("aaaaaaaaaaaaaaaaaaaaaa");
		// Tao doi tuong statement
//		Statement statement = conn.createStatement();
		//
//		String sql = "Select name from PMS_Staff";
//
//		ResultSet rs = statement.executeQuery(sql);
//		while (rs.next()) {
//			String name = rs.getString("name");
//			System.out.println("-----------------");
//			System.out.println("Ten la:" + name);
//		}
//		
	// call store procedure
		
	}
}
