package com.admissions;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admissions")
@CrossOrigin(origins = "*")
public class AdmissionsController {

    @Autowired
    private AdmissionsScraperService scraperService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Manually triggers sync for a specific admission year (e.g. 2026).
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncAdmissions(@RequestParam(value = "year", defaultValue = "2026") int year) {
        int count = scraperService.scrapeAndSaveForYear(year);
        if (count > 0) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Successfully synced " + count + " admissions records for year " + year,
                "count", count,
                "year", year
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "message", "No admissions data found or failed to parse page for year " + year,
                "year", year
            ));
        }
    }

    /**
     * Retrieves admissions data from table.
     */
    @GetMapping
    public ResponseEntity<?> getAdmissions(@RequestParam(value = "year", required = false) Integer year) {
        String sql;
        List<Map<String, Object>> list;
        if (year != null) {
            sql = "SELECT * FROM dbo.admissions WHERE admission_year = ? ORDER BY campus_code ASC, program_type ASC, major_code ASC";
            list = jdbcTemplate.queryForList(sql, year);
        } else {
            sql = "SELECT * FROM dbo.admissions ORDER BY admission_year DESC, campus_code ASC, program_type ASC, major_code ASC";
            list = jdbcTemplate.queryForList(sql);
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "total", list.size(),
            "data", list
        ));
    }
}
