package com.kpi;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * KpiH404Controller - REST Controller for KPI H4.04
 * "Mức độ hài lòng về thư viện và học liệu số"
 */
@RestController
@RequestMapping({"/api/kpi/h404", "/kpi/h404", "/api/kpi/calculate/h404", "/kpi/calculate/h404"})
public class KpiH404Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiH404Controller.class);

    @Autowired
    private KpiH404CalculationService calculationService;

    /**
     * Triggers recalculation of KPI H4.04 from S-Link surveys.
     */
    @RequestMapping(value = {"", "/recalculate", "/calculate"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recalculate(@RequestParam(value = "forceAll", defaultValue = "true") boolean forceAll) {
        log.info("Received recalculation request for KPI H4.04 (forceAll={})", forceAll);
        try {
            JSONObject result = calculationService.calculateAndSaveH404(null, forceAll);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error triggering H4.04 recalculation: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err.toString());
        }
    }

    /**
     * Retrieves breakdown of sub-criteria ratings for KPI H4.04.
     */
    @GetMapping(value = "/breakdown", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getBreakdown(@RequestParam(value = "periodCode", required = false) String periodCode) {
        try {
            JSONObject result = calculationService.getBreakdown(periodCode);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error getting H4.04 breakdown: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err.toString());
        }
    }
}
