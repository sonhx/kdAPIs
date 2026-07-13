package com.surveys;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SurveyTestController {

    @Autowired
    private SurveySyncScheduler surveySyncScheduler;

    @GetMapping("/test-sync")
    public String triggerSync() {
        // Chạy đồng bộ trong một luồng nền để không chặn HTTP Request
        new Thread(() -> {
            System.out.println("Manual trigger: Starting syncSurveys...");
            surveySyncScheduler.syncSurveys();
            
            System.out.println("Manual trigger: Starting syncOngoingSurveyDetails...");
            surveySyncScheduler.syncOngoingSurveyDetails();
            
            System.out.println("Manual trigger: Completed!");
        }).start();
        
        return "Tiến trình đồng bộ khảo sát đã được kích hoạt chạy ngầm (Background Task). Vui lòng kiểm tra Console (Log) để xem tiến độ!";
    }
}
