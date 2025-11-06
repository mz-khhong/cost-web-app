package com.cost.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class MainController {

    @GetMapping("/")
    public String index(Model model, HttpServletRequest request) {
        // 접속 정보 수집
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String sessionId = request.getSession().getId();
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String remoteHost = request.getRemoteHost();
        String acceptLanguage = request.getHeader("Accept-Language");
        String referer = request.getHeader("Referer");
        
        // 접속 시간
        LocalDateTime now = LocalDateTime.now();
        String currentTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        // 모델에 추가
        model.addAttribute("title", "현재 사이트의 정보입니다.");
        model.addAttribute("message", "Cost Web App에 오신 것을 환영합니다!");
        model.addAttribute("currentTime", currentTime);
        model.addAttribute("status", "UP");
        
        // 접속 정보
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("userAgent", userAgent != null ? userAgent : "N/A");
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("requestUri", requestUri);
        model.addAttribute("method", method);
        model.addAttribute("serverName", serverName);
        model.addAttribute("serverPort", serverPort);
        model.addAttribute("remoteHost", remoteHost);
        model.addAttribute("acceptLanguage", acceptLanguage != null ? acceptLanguage : "N/A");
        model.addAttribute("referer", referer != null ? referer : "직접 접속");
        
        return "index";
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("title", "Home");
        model.addAttribute("message", "홈 페이지입니다.");
        return "index";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        return "login";
    }

    @GetMapping("/components/login")
    public String loginComponent() {
        return "components/login";
    }

    @GetMapping("/components/health")
    public String healthComponent() {
        return "components/health";
    }

    @GetMapping("/components/actuator")
    public String actuatorComponent() {
        return "components/actuator";
    }

    @GetMapping("/components/prometheus")
    public String prometheusComponent() {
        return "components/prometheus";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) Boolean rememberMe,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {
        
        // TODO: 실제 인증 로직 구현 필요
        // 현재는 기본 검증만 수행
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            model.addAttribute("error", "ID와 비밀번호를 입력해주세요.");
            // AJAX 요청인 경우 그대로 login 페이지 반환 (에러 포함)
            return "login";
        }

        // 기본 인증 (임시)
        // 실제로는 Spring Security를 사용하거나 DB 조회를 해야 함
        if (username.equals("admin") && password.equals("admin")) {
            redirectAttributes.addFlashAttribute("message", "로그인 성공!");
            return "redirect:/";
        } else {
            model.addAttribute("error", "ID 또는 비밀번호가 올바르지 않습니다.");
            return "login";
        }
    }
}

