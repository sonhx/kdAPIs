package com.ws;

/*
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.websocket.EncodeException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.session.SessionService;
import com.session.struct_session;

import db.dbconnect;

@ServerEndpoint("/endpoint")

public class WsEndPoint {
    
	private Session session;
    
	private static final Set<WsEndPoint> wsEndpoints = new CopyOnWriteArraySet<>();
   
	//private static HashMap<String, String> users = new HashMap<>();
    
    private String session_id="";
    private int IS_BOT=0;
    private int user_id=0;
    private int user_type=0;
    private int root_org_id=0;
    
    private String board_code="";
    private int board_id=0;
    
    
    private static dbconnect dbcon=null;	
	static DateFormat dateFormat;
	
	public WsEndPoint(){
     	dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		dbcon=new dbconnect();
	}
	//========================================================================================
	private static String fn_user_name(int user_id) {
		String user_name = "ChÆ°a gÃ¡n ngÆ°á»i";
		Statement s1;
		ResultSet rs1;
		try {
			s1 = dbcon.conn.createStatement();
			rs1 = s1.executeQuery("select * from dbo.tbl_user where ID='" + user_id + "'");
			if (rs1.next()) {
				user_name = rs1.getString("FullName");
			}
		} catch (SQLException e) {

			e.printStackTrace();
		}
		return user_name;
	}
	
	//----------------------------------------------------
	@OnOpen
    public void onOpen(Session session) {
        System.out.println("WEBSOCKET onOpen::" + session.getId());
        this.session = session;
        
        
        wsEndpoints.add(this);
        System.out.println("ep size:" +wsEndpoints.size());
        
        Map<String, List<String>> params = session.getRequestParameterMap();
        try {
        	System.out.println(params.toString());
        	
        	String session_id="";
        	if (params.get("session_id") != null ){
                session_id=params.get("session_id").get(0);
                
                this.session_id=session_id;
                System.out.println("SESSION_ID:"+session_id);
                JSONObject jout = new JSONObject();
                
                if(session_id.equals("0000000000")){//BOT
                	this.IS_BOT=1;
                	this.board_code=params.get("board_code").get(0);
                	
                	
                	Statement s1= dbcon.conn.createStatement();
            		ResultSet rs1 = s1.executeQuery("select * from dbo.tbl_iot_board where Code=N'" + this.board_code + "'");
            		if (rs1.next()) {
            			
            			this.board_id = rs1.getInt("ID");
            			this.root_org_id=rs1.getInt("OrgID");
            		}
            		                	
                
                	
                	System.out.println("From BOT --->>>>>>>>>>>>>>:"+this.board_code);
                	
                }else{
                	System.out.println("From User");
	                //verify user
	     			SessionService ss= new SessionService();
	     			struct_session sst=ss.getSessionInfo(session_id);
	     			
	     			if(sst!=null){
	     				this.user_id=sst.UserID;
	     				this.user_type=sst.UserType;
	     				this.root_org_id=sst.RootOrgID;
	     			}
	     			
	            	String content="CÃ³ WS client má»›i:"+session_id;
	            	
	            	jout.put("user_id",this.user_id);
	            	jout.put("user_name",fn_user_name(this.user_id));
	            	jout.put("created_time",dateFormat.format(new Date()));
	    			jout.put("content",content);
	    			
	    			
                }
                
                jout.put("type","OPENED");
                
     			session.getBasicRemote().sendText(jout.toString());
                
            }else{
            	 System.out.println("Error: NO SESSION_ID");
            }
			
 		} catch (IOException | JSONException | SQLException e) {
			e.printStackTrace();
		}
    }
	//-------------------------------------------------------------------------------------
    @OnClose
    public void onClose(Session session) {
        System.out.println("WEBSOCKET onClose::" +  session.getId());
        System.out.println("weEndpoint size:" +wsEndpoints.size());
        
        System.out.println("endpoint size before:" +wsEndpoints.size());
        wsEndpoints.remove(this);
        System.out.println("endpoint size after:" +wsEndpoints.size());
          
        
    }
    //-------------------------------------------------------------------------------------
    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("WEBSOCKET onMessage:From=" + session.getId() + " Message=" + message);
       
        try {
            broadcast_to_user(message,0);
        } catch (IOException | EncodeException e) {
            e.printStackTrace();
        }
    }
    //-------------------------------------------------------------------------------------
    @OnError
    public void onError(Throwable t) {
        System.out.println("WS onError:" + t.getMessage());
    }
    //-------------------------------------------------------------------------------------
    public void broadcast_to_user(String message, int user_id) throws IOException, EncodeException {
    	Statement s1;
    	DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    	Date date = new Date();	 
    	 try {
    		s1 = dbcon.conn.createStatement();
    		int send=0;
    		
	    	for( WsEndPoint e : wsEndpoints ) {
	           
	            if(e.session.isOpen()){
	            	
	            	if(e.user_type==1 || e.user_id==user_id){
	            		
	            		JSONObject jout = new JSONObject();
	                	jout = new JSONObject();
	        			jout.put("content",message);
	        			jout.put("created_time",dateFormat.format(date));
	        			jout.put("type","INFO");
	            		
	            		e.session.getBasicRemote().sendText(jout.toString());
	            		
	            		s1.executeUpdate("insert into TBL_SEASON_WS_PUSH_LOG(PushContent,UserID,IsWsChannelReady,IsPushOk,CreatedTime) " +
	            				"values(N'"+message+"',"+user_id+","+1+",1,'"+dateFormat.format(date)+"')");
	            		send=1;
	            	}else{
	            		System.out.printf( "PASS e.user_type:"+e.user_type+"--"+e.user_id+"--"+user_id);
	            		//e.session.getBasicRemote().sendText(">>>>>>>NOTI>>>>TO USER:"+e.user_id+"<<<<<<NOT FOR YOU<<<<<<<");
	            	}
	            }else{
	            	System.out.printf( "PASS because session closed: "+e.IS_BOT+"--"+e.user_id+"--"+e.board_code);
	            }
	            
	            
	        }
	    	if(send==0){
            	s1.executeUpdate("insert into TBL_SEASON_WS_PUSH_LOG(PushContent,UserID,IsWsChannelReady,IsPushOk,CreatedTime) " +
        				"values(N'"+message+"',"+user_id+","+0+",0,'"+dateFormat.format(date)+"')");

	    	}
	    	s1.close();
	    	System.out.println("");
    	 } catch (SQLException | JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		 }
    }
    public void broadcast_to_bot(String message, String board_code) throws IOException, EncodeException {
    	Statement s1;
    	DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    	Date date = new Date();	 
    	 try {
    		s1 = dbcon.conn.createStatement();
    		//int send=0;
    		
	    	for( WsEndPoint e : wsEndpoints ) {
	           
	            if(e.session.isOpen() && e.IS_BOT==1){
	            	
	            	if(e.board_code.equalsIgnoreCase(board_code)){
	            		
	            		JSONObject jout = new JSONObject();
	                	jout = new JSONObject();
	        			jout.put("content",message);
	        			
	        			jout.put("created_time",dateFormat.format(date));
	        			jout.put("type","COMMAND");
	            		
	            		e.session.getBasicRemote().sendText(jout.toString());
	            		
	            		//s1.executeUpdate("insert into TBL_SEASON_WS_PUSH_LOG(PushContent,UserID,IsWsChannelReady,IsPushOk,CreatedTime) " +
	            		//		"values(N'"+message+"',"+user_id+","+1+",1,'"+dateFormat.format(date)+"')");
	            		//send=1;
	            	}else{
	            		System.out.printf( "PASS e.user_type:"+e.user_type+"--"+e.user_id+"--"+user_id);
	            		//e.session.getBasicRemote().sendText(">>>>>>>NOTI>>>>TO USER:"+e.user_id+"<<<<<<NOT FOR YOU<<<<<<<");
	            	}
	            }else{
	            	// System.out.printf( "PASS beacuse session closed -BOTTTT");
	            }
	            
	            
	        }
	    	if(send==0){
            	s1.executeUpdate("insert into TBL_SEASON_WS_PUSH_LOG(PushContent,UserID,IsWsChannelReady,IsPushOk,CreatedTime) " +
        				"values(N'"+message+"',"+user_id+","+0+",0,'"+dateFormat.format(date)+"')");

	    	}
	    	s1.close();
	    	System.out.println("");
    	 } catch (SQLException | JSONException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		 }
    }
  
    //-------------------------------------------------------------------------------------
    public JSONArray list_ws_user(int org_id) throws IOException, EncodeException {
    	//DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    	//Date date = new Date();	 
    	JSONArray result_jarr=new JSONArray();
    	try {
	    	for( WsEndPoint e : wsEndpoints ) {
	    		
	    		//System.out.println(e.session.getId()+"--"+e.root_org_id+"--"+fn_user_name(e.user_id));
	            
	    		if(e.session.isOpen()){
	            	//System.out.println("11111:"+org_id+"--"+e.root_org_id);
	            	
	            	if(org_id==0 || e.root_org_id==org_id){
	            		//System.out.println("2222");
	            		JSONObject obj = new JSONObject();
	            		obj = new JSONObject();
	                	
	            		obj.put("is_bot",e.IS_BOT);
	            		
	            		if(e.IS_BOT==0){
	            			obj.put("user_id",e.user_id);
	            			obj.put("user_name",fn_user_name(e.user_id));
	            		}else{
	            			obj.put("user_id",e.board_id);
	            			obj.put("user_name",e.board_code);
	            		}
	            		
	            		obj.put("ws_session_id",e.session.getId());
	            		
	            		result_jarr.put(obj);
	            	}
	            }
	        }
	    	
    	 } catch (JSONException e1) {

			e1.printStackTrace();
		 }
    	return result_jarr;
    }
   
}*/



