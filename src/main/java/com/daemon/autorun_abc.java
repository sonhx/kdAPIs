/*package daemon;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;


import com.log.rawlog;

import db.dbconnect;
import email.SendMailTLS;


class checkNewEventThread{
	rawlog log;

	int i = 0;
	Timer timer;
	private static dbconnect dbcon=null;	
	static SendMailTLS test_mail_tls;
	
	//-----------------------------------------------------------------------------------------
	private static String fn_user_name(int user_id){
		String user_name="unknown";
		Statement s1;
		ResultSet rs1;
		try {
			s1 = dbcon.conn.createStatement();
			rs1 = s1.executeQuery("select * from dbo.tbl_user where ID='"+user_id+"'");
			if(rs1.next()){
				user_name=rs1.getString("Fullname");
			}
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
 		return user_name;
	}
	//-------------------------------------------------------------------------------
	public checkNewEventThread() {
	  	log=new rawlog();
		System.out.println("START timer\n");
		
	    timer = new Timer();
	    timer.schedule(new checkNewEventThreadex(), 1000,6000);//Chu ky 6s
	}
	//------------------------Lay thong tin thong ke------------------------------------------
	public void statistic_count() {
		System.out.println("ezKPI Statistic_count automated Jobs called\n");
		Statement s1;
		ResultSet rs1;		
	}
	//-------------------------Kiem tra va thuc hien cac cong viec dinh ky hay duoc hen lich------
	public void periodic_jobs_check() {
		System.out.println("ezKPI periodic_jobs_check called\n");
		Statement s1;
		ResultSet rs1;		
	}
	//-------------------------------------------------------------------------------------
	public void fn_save_notify_result(String content, int notify_id){
		try {
			Statement s2 = dbcon.conn.createStatement();
         	s2.executeUpdate("update dbo.tbl_notify set Result=N'"+content+"', IsSent='1' where ID='"+notify_id+"'" ); 
         	System.out.println("save Notify State OK");
         	s2.close();
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
	}
	
	//----------------------------------------------------------------------------------------------
	public void send_notify(){
		System.out.println("ezKPI Send Notifies automated Jobs called");
		test_mail_tls = new SendMailTLS();
		
		Statement s1;
		ResultSet rs1;
		try {
			s1 = dbcon.conn.createStatement();
			rs1 = s1.executeQuery("select * from dbo.tbl_notify where IsSent='0'");
			while(rs1.next()){
				test_mail_tls.tlsmain("kiennt@ptit.edu.vn","ezKPI alert",rs1.getString("NotifyContent") );
				fn_save_notify_result("ÄÃ£ lÆ°u",rs1.getInt("ID"));
			}
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
	}
	//----------------------------------------------------------------------------------------------
	class checkNewEventThreadex extends TimerTask {
	      public void run() {

	    	System.out.println("ezKPI START TimerTask:\n"+i);
	    	
	    	statistic_count();
	    	
	    	//periodic_jobs_check();
	    	
	    	send_notify();

	    	i++;
	      }
	}
}	
//--------------------------------------------------------------------------------------------
@SuppressWarnings("serial")
public class autorun extends HttpServlet {

private static dbconnect dbcon=null;	
static DateFormat dateFormat;

	public void autorun(){
		dbcon=new dbconnect();
	}

	public void init() throws ServletException{
		  System.out.println("---- ezKPI AUTORUN INIT------\n");
		  new checkNewEventThread();
	}

}
package daemon;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpServlet;



import db.IOCdbconnect;
import email.SendMailTLS;

class checkNewEventThread{

	int i = 0;
	Timer timer;
	private static IOCdbconnect dbcon=null;	
	static SendMailTLS test_mail_tls;
	
	public checkNewEventThread() {
		dbcon=new IOCdbconnect();
		System.out.println("START timer\n");
		
	    timer = new Timer();
	    timer.schedule(new checkNewEventThreadex(), 1000,60000);//Chu ky 6s
	}
	//----------------------------------------------------------------------------------------------
	class checkNewEventThreadex extends TimerTask {
	      public void run() {

	      }
	}
}	
//--------------------------------------------------------------------------------------------
@SuppressWarnings("serial")
public class autorun extends HttpServlet {

	private static IOCdbconnect dbcon=null;	
	static DateFormat dateFormat;

	public void autorun(){
		dbcon=new IOCdbconnect();
	}

	public void init() throws ServletException{
		  System.out.println("----ezFEEDBACK AUTORUN INIT------\n");
		  new checkNewEventThread();
	}

}

@WebListener
public class autorun_abc implements ServletContextListener {
	public void contextInitialized(ServletContextEvent event) {
		// Do your thing during webapp's startup.
		System.out.println("----ezFEEDBACK AUTORUN INIT------\n");
		new checkNewEventThread();
	}

	public void contextDestroyed(ServletContextEvent event) {
		// Do your thing during webapp's shutdown.
	}
}

*/


