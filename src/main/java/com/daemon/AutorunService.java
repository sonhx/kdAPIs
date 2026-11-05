package com.daemon;

import java.io.File;
import java.nio.file.*;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class AutorunService {

    private volatile boolean running = true;
    private WatchService watchService;
    private Thread watchThread;

    @PostConstruct
    public void startWatchThread() {
        watchThread = new Thread(this::watchDirectory, "ocr-autorun-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    @PreDestroy
    public void stopWatchThread() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception ignored) {}
        }
        if (watchThread != null && watchThread.isAlive()) {
            watchThread.interrupt();
        }
    }

    private void watchDirectory() {
        String userHome = System.getProperty("user.home");
        String ocrIn = userHome + File.separator + "Desktop" + File.separator + "ocrIn";
        String ocrOut = userHome + File.separator + "Desktop" + File.separator + "ocrOut";
        String abbyyProg = "C:\\Program Files\\ABBYY FineReader 16";

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(ocrIn);
            if (!Files.exists(path)) Files.createDirectories(path);
            
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            
            System.out.println("AutorunService: Watching " + ocrIn);

            WatchKey key;
            while (running && (key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        String filename = event.context().toString();
                        System.out.println("New file detected: " + filename);
                        executeOcr(ocrIn + "\\" + filename, ocrOut, abbyyProg, filename);
                    }
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException | InterruptedException e) {
            // Expected on application shutdown / context reload
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeOcr(String inputPath, String ocrOut, String abbyyProg, String filename) {
        try {
            int dotIdx = filename.lastIndexOf(".");
            String outputFilename = (dotIdx >= 0 ? filename.substring(0, dotIdx) : filename) + ".txt";
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
