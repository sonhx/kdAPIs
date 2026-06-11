package com.fcm;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;


//-----------------------------------------------------------------------------------------------------------
public class Fcm {
	//private static String DB = dbconnect.GetDbFBM;
	public int sendFCM(JSONObject obj){
		String sResponse = "";
		URL url;
		try {
			url = new URL("https://fcm.googleapis.com/fcm/send");
			
			URLConnection connection = url.openConnection();
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			// key server
			connection
			.setRequestProperty(
					"Authorization",
					"key=AAAA0Jht9wo:APA91bEmtqiEYpkVNbvB5rzP0OPu6MOuKALvaxz4eQ2dkM2ZUnMroR58-6f2qerbLvCJnzdfVn5i_N8fyVvLr6oGZ_2sOx0XJJ1Tqs4d8jDPH1FDGhu8dv3M8iP_RJ3TLKZVxaPXBvk9");//farrmnote
			
			
			connection.setConnectTimeout(50000);
			connection.setReadTimeout(50000);
			OutputStreamWriter out = new OutputStreamWriter(
					connection.getOutputStream(), "UTF-8");
			out.write(obj.toString());
			out.close();
			
			BufferedReader in = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
			String rt;
			while ((rt = in.readLine()) != null) {
				sResponse += rt;
			}
			
			System.out.println("\nREST Service Invoked Successfully, return: "+ sResponse);
			in.close();
		} catch (MalformedURLException e) {
			e.printStackTrace(); return -2;
		} catch (IOException e) {
			e.printStackTrace(); return -3;
		}
		
		return 1;
	}
	//-----------------------------------------------------------------------------------------------------------
	public int sendFcm2Device(JSONObject objNoti, JSONObject objMessage,
			String sToKey) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("notification", objNoti);
			obj.put("data", objMessage);
			obj.put("to", sToKey);

			obj.put("priority", "high");
			obj.put("collapse_key", "demo");
			obj.put("time_to_live", 3);
			System.out.println("Output JSON is "+obj.toString());
		} catch (JSONException e) {
			e.printStackTrace(); return -1;
		}
		return sendFCM(obj);
	}
	//-----------------------------------------------------------------------------------------------------------	
	public int sendFcm2Topic(JSONObject objNoti, JSONObject objMessage,
			String sTopic) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("notification", objNoti);
			obj.put("data", objMessage);
			
			obj.put("priority", "high");
			obj.put("collapse_key", "demo");
			obj.put("time_to_live", 3);
			
			obj.put("to","/topics/"+sTopic);
//			obj.put("to","/topics/system_notification");
		} catch (JSONException e) {
			e.printStackTrace(); return -1;
		}
		return sendFCM(obj);
	}
	//-----------------------------------------------------------------------------------------------------------
	/**
	 * This method allows sending a message to multiple topics at the same time
	 * @param objNoti
	 * @param objMessage
	 * @param sTopic
	 * @return
	 */
	//sonhx 20190325
	public int sendFcm2MultipleTopics(JSONObject objNoti, JSONObject objMessage,
			ArrayList<String> arrTopics) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("notification", objNoti);
			obj.put("data", objMessage);
			
			obj.put("priority", "high");
			obj.put("collapse_key", "demo");
			
			obj.put("time_to_live", 3);
			
//			obj.put("to","/topics/"+sTopic);
//			obj.put("to","/topics/system_notification");
			String sTopic = "";
			sTopic = arrTopics.get(0);
			if(arrTopics.size()>1){
				for(int i=0; i<arrTopics.size(); i++){
					sTopic += "&& '"+arrTopics.get(i)+"'"+ " in topics";
				}
			}
			
			
			obj.put("condition","/topics/"+sTopic);
			/*const message = {
					  notification: {
					    title: 'EPA fuel economy stats for new Mazda6',
					    body: 'New turbo charged 2.5L engine does 23/31/36 mpg.',
					  },
					  condition: `'auto-news' in topics && 'green-earth' in topics`,
					};*/
		} catch (JSONException e) {
			e.printStackTrace(); return -1;
		}
		return sendFCM(obj);
	}
	
	//-----------------------------------------------------------------------------------------------------------
	public JSONObject composeObjNoti(String sTitle, String sBody) {
		JSONObject objNoti = new JSONObject();
		try {
			objNoti.put("content_available", "true");
			objNoti.put("body", sBody);
			objNoti.put("title", sTitle);
			objNoti.put("sound", "enabled");
			objNoti.put("icon", "https://ezlife.vn/redsun/assets/layouts/layout4/img/ezfeedback_1.png");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return objNoti;
	}
	//-----------------------------------------------------------------------------------------------------------
	public JSONObject composeObjMessage(String sTitle, String sContent, String sSender_uname, String sSender_fullname) {
		JSONObject objMessage = new JSONObject();
		try {
			objMessage.put("event_title", sTitle);
			objMessage.put("event_content", sContent);

			objMessage.put("send_usr_name", sSender_uname);
			objMessage.put("send_usr_fullname", sSender_fullname);
			objMessage.put("event_send_timestamp",
					new java.util.Date().getTime());
			objMessage.put("event_content_type", 4);
			objMessage.put("event_content_priority", 1);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return objMessage;
	}
	//=============================================================================================
	public static void main(String[] args) {
		JSONObject objNoti = new JSONObject();
		JSONObject objMessage = new JSONObject();
		String sTitle = "Thông báo1";
		String sContent = "Thông báo nâng cấp hệ thống vào lúc 00:00 ngày 20/05/2017";

		String sUserToken = "e4bNRMMZT6u8FpM1bjXIre:APA91bGB_rUa5poRKQyIksBFK0Qg8lsCf26OMrXhZ6iHyw_igEgAt3GoxJa-XcvMuRRJCRREedPeZH7HazlOhtM7mL8PDWU8dD05D5G4Ud1ESm8QjkbAdQhQyVwvVnnDeh2iz8bXq9-L";//FarmNote
		
		Fcm fcm = new Fcm();
		objNoti = fcm.composeObjNoti(sTitle,
				sContent);
		objMessage = fcm.composeObjMessage(sTitle,
						sContent,"zin","FarmNote System");

		System.out.println("noti: " + objNoti.toString());
		System.out.println("data: " + objMessage.toString());

		fcm.sendFcm2Device(objNoti, objMessage, sUserToken);
	}

}

