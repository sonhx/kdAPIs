package com.kpi;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * KpiQ704Controller - REST Controller for KPI Q7.04
 * "Mức độ hài lòng cán bộ về môi trường làm việc"
 */
@RestController
@RequestMapping({"/api/kpi/q704", "/kpi/q704", "/api/kpi/calculate/q704", "/kpi/calculate/q704"})
public class KpiQ704Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiQ704Controller.class);

    @Autowired
    private KpiQ704CalculationService calculationService;

    /**
     * Triggers recalculation of KPI Q7.04 from S-Link surveys.
     * Supports both POST and GET for seamless UI integration.
     * 
     * @param forceAll If true, forces recalculation for all historical campaigns
     */
    @RequestMapping(value = {"", "/recalculate", "/calculate"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> recalculate(@RequestParam(value = "forceAll", defaultValue = "false") boolean forceAll) {
        log.info("Received recalculation request for KPI Q7.04 (forceAll={})", forceAll);
        try {
            JSONObject result = calculationService.calculateAndSaveQ704(null, forceAll);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                    .body(result.toString());
        } catch (Exception e) {
            log.error("Error triggering Q7.04 recalculation: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500)
                    .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                    .body(err.toString());
        }
    }

    /**
     * Retrieves breakdown of sub-criteria ratings for KPI Q7.04.
     * 
     * @param periodCode Optional period code (e.g. TERM_2025-2026_HK2)
     */
    @GetMapping(value = "/breakdown", produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> getBreakdown(@RequestParam(value = "periodCode", required = false) String periodCode) {
        try {
            JSONObject result = calculationService.getBreakdown(periodCode);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                    .body(result.toString());
        } catch (Exception e) {
            log.error("Error getting Q7.04 breakdown: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500)
                    .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                    .body(err.toString());
        }
    }
}
