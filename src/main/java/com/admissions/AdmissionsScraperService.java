package com.admissions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdmissionsScraperService {

    private static final Logger logger = LoggerFactory.getLogger(AdmissionsScraperService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static class AdmissionRecord {
        public int admissionYear;
        public String campusCode;
        public String majorCode;
        public String majorName;
        public String programType;
        public int targetQuota;
        public int registeredCount;
        public int enrolledCount;
        public String notes;

        @Override
        public String toString() {
            return String.format("AdmissionRecord[%d | %s | %s | %s | %s | Quota: %d]",
                    admissionYear, campusCode, majorCode, majorName, programType, targetQuota);
        }
    }

    /**
     * Scrapes admissions data for a target year from PTIT website and updates the admissions table.
     * @param year the admission year (e.g., 2026)
     * @return the number of records synced, or 0 if page/data is not found
     */
    @Transactional
    public int scrapeAndSaveForYear(int year) {
        String url = "https://tuyensinh.ptit.edu.vn/de-an-tuyen-sinh/thong-tin-tuyen-sinh-dai-hoc-chinh-quy-nam-" + year + "/";
        logger.info("Starting PTIT Admissions Scraper for year {} using URL: {}", year, url);

        List<AdmissionRecord> records = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .referrer("https://tuyensinh.ptit.edu.vn/")
                    .timeout(15000)
                    .get();

            Elements tables = doc.select("table");
            logger.info("Found {} tables on page {}", tables.size(), url);

            if (tables.isEmpty()) {
                logger.warn("No admissions tables found at {}", url);
                return 0;
            }

            // Table 1 is usually Hà Nội Campus (BVH)
            if (tables.size() >= 1) {
                List<AdmissionRecord> bvhRecords = parseQuotaTable(tables.get(0), year, "BVH");
                records.addAll(bvhRecords);
            }

            // Table 2 is usually TP.HCM Campus (BVS)
            if (tables.size() >= 2) {
                List<AdmissionRecord> bvsRecords = parseQuotaTable(tables.get(1), year, "BVS");
                records.addAll(bvsRecords);
            }

            // Parse historical enrollment stats (Table 6 BVH, Table 7 BVS) if applicable
            if (tables.size() >= 6) {
                updateHistoricalStats(tables.get(5), "BVH", year, records);
            }
            if (tables.size() >= 7) {
                updateHistoricalStats(tables.get(6), "BVS", year, records);
            }

        } catch (IOException e) {
            logger.warn("Failed to fetch admissions page for year {}: {}", year, e.getMessage());
            return 0;
        } catch (Exception e) {
            logger.error("Error parsing admissions page for year {}: {}", year, e.getMessage(), e);
            return 0;
        }

        if (records.isEmpty()) {
            logger.warn("Extracted 0 records for year {}", year);
            return 0;
        }

        int count = saveOrUpdateRecords(records);
        logger.info("Successfully synced {} admissions records for year {}", count, year);
        return count;
    }

    private List<AdmissionRecord> parseQuotaTable(Element table, int year, String campusCode) {
        List<AdmissionRecord> list = new ArrayList<>();
        Elements rows = table.select("tr");
        String currentProgramType = "Đại trà";

        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.isEmpty()) continue;

            String fullText = row.text().trim();

            // Detect section category headers
            if (fullText.contains("TÀI NĂNG") || fullText.contains("THẠC SĨ TÍCH HỢP")) {
                currentProgramType = "Đào tạo tài năng";
                continue;
            } else if (fullText.contains("ĐẠI TRÀ")) {
                currentProgramType = "Đại trà";
                continue;
            } else if (fullText.contains("CHẤT LƯỢNG CAO")) {
                currentProgramType = "Chất lượng cao";
                continue;
            } else if (fullText.contains("LIÊN KẾT QUỐC TẾ")) {
                currentProgramType = "Liên kết quốc tế";
                continue;
            } else if (fullText.contains("MỚI TỪ NĂM") || fullText.contains("DỰ KIẾN")) {
                currentProgramType = "Chương trình mới";
                continue;
            }

            // Skip table header rows
            if (fullText.contains("Mã xét tuyển") || fullText.contains("Số lượng dự kiến") || fullText.contains("Tên ngành")) {
                continue;
            }

            // Standard quota row formats:
            // Col 0: TT (number)
            // Col 1: Mã xét tuyển (e.g. 7480201 or 8480101TN)
            // Col 2: Tên ngành/chương trình
            // Col 3: Mã ngành
            // Col 4: Tên ngành theo danh mục
            // Col 5: Số lượng dự kiến / target quota
            if (cells.size() >= 5) {
                String majorCode = cells.get(1).text().trim();
                String majorName = cells.get(2).text().trim();
                String quotaStr = cells.get(cells.size() >= 6 ? 5 : 4).text().trim();

                // Clean major code and quota
                if (majorCode.matches("^[0-9A-Za-z_]+$") && !majorCode.equalsIgnoreCase("TT")) {
                    int quota = parseInteger(quotaStr);
                    if (quota > 0 || !majorName.isEmpty()) {
                        AdmissionRecord record = new AdmissionRecord();
                        record.admissionYear = year;
                        record.campusCode = campusCode;
                        record.majorCode = majorCode;
                        record.majorName = majorName;
                        record.programType = currentProgramType;
                        record.targetQuota = quota;
                        record.registeredCount = 0;
                        record.enrolledCount = 0;
                        record.notes = null;
                        list.add(record);
                    }
                }
            }
        }
        return list;
    }

    private void updateHistoricalStats(Element table, String campusCode, int targetYear, List<AdmissionRecord> records) {
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.size() < 4) continue;

            String majorCode = cells.get(1).text().trim();
            String majorName = cells.get(2).text().trim();

            if (!majorCode.matches("^[0-9A-Za-z_]+$") || majorCode.equalsIgnoreCase("TT")) continue;

            // Check if year 2024 / 2025 enrollment stats columns are present
            // If historical data row matches existing record in our list, update enrolled count
            for (AdmissionRecord rec : records) {
                if (rec.campusCode.equals(campusCode) && rec.majorCode.equals(majorCode)) {
                    // Look for numbers in remaining cells if applicable
                    for (int i = 3; i < cells.size(); i++) {
                        int val = parseInteger(cells.get(i).text());
                        if (val > 0 && rec.enrolledCount == 0) {
                            rec.enrolledCount = val;
                            break;
                        }
                    }
                }
            }
        }
    }

    private int parseInteger(String str) {
        if (str == null) return 0;
        String clean = str.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return 0;
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int saveOrUpdateRecords(List<AdmissionRecord> records) {
        int updatedCount = 0;
        for (AdmissionRecord r : records) {
            boolean exists = false;
            try {
                Integer existingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM dbo.admissions WHERE admission_year = ? AND campus_code = ? AND major_code = ? AND program_type = ?",
                    Integer.class, r.admissionYear, r.campusCode, r.majorCode, r.programType
                );
                if (existingId != null) exists = true;
            } catch (EmptyResultDataAccessException e) {
                exists = false;
            }

            if (exists) {
                String sql = "UPDATE dbo.admissions SET major_name = ?, target_quota = ?, registered_count = ?, enrolled_count = ?, notes = ? " +
                             "WHERE admission_year = ? AND campus_code = ? AND major_code = ? AND program_type = ?";
                jdbcTemplate.update(sql, r.majorName, r.targetQuota, r.registeredCount, r.enrolledCount, r.notes,
                        r.admissionYear, r.campusCode, r.majorCode, r.programType);
            } else {
                String sql = "INSERT INTO dbo.admissions (admission_year, campus_code, major_code, major_name, program_type, target_quota, registered_count, enrolled_count, notes) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                jdbcTemplate.update(sql, r.admissionYear, r.campusCode, r.majorCode, r.majorName, r.programType,
                        r.targetQuota, r.registeredCount, r.enrolledCount, r.notes);
            }
            updatedCount++;
        }
        return updatedCount;
    }
}
