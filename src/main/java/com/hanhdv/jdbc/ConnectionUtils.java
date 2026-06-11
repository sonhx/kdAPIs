package com.hanhdv.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionUtils {
	public static Connection getMyConnection() throws SQLException,
			ClassNotFoundException {
		// Sử dụng SQL.
		// có thể thay thế bởi Database nào đó.
		return SQLServerConnUtils_SQLJDBC.getSQLServerConnection();
	}

	public static Connection getMyTNTHConnection() throws SQLException,
			ClassNotFoundException {
		// Sử dụng SQL.
		// có thể thay thế bởi Database nào đó.
		return SQLServerConnUtils_SQLJDBC.getSQLServerTNTHConnection();
	}
	

}
