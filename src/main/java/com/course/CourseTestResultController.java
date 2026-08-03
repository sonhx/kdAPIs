package com.course;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/course-test-results", "/course-test"})
public class CourseTestResultController {

    private static final Logger log = LoggerFactory.getLogger(CourseTestResultController.class);

    @Autowired
    private CourseTestResultService courseTestResultService;

    /**
     * Upload CSV test result file.
     * Params:
     * - file: MultipartFile (.csv)
     * - semester: optional semester identifier (e.g. "HK2 2025-2026", "20251")
     */
    @PostMapping(value = {"/upload", "/upload-file"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadCourseTestResults(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "semester", required = false) String semester) {

        JSONObject result = new JSONObject();
        if (file == null || file.isEmpty()) {
            result.put("status", "ERROR");
            result.put("message", "File is empty or not provided.");
            return ResponseEntity.badRequest().body(result.toString());
        }

        try {
            log.info("Processing uploaded test results file: {}, term parameter: {}", file.getOriginalFilename(), semester);
            int count = courseTestResultService.saveUploadedFile(file.getInputStream(), file.getOriginalFilename(), semester);

            result.put("status", "SUCCESS");
            result.put("message", "Successfully imported " + count + " course test result records.");
            result.put("recordCount", count);
            result.put("semester", semester);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error importing test result file", e);
            result.put("status", "ERROR");
            result.put("message", "Failed to process uploaded file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.toString());
        }
    }

    /**
     * Get stats data for Course Quality Risk table.
     * Params:
     * - semester: optional semester filter (e.g. "HK2 2025-2026", "20251", or "ALL")
     */
    @RequestMapping(value = {"/stats", "/list"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCourseRiskStats(
            @RequestParam(value = "semester", required = false) String semester) {

        List<Map<String, Object>> stats = courseTestResultService.getCourseRiskStats(semester);
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("data", stats);
        result.put("total", stats.size());
        result.put("selectedSemester", semester != null ? semester : "ALL");
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Get list of available semesters/terms present in the database.
     */
    @RequestMapping(value = "/semesters", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAvailableSemesters() {
        List<String> semesters = courseTestResultService.getAvailableSemesters();
        JSONObject result = new JSONObject();
        result.put("status", "SUCCESS");
        result.put("semesters", semesters);
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Download sample CSV template file.
     */
    @GetMapping(value = {"/sample-file", "/sample"}, produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> downloadSampleCsv() {
        String csvContent = "THỐNG KÊ ĐIỂM HỌC PHẦN\n" +
                "Điểm thi học kỳ 20251\n" +
                "\n" +
                "TT,Mã HP,Tên học phần,Số TC,Tổng số,Số có điểm,[0; 1],[1; 2],[2; 3],[3; 4],[4; 5],[5; 6],[6; 7],[7; 8],[8; 9],[9; 10],Cấm thi (C),Vắng (V),Hoãn (H),Đình chỉ (DC),Miễn (M),Số trượt (F),Tỷ lệ trượt (%),Điểm TB\n" +
                "1,INT1341,Trí tuệ nhân tạo,3,185,185,5,12,18,25,30,35,28,20,9,3,0,0,0,0,0,60,32.4,4.8\n" +
                "2,INT1340,Cấu trúc dữ liệu và Giải thuật,3,220,220,6,15,20,22,35,42,38,25,12,5,0,0,0,0,0,63,28.6,5.1\n" +
                "3,ELE1322,Xử lý tín hiệu số,3,95,95,2,5,7,9,18,20,16,11,5,2,0,0,0,0,0,23,24.2,5.3\n";

        byte[] bytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "TK_SV_theo_pho_diem_thi_Mau.csv");

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
