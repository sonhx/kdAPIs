package com.kpi;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * KpiG209Controller - REST Controller for KPI G2.09
 * "Mức độ hài lòng GV về điều kiện làm việc"
 */
@RestController
@RequestMapping({"/api/kpi/g209", "/kpi/g209", "/api/kpi/calculate/g209", "/kpi/calculate/g209"})
public class KpiG209Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiG209Controller.class);

    @Autowired
    private KpiG209CalculationService calculationService;

    /**
     * Triggers recalculation of KPI G2.09 from S-Link surveys.
     * Supports both POST and GET for seamless UI integration.
     * 
     * @param forceAll If true, forces recalculation for all historical campaigns
     */
    @RequestMapping(value = {"", "/recalculate", "/calculate"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recalculate(@RequestParam(value = "forceAll", defaultValue = "true") boolean forceAll) {
        log.info("Received recalculation request for KPI G2.09 (forceAll={})", forceAll);
        try {
            JSONObject result = calculationService.calculateAndSaveG209(null, forceAll);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error triggering G2.09 recalculation: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err.toString());
        }
    }

    /**
     * Retrieves breakdown of sub-criteria ratings for KPI G2.09.
     * 
     * @param periodCode Optional period code (e.g. TERM_2025-2026_HK2)
     */
    @GetMapping(value = "/breakdown", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getBreakdown(@RequestParam(value = "periodCode", required = false) String periodCode) {
        try {
            JSONObject result = calculationService.getBreakdown(periodCode);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error getting G2.09 breakdown: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err.toString());
        }
    }
}
