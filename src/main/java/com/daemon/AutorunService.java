package com.daemon;

import java.io.File;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class AutorunService {

    @PostConstruct
    public void startWatchThread() {
        new Thread(this::watchDirectory).start();
    }

    private void watchDirectory() {
        String userHome = System.getProperty("user.home");
        String ocrIn = userHome + File.separator + "Desktop" + File.separator + "ocrIn";
        String ocrOut = userHome + File.separator + "Desktop" + File.separator + "ocrOut";
        String abbyyProg = "C:\\Program Files\\ABBYY FineReader 16";

        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            Path path = Paths.get(ocrIn);
            if (!Files.exists(path)) Files.createDirectories(path);
            
            path.register(service, StandardWatchEventKinds.ENTRY_CREATE);
            
            System.out.println("AutorunService: Watching " + ocrIn);

            WatchKey key;
            while ((key = service.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        String filename = event.context().toString();
                        System.out.println("New file detected: " + filename);
                        executeOcr(ocrIn + "\\" + filename, ocrOut, abbyyProg, filename);
                    }
                }
                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeOcr(String inputPath, String ocrOut, String abbyyProg, String filename) {
        try {
            String outputFilename = filename.substring(0, filename.lastIndexOf(".")) + ".txt";
            String outputPath = ocrOut + "\\" + outputFilename;
            
            String sCmdLine = "cmd /c c: && cd \"" + abbyyProg + "\" && finereaderocr.exe \"" + inputPath + "\" /lang vietnamese /out \"" + outputPath + "\" /quit";
            
            CommandLine cmdLine = CommandLine.parse(sCmdLine);
            DefaultExecutor executor = new DefaultExecutor();
            executor.execute(cmdLine);
            System.out.println("OCR executed for: " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
