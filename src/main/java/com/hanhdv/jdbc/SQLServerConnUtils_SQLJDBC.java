package com.hanhdv.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLServerConnUtils_SQLJDBC {
	// Kết nối vào SQLServer.
	 // (Sử dụng thư viện đi�?u khiển SQLJDBC)
	 public static Connection getSQLServerConnection()
	         throws SQLException, ClassNotFoundException {
		 
	     String hostName = "localhost";
	     String sqlInstanceName = "MSSQLSERVER";
	     
	     //String database = "PTITIoC";
	     String database = "kiemdinh";
	     //String userName = "sa1";	//localhóst
	     //String password = "sa1";  
	     
	     //String userName = "sa1";
	     String userName = "sa";
	     String password = "Cdit@mothai34nam"; 	   //eyecheck  
	   
	     
	    
	     return getSQLServerConnection(hostName, sqlInstanceName,
	             database, userName, password);
	 }
	 public static Connection getSQLServerTNTHConnection()
	         throws SQLException, ClassNotFoundException {
	     String hostName = "localhost";
	     String sqlInstanceName = "MSSQLSERVER";
	     String database = "TNTH";
	     String userName = "sa";
	     String password = "sa@123456";
	     
	    
	     return getSQLServerConnection(hostName, sqlInstanceName,
	             database, userName, password);
	 }
	 
	 public static Connection getSQLServerKiemdinhConnection()
			 throws SQLException, ClassNotFoundException {
		 String hostName = "localhost";
		 String sqlInstanceName = "MSSQLSERVER";
		 String database = "kiemdinh";
		 String userName = "sa1";
		 String password = "Cdit@mothai34nam";
		 
		 return getSQLServerConnection(hostName, sqlInstanceName,
				 database, userName, password);
	 }
	// Trư�?ng hợp sử dụng SQLServer.
	 // Và thư viện SQLJDBC.
	 public static Connection getSQLServerConnection(String hostName,
	         String sqlInstanceName, String database, String userName,
	         String password) throws ClassNotFoundException, SQLException {
	     // Khai báo class Driver cho DB SQLServer
	     // Việc này cần thiết với Java 5
	     // Java6 tự động tìm kiếm Driver thích hợp.
	     // Nếu bạn dùng Java6, thì ko cần dòng này cũng được.
	     Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

	     // Cấu trúc URL Connection dành cho SQLServer
	     // Ví dụ:
	     // jdbc:sqlserver://ServerIp:1433/SQLEXPRESS;databaseName=simplehr
	     String connectionURL = "jdbc:sqlserver://" + hostName + ":1433"
	             + ";instance=" + sqlInstanceName 
	             + ";databaseName=" + database;
       
	     Connection conn = DriverManager.getConnection(connectionURL, userName,
	             password);
	     return conn;
	 }
}
