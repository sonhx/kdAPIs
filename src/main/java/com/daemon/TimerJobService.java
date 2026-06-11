package com.daemon;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TimerJobService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int counter = 0;

    @Scheduled(fixedRate = 60000)
    public void runJob() {
        // Logic from legacy timerjob.java was mostly commented out.
        // Keeping the structure here for future implementation.
        counter++;
        // inputDataDeadlineScan();
    }

    private int isGateOpen(int bd, int bm, int ed, int em) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date currentTime = new Date();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(currentTime);
        int currentYear = cal.get(java.util.Calendar.YEAR);

        String strBeginTime = String.format("%d-%02d-%02d 00:00:00.000", currentYear, bm, bd);
        String strEndTime = String.format("%d-%02d-%02d 00:00:00.000", currentYear, em, ed);

        try {
            Date beginTime = dateFormat.parse(strBeginTime);
            Date endTime = dateFormat.parse(strEndTime);
            if (currentTime.after(endTime))
                return -1;
            else if (currentTime.before(endTime) && currentTime.after(beginTime))
                return 1;
            else
                return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
