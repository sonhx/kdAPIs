package com.kpi;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * KpiH402Controller - REST Controller for KPI H4.02
 * "Mức độ hài lòng SV về tư vấn việc làm"
 */
@RestController
@RequestMapping({"/api/kpi/h402", "/kpi/h402", "/api/kpi/calculate/h402", "/kpi/calculate/h402"})
public class KpiH402Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiH402Controller.class);

    @Autowired
    private KpiH402CalculationService calculationService;

    /**
     * Triggers recalculation of KPI H4.02 from S-Link surveys.
     * Supports both POST and GET for seamless UI integration.
     */
    @RequestMapping(value = {"", "/recalculate", "/calculate"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recalculate(@RequestParam(value = "forceAll", defaultValue = "true") boolean forceAll) {
        log.info("Received recalculation request for KPI H4.02 (forceAll={})", forceAll);
        try {
            JSONObject result = calculationService.calculateAndSaveH402(null, forceAll);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error triggering H4.02 recalculation: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err.toString());
        }
    }

    /**
     * Retrieves breakdown of sub-criteria ratings for KPI H4.02.
     */
    @GetMapping(value = "/breakdown", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getBreakdown(@RequestParam(value = "periodCode", required = false) String periodCode) {
        try {
            JSONObject result = calculationService.getBreakdown(periodCode);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error getting H4.02 breakdown: ", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err.toString());
        }
    }
}
