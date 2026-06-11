package com.daemon;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.db.IOCdbconnect;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

class checkNewTimeEventThread{

	int i = 0;
	Timer timer;
	private static IOCdbconnect dbcon=null;	
	
	public checkNewTimeEventThread() {
		dbcon=new IOCdbconnect();
	  	
		//System.out.println("TIMER JOBS: START timer\n");
		
	    timer = new Timer();
	    timer.schedule(new checkNewTimeEventThreadex(), 1000,60000);

	}
	//-----------------------------------------------------------------------------
	/*private static String fn_user_email(int user_id){
		String user_email="";
		Statement s1;
		ResultSet rs1;
		try {
			s1 = dbcon.conn.createStatement();
			rs1 = s1.executeQuery("select * from dbo.tbl_user where ID='"+user_id+"'");
			if(rs1.next()){
				user_email=rs1.getString("Email");
			}
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
 		return user_email;
	}*/
	//----------------------------------------------------------------------------------------------
	private int is_gate_open(int bd,int bm,int ed, int em){//begin date, begin month, end date, end onth
		DateFormat dateFormat= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date current_time=new Date();
		
		int current_year=current_time.getYear()+1900;
		
		String str_begin_time=""+current_year+"-"+String.format("%02d", bm)+"-"+String.format("%02d", bd)+" 00:00:00.000";
		String str_end_time=""+current_year+"-"+String.format("%02d", em)+"-"+String.format("%02d", ed)+" 00:00:00.000";;
		
		try {
		
			
			Date begin_time = dateFormat.parse(str_begin_time);
			Date end_time=dateFormat.parse(str_end_time);
			if(current_time.after(end_time)){
				return -1;
			}else if(current_time.before(end_time)&& current_time.after(begin_time)){
				return 1;
			}else{
				return 0;
			}

		} catch (ParseException e) {
			
			e.printStackTrace();
		}
		return 0;
		
    }
	/*private void create_message_and_notify(int kpi_id,int data_id, int flag_open,String batch_name){
		Statement s1,s2;
		ResultSet rs1,rs2;
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date date = new Date();
		
		String content="";
		if(flag_open==0)content="Info: Káº¿t thÃºc Ä‘á»£t nháº­p liá»‡u KPI_ID="+kpi_id+" Data source:"+data_id+","+batch_name;
		else content="Info: Báº¯t Ä‘áº§u Ä‘á»£t nháº­p liá»‡u KPI_ID="+kpi_id+" Data source:"+data_id+","+batch_name;

		System.out.println("create_message_and_notify : "+content);
		
		try {
			s1 = dbcon.conn.createStatement();
			s2 = dbcon.conn.createStatement();
			
			String sql_str="", d_src_name="";
			
			if(data_id==0){
				d_src_name="KpiDataOwnerID";
				sql_str="select * from TBL_KPI_INPUT_INFO where KpiID="+kpi_id +" and IsDirectInput=1 and KpiDataOwnerID>0";
			}else{
				d_src_name="DataSourceOwner"+data_id+"ID";
				sql_str="select * from TBL_KPI_INPUT_INFO where KpiID="+kpi_id +" and IsDirectInput=0 and "+d_src_name+">0";
			}
			
			rs1=s1.executeQuery(sql_str);
			if(rs1.next()){
				
				s2.executeUpdate("insert into TBL_MESSAGE (SenderID,ReceiverID,MsgSubject,MsgContent,CreatedTime) " 
						+ "values(0,"+rs1.getInt(d_src_name)+",N'ThÃ´ng bÃ¡o',N'"+content+ "','" + dateFormat.format(date) + "')");
				
				s2.executeUpdate("insert into TBL_NOTIFY (UserID,SessionID,NotifyContent,Email,CreatedTime) " 
						+ "values(0,'',N'"+content+"',N'"+fn_user_email(rs1.getInt(d_src_name))+ "','" + dateFormat.format(date) + "')");

			}
			s2.close();
			s1.close();
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
		
	}*/
	//---------------------------------------------------------------------------------
	/*public void input_data_deadline_scan(){
		
		Statement s1,s2,s3;
		ResultSet rs1;
		String sql="Select * from TBL_HDHV_KPI_DATA_SOURCE_BATCH where (IsDeleted =0 or IsDeleted is NULL)";
		try {
			Date current_time=new Date();
			
			int current_month=current_time.getMonth()+1;
			int current_date=current_time.getDate();
		
			//System.out.println("TIMER SCAN KPI GATE: START current_date:"+current_date+" -MONTH:"+current_month);
			
			s1 = dbcon.conn.createStatement();
			rs1=s1.executeQuery(sql);
			int is_d1_on=0,is_d2_on=0,is_d3_on=0,is_d4_on=0,is_k_on=0;
			int d1_on=0,d2_on=0,d3_on=0,d4_on=0,k_on=0;
			
			s2 = dbcon.conn.createStatement();
			while(rs1.next()){
				int kpi_id=rs1.getInt("KpiID");
				
				is_d1_on=rs1.getInt("IsData1Open");
				d1_on=is_gate_open(rs1.getInt("Data1BeginDate"),rs1.getInt("Data1BeginMonth"),rs1.getInt("Data1EndDate"),rs1.getInt("Data1EndMonth"));
				
				
				if(d1_on!=is_d1_on){
					//create_message_and_notify(kpi_id,1,d1_on,rs1.getString("Name"));
				}
				
				is_d2_on=rs1.getInt("IsData2Open");
				d2_on=is_gate_open(rs1.getInt("Data2BeginDate"),rs1.getInt("Data2BeginMonth"),rs1.getInt("Data2EndDate"),rs1.getInt("Data2EndMonth"));
							
				if(d2_on!=is_d2_on){
					//create_message_and_notify(kpi_id,2,d2_on,rs1.getString("Name"));
				}
				
				d3_on=is_gate_open(rs1.getInt("Data3BeginDate"),rs1.getInt("Data3BeginMonth"),rs1.getInt("Data3EndDate"),rs1.getInt("Data3EndMonth"));
				is_d3_on=rs1.getInt("IsData3Open");
				if(d3_on!=is_d3_on){
					//create_message_and_notify(kpi_id,3,d3_on,rs1.getString("Name"));
				}
				
				d4_on=is_gate_open(rs1.getInt("Data4BeginDate"),rs1.getInt("Data4BeginMonth"),rs1.getInt("Data4EndDate"),rs1.getInt("Data4EndMonth"));
				is_d4_on=rs1.getInt("IsData4Open");
				if(d4_on!=is_d4_on){
					//create_message_and_notify(kpi_id,4,d4_on,rs1.getString("Name"));
				}
				is_k_on=rs1.getInt("IsKPIOpen");
				k_on=is_gate_open(rs1.getInt("CalDate"),rs1.getInt("CalMonth"),rs1.getInt("CalDateEnd"),rs1.getInt("CalMonthEnd"));
				if(k_on!=is_k_on){
					//create_message_and_notify(kpi_id,0,k_on,rs1.getString("Name"));
				}
				
				String sql_str="Update TBL_HDHV_KPI_DATA_SOURCE_BATCH set IsData1Open="+d1_on
						+",IsData2Open="+d2_on+",IsData3Open="+d3_on+",IsData4Open="+d4_on
						+",IsKPIOpen="+k_on +" where ID="+rs1.getInt("ID");
				s2.executeUpdate(sql_str);
				
			}
			s2.close();
			s1.close();
		
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}*/
	//----------------------------------------------------------------------------------------------
	class checkNewTimeEventThreadex extends TimerTask {
	      public void run() {
//	    	input_data_deadline_scan();
	    	i++;
	    	
	      }
	}
}
//--------------------------------------------------------------------------------------------
@SuppressWarnings("serial")
public class timerjob extends HttpServlet {

private static IOCdbconnect dbcon=null;	
static DateFormat dateFormat;

	public timerjob(){
		dbcon=new IOCdbconnect();
	}

	public void init() throws ServletException{
		  System.out.println("----timerjob INIT------\n");
		 new checkNewTimeEventThread();
	}

}

