package com.ptit.qa.ims.controller;

import com.ptit.qa.ims.entity.TrainingProgram;
import com.ptit.qa.ims.repository.CourseRepository;
import com.ptit.qa.ims.repository.TrainingProgramRepository;
import com.ptit.qa.ims.service.CourseCrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crawler/courses")
public class CourseCrawlerController {

    @Autowired
    private CourseCrawlerService courseCrawlerService;

    @Autowired
    private TrainingProgramRepository trainingProgramRepository;

    @Autowired
    private CourseRepository courseRepository;

    @RequestMapping(value = "/run", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> triggerCrawler() {
        Map<String, Object> result = courseCrawlerService.crawlAndSaveWithStats();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        int programCount = trainingProgramRepository.count();
        int courseCount = courseRepository.count();

        stats.put("totalTrainingPrograms", programCount);
        stats.put("totalCourses", courseCount);
        stats.put("status", "READY");

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/programs")
    public ResponseEntity<List<TrainingProgram>> getAllPrograms() {
        List<TrainingProgram> list = trainingProgramRepository.findAll();
        return ResponseEntity.ok(list);
    }
}
