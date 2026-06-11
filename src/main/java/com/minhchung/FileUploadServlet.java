package com.minhchung;

import java.io.File;
import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/fileuploadservlet")
public class FileUploadServlet {

	@PostMapping
	public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return ResponseEntity.badRequest().body("File is empty");
		}

		String fileName = file.getOriginalFilename();
		try {
			File destFile = new File("C:/upload/" + fileName);
			if (!destFile.getParentFile().exists()) {
				destFile.getParentFile().mkdirs();
			}
			file.transferTo(destFile);
			return ResponseEntity.ok("Sucessfully Java file upload.");
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMessage());
		}
	}
}
