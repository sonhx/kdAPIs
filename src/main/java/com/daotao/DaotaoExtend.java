package com.daotao;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DaotaoExtend {

    @Autowired
    private DaotaoRepository daotaoRepository;

    public String importChiTieu(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            JSONArray ja = jo.getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject json = ja.getJSONObject(i);
                insertChiTieuIntoDB(json.getString("trinh_do"),
                        json.getString("ma_nganh"),
                        json.getString("ten_nganh"), json.getInt("nam_ts"),
                        json.getInt("chi_tieu"), json.getString("co_so"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":" + 800 + ", \"description\":\"" + "Lỗi xử lý JSON" + "\"}";
        }
        return "{\"code\":" + 200 + ", \"description\":\"" + "Thêm mới thành công" + "\"}";
    }
    
    private void insertChiTieuIntoDB(String trinhDo, String maNganh,
            String tenNganh, int namChiTieu, int chiTieu, String co_so) {
        if (!daotaoRepository.existsChiTieu(namChiTieu, maNganh, co_so)) {
            daotaoRepository.insertChiTieu(trinhDo, maNganh, tenNganh, namChiTieu, chiTieu, co_so);
        }
    }
    
    public String importSinhVienTinhThanh(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            JSONArray ja = jo.getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject json = ja.getJSONObject(i);
                importSinhVienTheoTinhThanh(json.getString("ma_tinh_thanh").trim(), 
                        json.getString("ten_tinh_thanh"),
                        json.getInt("nam_hoc"), json.getInt("so_luong"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":" + 800 + ", \"description\":\"" + "Lỗi xử lý JSON" + "\"}";
        }
        return "{\"code\":" + 200 + ", \"description\":\"" + "Thêm mới thành công" + "\"}";
    }
    
    private void importSinhVienTheoTinhThanh(String ma_tinh_thanh,
            String ten_tinh_thanh, int nam_hoc, int so_luong) {
        if (!daotaoRepository.existsSinhVienTinhThanh(nam_hoc, ma_tinh_thanh)) {
            daotaoRepository.insertSinhVienTinhThanh(ma_tinh_thanh, ten_tinh_thanh, nam_hoc, so_luong);
        }
    }
    
    public String importQuiMoTS(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            JSONArray ja = jo.getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject json = ja.getJSONObject(i);
                inSertQuiMoTS(json.getString("he_dao_tao"),
                        json.getInt("nam_ts"), json.getInt("so_luong"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":" + 800 + ", \"description\":\"" + "Lỗi xử lý JSON" + "\"}";
        }
        return "{\"code\":" + 200 + ", \"description\":\"" + "Thêm mới thành công" + "\"}";
    }
    
    private void inSertQuiMoTS(String he_dao_tao, int nam_ts, int so_luong) {
        if (!daotaoRepository.existsQuiMoTS(nam_ts, he_dao_tao)) {
            Integer iHedaotao = daotaoRepository.getHeDaoTaoValue(he_dao_tao);
            if (iHedaotao != null) {
                daotaoRepository.insertQuiMoTS(nam_ts, iHedaotao, so_luong);
            }
        }
    }
    
    public String importNguonLucHV(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            JSONArray ja = jo.getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject json = ja.getJSONObject(i);
                insertNguonLuc(json.getInt("nam_hoc"),
                        json.getString("loai_nguon_luc"),
                        json.getString("ten_nguon_luc"),
                        json.getInt("so_luong"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":" + 800 + ", \"description\":\"" + "Lỗi xử lý JSON" + "\"}";
        }
        return "{\"code\":" + 200 + ", \"description\":\"" + "Thêm mới thành công" + "\"}";
    }
    
    private void insertNguonLuc(int nam_hoc, String loai_nguon_luc,
            String ten_nguon_luc, int so_luong) {
        if (!daotaoRepository.existsNguonLuc(nam_hoc, ten_nguon_luc)) {
            daotaoRepository.insertNguonLuc(nam_hoc, loai_nguon_luc, ten_nguon_luc, so_luong);
        }
    }
}
