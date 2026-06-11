package com.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileDownloaderNIO {
    private static final String FILE_URL1 = "https://quiz-lab.ptit.edu.vn/kdgd/doc/1/csgd/mc/H1/01/H1.01.01.01-%20516%20Q%C4%90-HV%2006.06.2017%20Q%C4%90%20c%C3%B4ng%20b%E1%BB%91%20S%E1%BB%A9%20m%E1%BA%A1ng%20t%E1%BA%A7m%20nh%C3%ACn%20m%E1%BB%A5c%20ti%C3%AAu%20chi%E1%BA%BFn%20l%C6%B0%E1%BB%A3c%20c%E1%BB%A7a%20HVCNBCVT.pdf";
    private static final String FILE_URL2 = "https://quiz-lab.ptit.edu.vn/kdgd/doc/1/csgd/mc/H1/01/H1.01.01.05-%2014%20CV-H%C4%90HV%2029.12.2020%20Xin%20%C3%BD%20ki%E1%BA%BFn%20v%E1%BB%81%20Chi%E1%BA%BFn%20l%C6%B0%E1%BB%A3c%20pt%20HV%202021-2025%20l%E1%BA%A7n2.pdf";
    private static final String ZIP_FILE_NAME = "downloaded_files_nio.zip";

    public static void main(String[] args) {
        try {
            URL url1 = new URL(FILE_URL1);
            URL url2 = new URL(FILE_URL2);

            try (InputStream in1 = url1.openStream(); InputStream in2 = url2.openStream()) {
                Files.copy(in1, Paths.get(ZIP_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(in2, Paths.get(ZIP_FILE_NAME), StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
