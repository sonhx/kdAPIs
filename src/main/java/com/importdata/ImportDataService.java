package com.importdata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/import1")
public class ImportDataService {

    @Autowired
    private ImportDataDAO importDataDAO;

    @PostMapping("/getImport_1")
    public String getImportData1(@RequestBody String req) {
        return importDataDAO.getImportData_1(req);
    }

    @PostMapping("/getImport_2")
    public String getImportData2(@RequestBody String req) {
        return importDataDAO.getImportData_2(req);
    }

    @PostMapping("/getImport_3")
    public String getImportData3(@RequestBody String req) {
        return importDataDAO.getImportData_3(req);
    }

    @PostMapping("/getImport_4")
    public String getImportData4(@RequestBody String req) {
        return importDataDAO.getImportData_4(req);
    }

    @PostMapping("/getImport_CSVC_2")
    public String getImportData_CSVC_2(@RequestBody String req) {
        return importDataDAO.getImportData_CSVC_2(req);
    }

    @PostMapping("/getImport_CSVC_3")
    public String getImportData_CSVC_3(@RequestBody String req) {
        return importDataDAO.getImportData_CSVC_3(req);
    }

    @PostMapping("/getImport_CSVC_4")
    public String getImportData_CSVC_4(@RequestBody String req) {
        return importDataDAO.getImportData_CSVC_4(req);
    }

    @PostMapping("/clearTable_1")
    public String clearTable_1(@RequestBody String req) {
        return importDataDAO.clearTable_1(req);
    }
}
