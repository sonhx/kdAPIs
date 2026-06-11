package com.hanhdv.jdbc;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;


public class PrepareStatement {

	public static void setValues(PreparedStatement preparedStatement,
			Object... values) throws SQLException {
		for (int i = 0; i < values.length; i++) {
			preparedStatement.setObject(i + 1, values[i]);
		}
	}

	public static void main(String[] args) {
		try {
			Connection conn = ConnectionUtils.getMyConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("Insert into tbl_employee(name,year,created_date) values(?,?,?)");
			/*preparedStatement.setString(1, "Hanhdv");
			preparedStatement.setInt(2, 1980);
			preparedStatement.setDate(3, java.sql.Date.valueOf("2013-09-04"));
			preparedStatement.executeUpdate();*/

			setValues(preparedStatement, "Không có gì quí hơn độc lập tự do", 1980,java.sql.Date.valueOf("2013-09-04") );
			preparedStatement.executeUpdate();
			
		} catch (ClassNotFoundException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
