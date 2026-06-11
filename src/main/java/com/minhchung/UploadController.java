package com.minhchung;

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
@RequestMapping("/upload")
public class UploadController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String FILE_UPLOAD_PATH = Config.homeDir + "/csgd/minhchung";

	@PostMapping("/m/{kd_id}/{doituong_kd}/{ma_mc}/{session_id}")
	public ResponseEntity<String> uploadFile(
			@PathVariable("ma_mc") String ma_mc,
			@PathVariable("kd_id") int kd_id,
			@PathVariable("doituong_kd") String doituong_kd,
			@PathVariable("session_id") String session_id,
			@RequestParam("file") MultipartFile file) {

		struct_session sst = sessionService.getSessionInfo(session_id);
		if (sst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Chưa đăng nhập");

		if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");

		try {
			String itemName = file.getOriginalFilename();
			String[] parts = ma_mc.split("\\.");
			String path = FILE_UPLOAD_PATH + "/" + parts[0] + "/" + parts[2];

			File destFile = new File(path + File.separator + itemName);
			if (!destFile.getParentFile().exists()) destFile.getParentFile().mkdirs();
			file.transferTo(destFile);

			registerMinhchung(ma_mc, itemName, parts, sst.UserID, kd_id, doituong_kd);
			return ResponseEntity.ok("File uploaded successfully");
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}

	public int registerMinhchung(String ma_mc, String itemName, String[] parts, int UserID, int kd_id, String doituong_kd) {
		String chuong_trinh = doituong_kd.equals("cs") ? "/csgd" : "/ctdt/" + doituong_kd;
		String path = "/kdgd/doc/" + kd_id + "/" + chuong_trinh + "/mc/" + parts[0] + "/" + parts[2] + "/" + ma_mc + "- " + itemName;
		String sql = "insert into TBL_Minhchung (tieu_chuan, tieu_chi, ma_mc, ten_mc, path, Createdtime, CreatedBy, kd_id, doituong_kd) "
				+ " values (?,?,?,?,GETDATE(),?,?,?)";
		return jdbcTemplate.update(sql, parts[0], parts[1] + "." + parts[2], ma_mc, ma_mc + "- " + itemName, UserID, kd_id, doituong_kd);
	}

	@PostMapping("/vb_den/{session_id}")
	public ResponseEntity<String> uploadVBden(
			@PathVariable("session_id") String session_id,
			@RequestParam("file") MultipartFile file,
			@RequestParam("id_tochuc") int id_tochuc,
			@RequestParam("ngay_gui") String ngay_gui,
			@RequestParam("ngay_nhan") String ngay_nhan,
			@RequestParam("ghi_chu") String ghi_chu,
			@RequestParam("kd_id") int kd_id,
			@RequestParam("doituong_kd") String doituong_kd) {

		struct_session sst = sessionService.getSessionInfo(session_id);
		if (sst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Chưa đăng nhập");

		if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");

		try {
			String itemName = file.getOriginalFilename();
			String path = Config.homeDir + "/vb/vbden";
			File destFile = new File(path + File.separator + itemName);
			if (!destFile.getParentFile().exists()) destFile.getParentFile().mkdirs();
			file.transferTo(destFile);

			registerVBden(itemName, id_tochuc, ngay_gui, ngay_nhan, ghi_chu, sst.UserID, kd_id, doituong_kd);
			return ResponseEntity.ok("File uploaded successfully");
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}

	public int registerVBden(String ten, int id_tc, String ngay_gui, String ngay_nhan, String ghi_chu, int UserID, int kd_id, String doituong_kd) {
		String doituong_kd_path = doituong_kd.equals("cs") ? "csdg" : "ctdt/" + doituong_kd;
		String path = "/kdgd/doc/" + kd_id + "/" + doituong_kd_path + "/vb/vbden/" + ten;
		String sql = "insert into TBL_Vanbanden (ten, id_tochuc, ngay_gui, ngay_nhan, ghi_chu, path, Createdtime, CreatedBy, kd_id, doituong_kd) "
				+ " values (?,?,?,?,?,?,GETDATE(),?,?,?)";
		return jdbcTemplate.update(sql, ten, id_tc, ngay_gui, ngay_nhan, ghi_chu, path, UserID, kd_id, doituong_kd);
	}

	@PostMapping("/vb_di/{session_id}")
	public ResponseEntity<String> uploadVBdi(
			@PathVariable("session_id") String session_id,
			@RequestParam("file") MultipartFile file,
			@RequestParam("id_tochuc") int id_tochuc,
			@RequestParam("ngay_gui") String ngay_gui,
			@RequestParam("ghi_chu") String ghi_chu,
			@RequestParam("kd_id") int kd_id,
			@RequestParam("doituong_kd") String doituong_kd) {

		struct_session sst = sessionService.getSessionInfo(session_id);
		if (sst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Chưa đăng nhập");

		if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty");

		try {
			String itemName = file.getOriginalFilename();
			String path = Config.homeDir + "/vb/vbdi";
			File destFile = new File(path + File.separator + itemName);
			if (!destFile.getParentFile().exists()) destFile.getParentFile().mkdirs();
			file.transferTo(destFile);

			registerVBdi(itemName, id_tochuc, ngay_gui, ghi_chu, sst.UserID, kd_id, doituong_kd);
			return ResponseEntity.ok("File uploaded successfully");
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}

	public int registerVBdi(String ten, int id_tc, String ngay_gui, String ghi_chu, int UserID, int kd_id, String doituong_kd) {
		String doituong_kd_path = doituong_kd.equals("cs") ? "csdg" : "ctdt/" + doituong_kd;
		String path = "/kdgd/doc/" + kd_id + "/" + doituong_kd_path + "/vb/vbdi/" + ten;
		String sql = "insert into TBL_Vanbandi (ten, id_tochuc, ngay_gui, ghi_chu, path, Createdtime, CreatedBy, kd_id, doituong_kd) "
				+ " values (?,?,?,?,?,GETDATE(),?,?,?)";
		return jdbcTemplate.update(sql, ten, id_tc, ngay_gui, ghi_chu, path, UserID, kd_id, doituong_kd);
	}
}
