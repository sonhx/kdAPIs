package com.db;


import java.sql.Connection;
import java.sql.DriverManager;

public class IOCdbconnect 
    {
	private static volatile Connection conn = null;
	public static int notifythread_ready = 0;
	private boolean LOCAL = true;
	public int result = 0;
	public String res = null;

	/**
	 * Constructor - ensures a Connection is available via getConnection().
	 * New code should call {@link #getConnection()} instead of accessing
	 * the connection field directly.
	 */
	public IOCdbconnect() {
        try {
            Connection c = getConnection();
            if (c != null) {
                result = 0;
            } else {
                result = -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            res = e.toString();
            result = -3;
        }
    }

	/**
	 * Thread-safe getter for a shared Connection. Reads DB configuration
	 * from system properties or environment variables when available:
	 * KD_DB_URL, KD_DB_USER, KD_DB_PASS. Falls back to legacy defaults.
	 */
	public static synchronized Connection getConnection() throws Exception {
        try {
            if (conn != null && !conn.isClosed()) {
                return conn;
            }

            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            String url = System.getProperty("KD_DB_URL", System.getenv("KD_DB_URL"));
            String user = System.getProperty("KD_DB_USER", System.getenv("KD_DB_USER"));
            String pass = System.getProperty("KD_DB_PASS", System.getenv("KD_DB_PASS"));

            if (url == null || url.isEmpty()) {
                // legacy default
                url = "jdbc:sqlserver://localhost:1433;DatabaseName=kiemdinh";
                user = (user == null) ? "sa1" : user;
                pass = (pass == null) ? "Cdit@mothai34nam" : pass;
            }

            if (user != null && pass != null) {
                conn = DriverManager.getConnection(url + ";user=" + user + ";password=" + pass);
            } else {
                conn = DriverManager.getConnection(url);
            }

            if (conn != null && !conn.isClosed()) {
                System.out.println("DB: connected successfully");
            }
            return conn;
        } catch (Exception ex) {
            conn = null;
            throw ex;
        }
    }

}