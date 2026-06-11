
package com.daemon;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;





import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;

import com.db.IOCdbconnect;
import com.email.SendMailTLS;

class checkNewEventThread{

	int i = 0;
	Timer timer;
	private static IOCdbconnect dbcon=null;	
	static SendMailTLS test_mail_tls;
	
	public checkNewEventThread() {
		dbcon=new IOCdbconnect();
	  	
		
		System.out.println("START timer\n");
		
	    timer = new Timer();
	    timer.schedule(new checkNewEventThreadex(), 1000,6000000);//Chu ky 6s TODO

	}
	//----------------------------------------------------------------------------------
	
	//----------------------------------------------------------------------------------------------
	class checkNewEventThreadex extends TimerTask {
	      public void run() {
	    	  String ocrIn = "C:\\Users\\sonho\\Desktop\\ocrIn"; //for input folder
	    	  String ocrOut = "C:\\Users\\sonho\\Desktop\\ocrOut"; //for output folder
	    	  String abbyyProg = "C:\\Program Files\\ABBYY FineReader 16"; //path to abbyy program
	    	  
	    	  //This watch service will watch a folder specified in path and trigger an action whenever a change occurs
	    	  //If a new file is dropped into the folder, the program will call ABBYY Fine Reader to execute OCR
	    	  // and save result to a textfile
	    	  try (WatchService service = FileSystems.getDefault().newWatchService()) {
	  			Map<WatchKey, Path> keyMap = new HashMap<>();
	  			//this is the path of the folder to be monitored
	  			Path path = Paths.get(ocrIn);
	  			//Path path = Paths.get("E:/git/kd/kdgdAPIs/files");
	  			keyMap.put(path.register(service, 
	  					StandardWatchEventKinds.ENTRY_CREATE,
	  					StandardWatchEventKinds.ENTRY_DELETE,
	  					StandardWatchEventKinds.ENTRY_MODIFY
	  					), path);
	  			WatchKey watchKey;
	  			
	  			do {
	  				watchKey = service.take();
	  				Path eventDir = keyMap.get(watchKey);
	  				
	  				for(WatchEvent<?> event : watchKey.pollEvents()) {
	  					WatchEvent.Kind<?> kind = event.kind();
	  					Path eventPath = (Path) event.context();
	  					System.out.println(eventDir + ": "+ kind + " : "+ eventPath);
	  					
	  					//when a new file inserted...
	  					if(kind.toString().equals("ENTRY_CREATE")){
	  						String filename = eventPath.toString();
	  						String input_file_path = ocrIn + "\\"+filename; 
	  						
	  						String output_filename = filename.substring(0, filename.lastIndexOf("."))+".txt";
	  						String output_file_path = ocrOut + "\\"+output_filename;
	  						
	  						String sCmdLine = "cmd /c c: && cd " + abbyyProg + " && finereaderocr.exe "
	  								+ input_file_path 
	  								+ " /lang vietnamese /out " 
	  								+ output_file_path
	  								+ " /quit";
	  						
	  						System.out.println(sCmdLine);
	  						//CommandLine cmdLine = CommandLine.parse("cmd /c c: && cd C:\\Program Files\\ABBYY FineReader 16 && finereaderocr.exe \"E:\\Dá»¯ liá»‡u MC\\Minh chá»©ng CSGD\\Minh chá»©ng CSGD\\Minh chá»©ng tiÃªu chuáº©n 12\\TiÃªu chuáº©n 12. NÃ¢ng cao cháº¥t lÆ°á»£ng\\TiÃªu chÃ­ 12.3\\H12.12.03.01- 12.2017 TT-BGDÄT 19.05.2017 ThÃ´ng tÆ° ban hÃ nh quy Ä‘á»‹nh vá» kiá»ƒm Ä‘á»‹nh cháº¥t lÆ°á»£ng cÆ¡ sá»Ÿ giÃ¡o dá»¥c Ä‘áº¡i há»c.pdf\" /lang vietnamese /out C:\\Users\\sonho\\Desktop\\result.docx /quit");
	  						CommandLine cmdLine = CommandLine.parse(sCmdLine);
	  				        DefaultExecutor executor = new DefaultExecutor();
	  				        int exitValue = executor.execute(cmdLine);
	  					}
	  				}
	  			} while (watchKey.reset());
	  		} catch (Exception e) {
	  			e.printStackTrace();
	  			// TODO: handle exception
	  		}
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


