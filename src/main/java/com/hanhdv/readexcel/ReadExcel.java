package com.hanhdv.readexcel;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//import com.tnth.jdbc.ConnectionUtils;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public class ReadExcel {
	//public static final String input_file = "E:\\PTIT Plan Management\\KehoachTemplate.xls";

	public static void ReadExcelAndInsertIntoDB(String path) {
		File inputWorkbook = new File(path);

		
		Workbook w;

		String cap_1 = "", cap_2 = "", cap_3 = "", cap_4 = "", cap_5 = "", ten_nganh;

		try {
			w = Workbook.getWorkbook(inputWorkbook);
			Sheet sheet = w.getSheet(0);

			int kpi_id = 0;
			int parent_id_1 = 0, parent_id_2 = 0, parent_id_3 = 0, parent_id_4 = 0, parent_id_5 = 0;
			String parentCode_1="", parentCode_2="",parentCode_3="", parentCode_4="", parentCode_5="";
			int parent_id;
			String dien_giai="";
			for (int i = 1; i < sheet.getRows(); i++) {
				Cell cell_index = sheet.getCell(0, i);
				cap_1 = cell_index.getContents();

				Cell cell_code = sheet.getCell(1, i);
				cap_2 = cell_code.getContents();

				Cell cell_name = sheet.getCell(2, i);
				cap_3 = cell_name.getContents();

				Cell cell_desc = sheet.getCell(3, i);
				cap_4 = cell_desc.getContents();

				Cell cell_unit = sheet.getCell(4, i);
				cap_5 = cell_unit.getContents();

				Cell cell_cycle = sheet.getCell(5, i);
				ten_nganh = cell_cycle.getContents();
				
				Cell cell_dien_giai= sheet.getCell(6,i);
				dien_giai= cell_dien_giai.getContents();
				
				
				System.out.println(cap_1 + "---" + cap_2 + "--" + cap_3 + "--"
						+ cap_4 + "--" + cap_5 + "--" + ten_nganh+ "--"+ dien_giai);
				String ma_nganh;
				

//				if (!cap_1.isEmpty()) {
//					ma_nganh = cap_1;
//					parent_id = 0;
//					int level = 1;
//					parent_id_1 = GetIDFromInsertLevel(ma_nganh, ten_nganh,
//							parent_id, level,dien_giai);
//					parentCode_1=ma_nganh;
//					
//				}
//				if (!cap_2.isEmpty()) {
//					ma_nganh =parentCode_1 +"."+ cap_2;
//					parent_id = parent_id_1;
//					int level = 2;
//					parent_id_2 = GetIDFromInsertLevel(ma_nganh, ten_nganh,
//							parent_id, level,dien_giai);
//					parentCode_2= ma_nganh;
//					
//				}
//				if (!cap_3.isEmpty()) {
//					ma_nganh = parentCode_2+"."+cap_3;
//					parent_id = parent_id_2;
//					int level = 3;
//					parent_id_3 = GetIDFromInsertLevel(ma_nganh, ten_nganh,
//							parent_id, level,dien_giai);
//					parentCode_3= ma_nganh;
//				}
//				if (!cap_4.isEmpty()) {
//					ma_nganh = parentCode_3+"."+cap_4;
//					parent_id = parent_id_3;
//					int level = 4;
//					parent_id_4 = GetIDFromInsertLevel(ma_nganh, ten_nganh,
//							parent_id, level,dien_giai);
//					parentCode_4= ma_nganh;
//				}
//				if (!cap_5.isEmpty()) {
//					ma_nganh = parentCode_4+"."+cap_5;
//					parent_id = parent_id_4;
//					int level = 5;
//					parent_id_5 = GetIDFromInsertLevel(ma_nganh, ten_nganh,
//							parent_id, level,dien_giai);
//					parentCode_5= ma_nganh;
//				}

			}
		} catch (BiffException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

//	public static int GetIDFromInsertLevel(String ma_nganh, String ten_nganh,
//			int parent_id, int level, String dien_giai) {
//		int id = 0;
//		try {
//
//			Connection conn = ConnectionUtils.getMyConnection();
//			String generatedColumns[] = { "id" };
//			String insertSQL = "Insert into TBL_PLAN_TEMPLATE(so_thu_tu,noi_dung,parent_id,level_from_root,dien_giai) values(N'"
//					+ ma_nganh
//					+ "' ,N'"
//					+ ten_nganh
//					+ "',"
//					+ parent_id
//					+ ","
//					+ level + ",N'"+ dien_giai+"')";
//
//			PreparedStatement stmtInsert = conn.prepareStatement(insertSQL,
//					generatedColumns);
//			stmtInsert.executeUpdate();
//			ResultSet rs = stmtInsert.getGeneratedKeys();
//			if (rs.next()) {
//				id = rs.getInt(1);
//				System.out.println("Inserted ID -" + id); // display inserted
//															// record
//			}
//			rs.close();
//			stmtInsert.close();
//			conn.close();
//		} catch (ClassNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (SQLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		return id;
//	}

	public static void main(String[] args) {
		//ReadExcelAndInsertIntoDB(input_file);

	}
}
