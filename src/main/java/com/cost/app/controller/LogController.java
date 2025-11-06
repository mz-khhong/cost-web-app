package com.cost.app.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RestController
@RequestMapping("/api")
public class LogController {

    // 캐시 설정
    private static class CachedLogs {
        private final List<String> logs;
        private final long timestamp;
        private final int lines;

        public CachedLogs(List<String> logs, int lines) {
            this.logs = logs;
            this.timestamp = System.currentTimeMillis();
            this.lines = lines;
        }

        public List<String> getLogs() {
            return logs;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getLines() {
            return lines;
        }

        public boolean isExpired(long cacheDurationMs) {
            return System.currentTimeMillis() - timestamp > cacheDurationMs;
        }
    }

    // 캐시 저장소 (라인 수별로 캐시)
    private volatile CachedLogs cachedLogs = null;
    
    // 동시성 제어를 위한 락 (읽기/쓰기 분리)
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 캐시 유효 시간 (2초)
    private static final long CACHE_DURATION_MS = 2000;
    
    // 동시 요청 제한 (최대 3개의 journalctl 프로세스만 동시 실행)
    private volatile int activeRequests = 0;
    private static final int MAX_CONCURRENT_REQUESTS = 3;

    @GetMapping(value = "/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "100") int lines) {
        
        Map<String, Object> response = new HashMap<>();
        
        // 최근 로그 라인 수 제한 (보안: 최대 1000줄)
        int maxLines = Math.min(lines, 1000);
        
        // 읽기 락으로 캐시 확인
        lock.readLock().lock();
        try {
            if (cachedLogs != null && 
                !cachedLogs.isExpired(CACHE_DURATION_MS) && 
                cachedLogs.getLines() == maxLines) {
                // 캐시된 데이터 반환
                response.put("success", true);
                response.put("lines", cachedLogs.getLogs().size());
                response.put("logs", new ArrayList<>(cachedLogs.getLogs()));
                response.put("cached", true);
                return ResponseEntity.ok(response);
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // 동시 요청 수 제한 확인
        if (activeRequests >= MAX_CONCURRENT_REQUESTS) {
            // 캐시된 데이터가 있으면 반환, 없으면 에러
            lock.readLock().lock();
            try {
                if (cachedLogs != null) {
                    response.put("success", true);
                    response.put("lines", cachedLogs.getLogs().size());
                    response.put("logs", new ArrayList<>(cachedLogs.getLogs()));
                    response.put("cached", true);
                    response.put("warning", "서버 부하를 줄이기 위해 캐시된 데이터를 반환합니다.");
                    return ResponseEntity.ok(response);
                }
            } finally {
                lock.readLock().unlock();
            }
            
            response.put("success", false);
            response.put("error", "현재 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            response.put("logs", new ArrayList<>());
            return ResponseEntity.ok(response);
        }
        
        // 로그 읽기 실행
        try {
            activeRequests++;
            
            List<String> logLines = readLogsFromSystem(maxLines);
            
            // 쓰기 락으로 캐시 업데이트
            lock.writeLock().lock();
            try {
                cachedLogs = new CachedLogs(logLines, maxLines);
            } finally {
                lock.writeLock().unlock();
            }
            
            response.put("success", true);
            response.put("lines", logLines.size());
            response.put("logs", logLines);
            response.put("cached", false);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "로그를 읽는 중 오류가 발생했습니다: " + e.getMessage());
            response.put("logs", new ArrayList<>());
        } finally {
            activeRequests--;
        }
        
        return ResponseEntity.ok(response);
    }
    
    private List<String> readLogsFromSystem(int maxLines) throws Exception {
        // 로컬 개발 환경 체크 (MacOS, Windows 등에서는 journalctl이 없을 수 있음)
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean isLinux = os.contains("linux");
        
        if (!isLinux) {
            // 로컬 개발 환경에서는 더미 로그 또는 파일 기반 로그 사용
            return getLocalLogs(maxLines);
        }
        
        // Linux 환경에서 journalctl 명령어 실행
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "journalctl",
                    "-u", "cost-web-app",
                    "-n", String.valueOf(maxLines),
                    "--no-pager",
                    "--no-hostname"
            );
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // 로그 읽기
            List<String> logLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logLines.add(line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                // journalctl이 실패하면 로컬 로그로 대체
                return getLocalLogs(maxLines);
            }
            
            return logLines;
        } catch (Exception e) {
            // journalctl 명령어가 없거나 실행 실패 시 로컬 로그로 대체
            return getLocalLogs(maxLines);
        }
    }
    
    private List<String> getLocalLogs(int maxLines) {
        List<String> logLines = new ArrayList<>();
        
        // 로컬 개발 환경에서는 샘플 로그 반환
        logLines.add("INFO  --- [cost-web-app] [main] com.cost.app.CostWebAppApplication : Starting CostWebAppApplication");
        logLines.add("INFO  --- [cost-web-app] [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port(s): 8080 (http)");
        logLines.add("INFO  --- [cost-web-app] [main] com.cost.app.CostWebAppApplication : Started CostWebAppApplication");
        logLines.add("INFO  --- [cost-web-app] [http-nio-8080-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/] : Initializing Spring DispatcherServlet 'dispatcherServlet'");
        logLines.add("INFO  --- [cost-web-app] [http-nio-8080-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/] : Completed initialization in 1 ms");
        logLines.add("DEBUG --- [cost-web-app] [http-nio-8080-exec-1] com.cost.app.controller.LogController : Log request received for " + maxLines + " lines");
        
        // 로컬 환경 메시지 추가
        logLines.add("INFO  --- [cost-web-app] [main] com.cost.app.controller.LogController : 로컬 개발 환경에서 실행 중입니다. journalctl을 사용할 수 없습니다.");
        
        // 요청한 라인 수만큼 반환
        return logLines.subList(0, Math.min(logLines.size(), maxLines));
    }
}
