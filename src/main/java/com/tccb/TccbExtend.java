package com.tccb;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TccbExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String getOrgName(int orgId) {
        try {
            return jdbcTemplate.queryForObject("select Name from tbl_org where ID=?", String.class, orgId);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getUserFullName(int userId) {
        try {
            return jdbcTemplate.queryForObject("select Fullname from tbl_user where ID=?", String.class, userId);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getPersonName(int personId) {
        try {
            return jdbcTemplate.queryForObject("select TenDayDu from tbl_cbcnv where ID=? and (IsDeleted is null or IsDeleted=0)", String.class, personId);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getHocHamName(int value) {
        try {
            return jdbcTemplate.queryForObject("select Name from DEF_HOCHAM where Value=?", String.class, value);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getTrinhDoName(int value) {
        try {
            return jdbcTemplate.queryForObject("select Name from DEF_TRINHDO where Value=?", String.class, value);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getStateName(int value) {
        if (value == 0) return "đang làm việc";
        try {
            return jdbcTemplate.queryForObject("select Name from DEF_CBCNV_STATE where Value=?", String.class, value);
        } catch (Exception e) {
            return "";
        }
    }

    public String getNgachName(int value) {
        try {
            return jdbcTemplate.queryForObject("select Name from DEF_NGACH where Value=?", String.class, value);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getNgach1Name(int value) {
        try {
            return jdbcTemplate.queryForObject("select Name from DEF_NGACH1 where Value=?", String.class, value);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
