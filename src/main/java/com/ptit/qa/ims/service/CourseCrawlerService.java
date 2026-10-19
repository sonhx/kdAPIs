package com.ptit.qa.ims.service;

import com.ptit.qa.ims.entity.Course;
import com.ptit.qa.ims.entity.TrainingProgram;
import com.ptit.qa.ims.repository.CourseRepository;
import com.ptit.qa.ims.repository.TrainingProgramRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CourseCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CourseCrawlerService.class);

    @Autowired
    private TrainingProgramRepository trainingProgramRepository;

    @Autowired
    private CourseRepository courseRepository;

    private static final String BASE_URL = "https://daotao.ptit.edu.vn/chuong-trinh-dao-tao/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 25000;

    private static final Pattern CREDIT_PATTERN = Pattern.compile("(\\d+)\\s*(tín chỉ|tc|credit|credits)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern CODE_AND_NAME_PATTERN = Pattern.compile("^[\\[\\(]?([A-Z0-9]{3,15})[\\]\\)]?\\s*[-:]\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    public void crawlAndSaveProgramsAndCourses() {
        crawlAndSaveWithStats();
    }

    public Map<String, Object> crawlAndSaveWithStats() {
        long startTime = System.currentTimeMillis();
        log.info("[Crawler] Bắt đầu tiến trình cào dữ liệu chương trình đào tạo & học phần từ {}", BASE_URL);

        int currentPage = 1;
        int totalProgramsProcessed = 0;
        int totalCoursesSaved = 0;
        List<String> errors = new ArrayList<>();

        while (true) {
            String pageUrl = (currentPage == 1) 
                    ? BASE_URL 
                    : BASE_URL + "page/" + currentPage + "/";

            log.info("[Crawler] Đang tải danh sách trang #{} ({})", currentPage, pageUrl);

            Document listDoc;
            try {
                listDoc = Jsoup.connect(pageUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .referrer("https://daotao.ptit.edu.vn/")
                        .get();
            } catch (IOException e) {
                log.warn("[Crawler] Không thể tải trang {} (có thể đã hết trang): {}", pageUrl, e.getMessage());
                break;
            }

            Elements postTitles = listDoc.select("h2.post-title a");
            if (postTitles.isEmpty()) {
                log.info("[Crawler] Không còn chương trình nào ở trang #{}. Kết thúc quét trang.", currentPage);
                break;
            }

            for (Element aTag : postTitles) {
                String programName = aTag.text().trim();
                String programUrl = aTag.absUrl("href");

                if (programUrl.isEmpty() || programUrl.endsWith("/feed/")) {
                    continue;
                }

                try {
                    int coursesCount = processSingleProgram(programName, programUrl);
                    totalCoursesSaved += coursesCount;
                    totalProgramsProcessed++;

                    Thread.sleep(400);
                } catch (Exception ex) {
                    String err = String.format("Lỗi khi cào CTĐT '%s' (%s): %s", programName, programUrl, ex.getMessage());
                    log.error("[Crawler] {}", err, ex);
                    errors.add(err);
                }
            }

            Element nextButton = listDoc.selectFirst("a.next.page-numbers");
            if (nextButton == null) {
                log.info("[Crawler] Đã quét đến trang cuối cùng (#{})", currentPage);
                break;
            }

            currentPage++;
        }

        long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
        log.info("[Crawler] Hoàn tất quét toàn bộ! Số CTĐT: {}, Số học phần: {}, Thời gian: {}s",
                totalProgramsProcessed, totalCoursesSaved, durationSeconds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("totalPagesScanned", currentPage);
        result.put("programsProcessed", totalProgramsProcessed);
        result.put("coursesSaved", totalCoursesSaved);
        result.put("durationSeconds", durationSeconds);
        result.put("errorsCount", errors.size());
        result.put("errors", errors);
        result.put("completedAt", LocalDateTime.now().toString());

        return result;
    }

    @Transactional
    public int processSingleProgram(String programName, String programUrl) {
        log.info("[Crawler] Đang cào trang chi tiết: '{}' ({})", programName, programUrl);

        Document detailDoc;
        try {
            detailDoc = Jsoup.connect(programUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .referrer(BASE_URL)
                    .get();
        } catch (IOException e) {
            log.error("[Crawler] Không thể kết nối tới CTĐT: {}", programUrl, e);
            return 0;
        }

        Element h1Title = detailDoc.selectFirst("h1.breadcrumb-title");
        String finalName = (h1Title != null && !h1Title.text().isBlank()) 
                ? h1Title.text().trim() 
                : programName;

        String programCode = extractProgramCode(detailDoc);

        TrainingProgram program = trainingProgramRepository.findByUrl(programUrl)
                .orElseGet(TrainingProgram::new);
        program.setName(finalName);
        program.setUrl(programUrl);
        program.setCode(programCode);
        program.setUpdatedAt(LocalDateTime.now());

        Long programId = trainingProgramRepository.save(program);
        if (programId == null) {
            log.error("[Crawler] Không lấy được ID sau khi lưu CTĐT: {}", programUrl);
            return 0;
        }

        List<Course> courses = extractCoursesFromDetailDoc(detailDoc, programId);

        courseRepository.deleteByProgramId(programId);
        courseRepository.saveAll(programId, courses);

        log.info("[Crawler] Đã lưu CTĐT '{}' (Mã: {}) cùng {} học phần.", finalName, programCode, courses.size());
        return courses.size();
    }

    private String extractProgramCode(Document doc) {
        for (Element item : doc.select("li.item, div.card")) {
            Element label = item.selectFirst("label");
            if (label != null && label.text().toLowerCase().contains("mã ngành")) {
                Element strong = item.selectFirst("strong");
                if (strong != null && !strong.text().isBlank()) {
                    return strong.text().trim();
                }
            }
        }

        String bodyText = doc.body().text();
        Pattern codePattern = Pattern.compile("Mã\\s*(?:ngành|chương\\s*trình|đào\\s*tạo)[:\\s]+([A-Z0-9_\\-\\.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = codePattern.matcher(bodyText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private List<Course> extractCoursesFromDetailDoc(Document doc, Long programId) {
        Map<String, Course> courseMap = new LinkedHashMap<>();

        Elements cards = doc.select("div.card-mon-hoc");
        if (!cards.isEmpty()) {
            for (Element card : cards) {
                Element titleElem = card.selectFirst("div.title");
                Element tagElem = card.selectFirst("div.tag");

                if (titleElem == null || titleElem.text().isBlank()) {
                    continue;
                }

                String rawTitle = titleElem.text().trim();
                String courseCode = null;
                String courseName = rawTitle;

                Matcher m = CODE_AND_NAME_PATTERN.matcher(rawTitle);
                if (m.matches()) {
                    courseCode = m.group(1).trim();
                    courseName = m.group(2).trim();
                }

                Integer credits = null;
                if (tagElem != null) {
                    credits = parseCredits(tagElem.text());
                }

                String dedupKey = (courseCode != null ? courseCode : "") + "_" + courseName.toLowerCase();
                if (!courseMap.containsKey(dedupKey)) {
                    Course course = new Course(programId, courseCode, courseName, credits);
                    courseMap.put(dedupKey, course);
                }
            }
        }

        if (courseMap.isEmpty()) {
            Elements tables = doc.select("table");
            for (Element table : tables) {
                Elements rows = table.select("tr");
                if (rows.size() < 2) continue;

                Element headerRow = rows.first();
                Elements headers = headerRow.select("th, td");
                int codeCol = -1;
                int nameCol = -1;
                int creditCol = -1;

                for (int i = 0; i < headers.size(); i++) {
                    String hText = headers.get(i).text().toLowerCase().trim();
                    if (hText.contains("mã hp") || hText.contains("mã môn") || hText.contains("mã học phần") || hText.equals("mã")) {
                        codeCol = i;
                    } else if (hText.contains("tên hp") || hText.contains("tên môn") || hText.contains("tên học phần") || hText.contains("học phần")) {
                        nameCol = i;
                    } else if (hText.contains("tín chỉ") || hText.contains("số tc") || hText.contains("tc")) {
                        creditCol = i;
                    }
                }

                if (nameCol != -1) {
                    for (int i = 1; i < rows.size(); i++) {
                        Elements cells = rows.get(i).select("td, th");
                        if (cells.size() <= nameCol) continue;

                        String cName = cells.get(nameCol).text().trim();
                        if (cName.isBlank() || cName.equalsIgnoreCase("tên học phần")) continue;

                        String cCode = (codeCol != -1 && cells.size() > codeCol) ? cells.get(codeCol).text().trim() : null;
                        Integer cCredits = (creditCol != -1 && cells.size() > creditCol) ? parseCredits(cells.get(creditCol).text()) : null;

                        String dedupKey = (cCode != null ? cCode : "") + "_" + cName.toLowerCase();
                        if (!courseMap.containsKey(dedupKey)) {
                            Course course = new Course(programId, cCode, cName, cCredits);
                            courseMap.put(dedupKey, course);
                        }
                    }
                }
            }
        }

        return new ArrayList<>(courseMap.values());
    }

    private Integer parseCredits(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher matcher = CREDIT_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        Matcher digitMatcher = DIGIT_PATTERN.matcher(text);
        if (digitMatcher.find()) {
            return Integer.parseInt(digitMatcher.group(1));
        }

        return null;
    }
}
