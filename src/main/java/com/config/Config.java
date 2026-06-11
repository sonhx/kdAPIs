package com.config;

public class Config {
	public static String DB = "jdbc/KD";
	public static final String host = "https://quiz-lab.ptit.edu.vn";
	public static final String homeDir = "C:/kdgd";
	public static final String homePath = "/kdgd/doc";
	
	public static void main(String[] args) {
		String ma_mc = "H1.01.01.01.";
		ma_mc = ma_mc.substring(0, ma_mc.length()-1);
		System.out.println(ma_mc);
		String s = ma_mc.replaceAll("\\.", "/");
//		System.out.println(s);
		
		String ma_mc_reduced = ma_mc.substring(0, ma_mc.lastIndexOf("."));
		System.out.println(ma_mc_reduced);
	}
}
