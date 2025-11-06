package com.cost.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class MainController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "Cost Web App");
        model.addAttribute("message", "Cost Web App에 오신 것을 환영합니다!");
        model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.addAttribute("status", "UP");
        return "index";
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("title", "Home");
        model.addAttribute("message", "홈 페이지입니다.");
        return "index";
    }
}

