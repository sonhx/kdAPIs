package com.hanhdv.readexcel;

import java.io.File;
import java.io.FileInputStream;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReadXLSXFile {

    @Autowired
    private ImportExcelData importExcelData;

    public void ReadExcelAndInsertIntoDB(String path, int org_id, int user_id) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 2; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                String cmnd = formatter.formatCellValue(row.getCell(1));
                String ten = formatter.formatCellValue(row.getCell(2));
                String hoc_ham = formatter.formatCellValue(row.getCell(3));
                String hoc_vi = formatter.formatCellValue(row.getCell(4));
                String email = formatter.formatCellValue(row.getCell(5));
                String ngay_sinh = formatter.formatCellValue(row.getCell(6));
                String gioi_tinh = formatter.formatCellValue(row.getCell(7));
                String chuc_vu = formatter.formatCellValue(row.getCell(8));

                importExcelData.insertRow(cmnd, ten, hoc_ham, hoc_vi, email, ngay_sinh, gioi_tinh, chuc_vu);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ReadExcelAndInsertIntoDB_2(String path, int org_id, int user_id) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 2; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String ten = formatter.formatCellValue(row.getCell(1));
                String email = formatter.formatCellValue(row.getCell(2));
                String don_vi = formatter.formatCellValue(row.getCell(3));
                String chuc_vu = formatter.formatCellValue(row.getCell(4));

                importExcelData.insertRow_2(ten, email, don_vi, chuc_vu);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ReadExcelAndInsertIntoDB_CSVC_2(String path, int org_id, int user_id) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 2; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String nganh = formatter.formatCellValue(row.getCell(1));
                String dau_sach = formatter.formatCellValue(row.getCell(2));
                String ban_sach = formatter.formatCellValue(row.getCell(3));

                importExcelData.insertRow_CSVC_2(nganh, dau_sach, ban_sach);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ReadExcelAndInsertIntoDB_CSVC_3(String path, int org_id, int user_id) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 2; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String ten = formatter.formatCellValue(row.getCell(1));
                String dien_tich = formatter.formatCellValue(row.getCell(2));
                String hthuc_sdung = formatter.formatCellValue(row.getCell(3));

                importExcelData.insertRow_CSVC_3(ten, dien_tich, hthuc_sdung);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ReadExcelAndInsertIntoDB_CSVC_4(String path, int org_id, int user_id) {
        try (FileInputStream fis = new FileInputStream(new File(path))) {
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 2; i < sheet.getPhysicalNumberOfRows(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String ten_tbi = formatter.formatCellValue(row.getCell(1));
                String ten_phong = formatter.formatCellValue(row.getCell(2));
                String dtg_sd = formatter.formatCellValue(row.getCell(3));
                String so_lg = formatter.formatCellValue(row.getCell(4));

                importExcelData.insertRow_CSVC_4(ten_tbi, ten_phong, dtg_sd, so_lg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
