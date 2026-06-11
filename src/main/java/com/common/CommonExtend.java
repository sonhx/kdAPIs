package com.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

public class CommonExtend {
	
	/**
	 * return list of files in folder and its children and children of children
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static Set<String> listFilesUsingFileWalkAndVisitor(String dir) throws IOException {
	    final Set<String> fileList = new HashSet<>();
	    Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
	        @Override
	        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
	            if (!Files.isDirectory(file)) {
	                fileList.add(file.getFileName().toString());
	            }
	            return FileVisitResult.CONTINUE;
	        }
	    });
	    return fileList;
	}
	
	/**
	 * return a list of files including files and sub-folders, 
	 * to check if is file or folder, use isFile() and isDirectory()
	 * @param sPath
	 * @return
	 */
	public File[] listFiles(String sPath){
		File folder = new File(sPath);
		File[] listOfFiles = folder.listFiles();

		/*for (int i = 0; i < listOfFiles.length; i++) {
		  if (listOfFiles[i].isFile()) {
		    System.out.println("File " + listOfFiles[i].getName());
		  } else if (listOfFiles[i].isDirectory()) {
		    System.out.println("Directory " + listOfFiles[i].getName());
		  }
		}*/
		
		return listOfFiles;
	}
	
	public static void main(String[] args) throws IOException {
		Set<String> listFile = listFilesUsingFileWalkAndVisitor("E:/Dữ liệu MC/Minh chứng CSGD/Minh chứng CSGD/Minh chứng tiêu chuẩn 1/Tiêu chẩn 1. Tầm nhìn Sứ mạng và Văn hoá/TC 1.1");
		
		for(String s:listFile){
			System.out.println(s);
		}
	}

}
