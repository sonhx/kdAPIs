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

	private static String defaultUsername = "eiqa.support@gmail.com";
	private static String defaultPassword = "";

	static {
		try {
			java.io.InputStream in = SendMailTLS.class.getClassLoader().getResourceAsStream("application.properties");
			if (in != null) {
				Properties appProps = new Properties();
				appProps.load(in);
				String user = appProps.getProperty("mail.sender.username");
				if (user != null && !user.isBlank()) {
					defaultUsername = user.trim();
				}
				String pwd = appProps.getProperty("mail.sender.password");
				if (pwd != null && !pwd.isBlank()) {
					defaultPassword = pwd.replace(" ", "").trim();
				}
			}
		} catch (Exception e) {
			System.err.println("SendMailTLS: Could not load application.properties: " + e.getMessage());
		}
	}

	public static String getDefaultUsername() {
		return defaultUsername;
	}

	public String tlsmain(String to_addr, String subject, String content) {
		return tlsmain(to_addr, subject, content, defaultUsername, defaultPassword);
	}

	public String tlsmain(String to_addr, String subject, String content, String username, String password) {
		if (username == null || username.isBlank()) {
			username = defaultUsername;
		}
		if (password == null || password.isBlank()) {
			password = defaultPassword;
		}
		if (password != null) {
			password = password.replace(" ", "").trim();
		}

		final String finalUsername = username;
		final String finalPassword = password;

		Properties props = new Properties();
		props.put("mail.smtp.user", finalUsername);
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "465");
		props.put("mail.debug", "false");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.EnableSSL.enable", "true");

		props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		props.setProperty("mail.smtp.socketFactory.fallback", "false");
		props.setProperty("mail.smtp.port", "465");
		props.setProperty("mail.smtp.socketFactory.port", "465");

		Authenticator auth = new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(finalUsername, finalPassword);
			}
		};

		Session session = Session.getInstance(props, auth);

		try {
			System.out.println("SendMailTLS -> Sending from: " + finalUsername + " To: " + to_addr + " Subj: " + subject);

			MimeMessage message = new MimeMessage(session);
			try {
				message.setFrom(new InternetAddress(finalUsername, "Ban ĐBCL Học viện (QA-IMS)", "UTF-8"));
			} catch (Exception ex) {
				message.setFrom(new InternetAddress(finalUsername));
			}
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to_addr));
			message.setSubject(subject, "UTF-8");

			String lk = content + "\r\n\r\n--\r\nBan ĐBCL & TTKT Học viện\r\nHệ thống QA-IMS (Email tự động: " + finalUsername + ")\r\n";
			message.setText(lk, "UTF-8");

			Transport.send(message);

			System.out.println("Send email ok to " + to_addr);
			return null;
		} catch (MessagingException e) {
			System.err.println("SendMailTLS error: " + e.getMessage());
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	
}
