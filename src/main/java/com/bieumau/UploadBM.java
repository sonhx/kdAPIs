package com.bieumau;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.config.Config;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/bm_upload")
public class UploadBM {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PostMapping("/{session_id}")
	public ResponseEntity<String> uploadBM(
			@PathVariable("session_id") String session_id,
			@RequestParam("file") MultipartFile file,
			@RequestParam("ghi_chu") String ghi_chu,
			@RequestParam("kd_id") int kd_id,
			@RequestParam("doituong_kd") String doituong_kd) {

		struct_session sst = sessionService.getSessionInfo(session_id);
		if (sst == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Chưa đăng nhập");
		}

		if (file.isEmpty()) {
			return ResponseEntity.badRequest().body("File is empty");
		}

		try {
			String itemName = file.getOriginalFilename();
			String path = Config.homeDir + "/bm";
			File destFile = new File(path + File.separator + itemName);
			if (!destFile.getParentFile().exists()) {
				destFile.getParentFile().mkdirs();
			}
			file.transferTo(destFile);

			registerBM(itemName, ghi_chu, sst.UserID, kd_id, doituong_kd);
			return ResponseEntity.ok("File uploaded successfully");
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}

	public int registerBM(String ten_bm, String ghi_chu, int UserID, int kd_id, String doituong_kd) {
		String path = "/kdgd/doc/bm/" + ten_bm;
		String sql = "insert into TBL_Bieumau (ten, ghi_chu, path, Createdtime, CreatedBy, kd_id, doituong_kd) "
				+ " values (?, ?, ?, GETDATE(), ?, ?, ?)";
		return jdbcTemplate.update(sql, ten_bm, ghi_chu, path, UserID, kd_id, doituong_kd);
	}
}
