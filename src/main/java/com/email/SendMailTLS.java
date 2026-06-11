package com.email;

import java.io.IOException;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
 
public class SendMailTLS {

	
	
	public String tlsmain(String to_addr, String subject, String content) {//String[] args) {
		String link = "<a href=\"WWW.google.es\">ACTIVAR CUENTA</a>";
		String lk="";
		
		final String username ="ezkpivn@gmail.com";
		final String password ="@zxcvbnm" ;
 
		Properties props = new Properties();
	
	
	
		props.put("mail.smtp.user",username); 
		props.put("mail.smtp.host", "smtp.gmail.com"); 
		props.put("mail.smtp.port", "25"); 
		props.put("mail.debug", "true"); 
		props.put("mail.smtp.auth", "true"); 
		props.put("mail.smtp.starttls.enable","true"); 
		props.put("mail.smtp.EnableSSL.enable","true");

		props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");   
		props.setProperty("mail.smtp.socketFactory.fallback", "false");   
		props.setProperty("mail.smtp.port", "465");   
		props.setProperty("mail.smtp.socketFactory.port", "465"); 
		
		Authenticator auth = new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		};
	
		Session session = Session.getInstance(props, auth);		
		
		try {
			MimeBodyPart mimeBodyPart=new MimeBodyPart();
		    mimeBodyPart.setContent(lk,"text/html");
		    MimeMultipart multipart=new MimeMultipart();
		    multipart.addBodyPart(mimeBodyPart);
			
			System.out.println("DSend email: To:"+to_addr+" sbj:"+subject+" cnt:"+content);
			
			
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress("ezkpivn@gmail.com"));
			message.setRecipients(Message.RecipientType.TO,	InternetAddress.parse(to_addr));
			
			message.setSubject(subject);
			
			lk="Email from ezKPI\r\n\r\n";
			lk+=content;
			lk+="\r\n CDIT Teams\r\n";
			
			message.setText(lk);
      
			
			Transport.send(message);
			
			System.out.println("Send email ok: "+content);
			
			return null;
		} catch (MessagingException e) {
			
			System.out.println("-c---------");
			System.out.println(e.getMessage());
			System.out.println("----------");
			e.printStackTrace();
			//throw new RuntimeException(e);
			return e.getMessage();
		}
		
	}
	
	
}
