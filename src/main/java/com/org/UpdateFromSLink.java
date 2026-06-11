package com.org;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class UpdateFromSLink {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    @Scheduled(cron = "0 0 4 * * *") // Run at 4:00 AM every day
    public void syncOrgData() {
        try {
            // Get the last processed updatedTime from TBL_ORG
            String lastProcessedTime = getLastProcessedTime();

            // Fetch new data from S-Link where updatedTime > lastProcessedTime
            List<Map<String, Object>> newData = fetchFromSLink(lastProcessedTime);

            for (Map<String, Object> row : newData) {
                updateOrg(row);
            }

        } catch (Exception e) {
            // Log error, but since no logger, perhaps System.out
            System.out.println("Error in syncOrgData: " + e.getMessage());
        }
    }

    private String getLastProcessedTime() {
        // Get the maximum UpdatedTime from TBL_ORG
        String sql = "SELECT MAX(UpdatedTime) FROM TBL_ORG WHERE UpdatedTime IS NOT NULL";
        String maxTime = jdbcTemplate.queryForObject(sql, String.class);
        if (maxTime == null) {
            // Default start time if no data
            return "1900/01/01 00:00:00";
        }
        return maxTime;
    }

    private List<Map<String, Object>> fetchFromSLink(String lastTime) {
        // Placeholder for fetching data from S-Link
        // This should connect to S-Link and fetch rows where updatedTime > lastTime
        // For now, return empty list
        // TODO: Implement actual connection to S-Link
        return List.of(); // Return empty list as placeholder
    }

    private void updateOrg(Map<String, Object> row) {
        Integer id = (Integer) row.get("id");
        if (id == null) return;

        // Check if org exists
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TBL_ORG WHERE ID = ?", Integer.class, id);
        String currentTime = dateFormat.format(new Date());

        if (count != null && count > 0) {
            // Update existing
            String updateSql = "UPDATE TBL_ORG SET Name = ?, Url = ?, Logo = ?, Hotline = ?, Email = ?, OrderID = ?, ParentID = ?, UpdatedTime = ? WHERE ID = ?";
            jdbcTemplate.update(updateSql,
                row.get("name"),//TODO change the actual params accordingly
                row.get("url"),//TODO ditto
                row.get("logo"),
                row.get("hotline"),
                row.get("email"),
                row.get("order_id"),
                row.get("parent_id"),
                row.get("updatedTime"),
                id);
        } else {
            // Insert new
            String insertSql = "INSERT INTO TBL_ORG (ID, Name, Url, Logo, Hotline, Email, OrderID, ParentID, CreatedTime, UpdatedTime, IsDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
            jdbcTemplate.update(insertSql,
                id,
                row.get("name"),//TODO ditto
                row.get("url"),
                row.get("logo"),
                row.get("hotline"),
                row.get("email"),
                row.get("order_id"),
                row.get("parent_id"),
                currentTime,
                row.get("updatedTime"));
        }
    }
}

