package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.ApiResponse;
import com.jasper.scheduler.dto.ExecutionSummaryDto;
import com.jasper.scheduler.mapper.ExecutionLogMapper;
import com.jasper.scheduler.service.JasperApiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class DashboardController {

    private final ExecutionLogMapper executionLogMapper;
    private final JasperApiService jasperApiService;

    public DashboardController(ExecutionLogMapper executionLogMapper,
                               JasperApiService jasperApiService) {
        this.executionLogMapper = executionLogMapper;
        this.jasperApiService = jasperApiService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                            @RequestParam(required = false) String status,
                            @AuthenticationPrincipal UserDetails user) {
        model.addAttribute("adminId", user.getUsername());
        model.addAttribute("totalSuccess", executionLogMapper.countTodayByStatus("SUCCESS"));
        model.addAttribute("totalFailed",  executionLogMapper.countTodayByStatus("FAILED"));
        model.addAttribute("totalPending", executionLogMapper.countPendingJobs());
        model.addAttribute("totalSent",    executionLogMapper.countTotalSentThisMonth());
        model.addAttribute("dailyStats",   executionLogMapper.findDailyStats(7));
        model.addAttribute("upcomingJobs", executionLogMapper.findUpcomingJobs(5));
        model.addAttribute("executions",   executionLogMapper.findByFilter(status, null, 50));
        model.addAttribute("selectedStatus", status);
        return "dashboard/index";
    }

    @PostMapping("/dashboard/retry/{executionId}")
    @ResponseBody
    public ApiResponse<Void> retry(@PathVariable Long executionId) {
        try {
            ExecutionSummaryDto exec = executionLogMapper.findById(executionId);
            if (exec == null) return ApiResponse.fail("실행 이력을 찾을 수 없습니다.");
            jasperApiService.executeJob(exec.getJobId(), "MANUAL");
            return ApiResponse.ok("재전송이 완료되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("재전송 실패: " + e.getMessage());
        }
    }
}
