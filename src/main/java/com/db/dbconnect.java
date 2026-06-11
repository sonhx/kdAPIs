package com.db;


import java.sql.Connection;
import java.sql.DriverManager;

public class dbconnect {
	public static Connection conn=null;
	public static int notifythread_ready=0;
	boolean LOCAL=true;
	public int result=0;
	public String res=null;
	public static String DB = "jdbc/KD";
	
	public dbconnect(){
        try{
 			if(conn==null){
				System.out.println("DB : Connecting to database.....");
//	            Class.forName("com.microsoft.jdbc.sqlserver.SQLServerDriver"); 
	            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver"); 
	            
	            if(LOCAL){
	            	conn=DriverManager.getConnection("jdbc:sqlserver://localhost:1433;" + 
	                       "DatabaseName=kiemdinh;User=sa1;Password=Cdit@mothai34nam;");    
	            }else{
			           conn=DriverManager.getConnection("jdbc:sqlserver://10.99.3.102:1433;" + 
			        		   "DatabaseName=kiemdinh;User=sa1;Password=Cdit@mothai34nam;");  
	            }  
	
	            if(conn!=null){
	            	result=0;
	            	System.out.println("DB Kiemdinh: Ket noi thanh cong");
	            }else{
	            	result=-1;
	            	System.out.println("DB Kiemdinh: Ket noi that bai");
	            }
			}else{
				result=-2;
				//System.out.println("Connect to database ready before");
			}
        } catch (Exception e){
            e.printStackTrace();
            res=e.toString();
            result=-3;
        } 	 
	}
}
