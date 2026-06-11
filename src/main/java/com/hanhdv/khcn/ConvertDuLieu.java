package com.hanhdv.khcn;

import java.io.File;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

@Service
public class ConvertDuLieu {

	@Autowired
	private BaiBaoDAO baiBaoDAO;

	@Autowired
	private BaoCaoDAO baoCaoDAO;

	public String ConvertDuLieuBaiBao(String sPathFile) {
		File inputWorkbook = new File(sPathFile);
		Workbook w;
		try {
			w = Workbook.getWorkbook(inputWorkbook);
			Sheet sheet = w.getSheet(0);

			String issn = "", ten_bai_bao, ten_tap_chi, nam_xuat_ban;

			int loai_tap_chi;
			int bai_bao_id = 0;
			for (int i = 3; i < sheet.getRows(); i++) {

				ten_bai_bao = sheet.getCell(1, i).getContents();
				ten_tap_chi = sheet.getCell(3, i).getContents();
				nam_xuat_ban = sheet.getCell(4, i).getContents();

				String tap_chi_quoc_te = sheet.getCell(7, i).getContents();

				String tap_chi_isi = sheet.getCell(13, i).getContents();
				String tap_chi_scopus = sheet.getCell(14, i).getContents();

				String tap_chi_trong_nuoc = sheet.getCell(8, i).getContents();
				String tap_chi_cap_truong = sheet.getCell(9, i).getContents();
				if (!tap_chi_quoc_te.isEmpty()) {
					if (!tap_chi_isi.isEmpty())
						loai_tap_chi = 2;
					else if (!tap_chi_scopus.isEmpty())
						loai_tap_chi = 3;
					else
						loai_tap_chi = 4;
				} else if (!tap_chi_trong_nuoc.isEmpty())
					loai_tap_chi = 5;
				else if (!tap_chi_cap_truong.isEmpty())
					loai_tap_chi = 6;
				else
					continue;

				String thong_tin_them = "";

				if (!ten_bai_bao.isEmpty()) {
					bai_bao_id = baiBaoDAO.getIDFromInsertBaiBao(issn,
							ten_bai_bao, loai_tap_chi, ten_tap_chi,
							nam_xuat_ban, thong_tin_them);

					String tac_gia = sheet.getCell(2, i).getContents();
					if (bai_bao_id > 0 && !tac_gia.isEmpty()) {
						String[] lst_tac_gia = tac_gia.split(",");
						for (String ho_ten : lst_tac_gia) {
							if (!ho_ten.isEmpty())
								baiBaoDAO.insertMemBaiBao(ho_ten, "", 1, 0,
										bai_bao_id);
						}
					}
				}

			}
		} catch (BiffException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}

	public String ConvertDuLieuBaoCao(String sPathFile) {
		File inputWorkbook = new File(sPathFile);
		Workbook w;
		try {
			w = Workbook.getWorkbook(inputWorkbook);
			Sheet sheet = w.getSheet(0);
			for (int i = 3; i < sheet.getRows(); i++) {

				String ten_bao_cao = sheet.getCell(1, i).getContents();
				String ten_hoi_thao = sheet.getCell(3, i).getContents();
				String sNamBaoCao = sheet.getCell(4, i).getContents();
				int nam_bao_cao = 0;
				if (!sNamBaoCao.isEmpty())
					try {
						nam_bao_cao = Integer.parseInt(sNamBaoCao);
					} catch (Exception e) {
						continue;
					}
				String hoi_thao_quoc_te = sheet.getCell(10, i).getContents();
				String hoi_thao_trong_nuoc = sheet.getCell(11, i).getContents();
				String hoi_thao_truong = sheet.getCell(12, i).getContents();
				int loai_hoi_thao;
				if (!hoi_thao_quoc_te.isEmpty()) {
					loai_hoi_thao = 1;
				} else if (!hoi_thao_trong_nuoc.isEmpty())
					loai_hoi_thao = 2;
				else if (!hoi_thao_truong.isEmpty())
					loai_hoi_thao = 3;
				else
					continue;

				String thong_tin_them = "";
				int bao_cao_id = 0;
				if (!ten_bao_cao.isEmpty() && !sNamBaoCao.isEmpty()) {
					bao_cao_id = baoCaoDAO.getIDFromInsertBaoCao(ten_bao_cao,
							ten_hoi_thao, loai_hoi_thao, nam_bao_cao,
							thong_tin_them);

					String tac_gia = sheet.getCell(2, i).getContents();
					if (bao_cao_id > 0 && !tac_gia.isEmpty()) {
						String[] lst_tac_gia = tac_gia.split(",");
						for (String ho_ten : lst_tac_gia) {
							if (!ho_ten.isEmpty())
								baoCaoDAO.insertMemBaoCao(ho_ten, "", 1, 0,
										bao_cao_id);
						}
					}
				}

			}
		} catch (BiffException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}
}
