package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.*;
import com.jasper.scheduler.mapper.*;
import com.jasper.scheduler.service.JasperApiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ════════════════════════════════════════════════════════════
//  로그인
// ════════════════════════════════════════════════════════════
@Controller
class AuthController {
    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @GetMapping("/")
    public String root() { return "redirect:/dashboard"; }
}

// ════════════════════════════════════════════════════════════
//  대시보드
// ════════════════════════════════════════════════════════════
@Controller
class DashboardController {

    private final ExecutionLogMapper executionLogMapper;
    private final JasperApiService jasperApiService;

    DashboardController(ExecutionLogMapper executionLogMapper,
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
        model.addAttribute("executions",
            executionLogMapper.findByFilter(status, null, 50));
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

// ════════════════════════════════════════════════════════════
//  Internal User
// ════════════════════════════════════════════════════════════
@Controller
@RequestMapping("/users/internal")
class InternalUserController {

    private final InternalUserMapper mapper;

    InternalUserController(InternalUserMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String company,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String keyword) {
        model.addAttribute("users", mapper.findByCondition(company, role, keyword));
        model.addAttribute("company", company);
        model.addAttribute("role", role);
        model.addAttribute("keyword", keyword);
        return "user/internal-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new InternalUserDto());
        model.addAttribute("mode", "create");
        return "user/internal-form";
    }

    @GetMapping("/{email}")
    public String editForm(@PathVariable String email, Model model) {
        model.addAttribute("user", mapper.findByEmail(email));
        model.addAttribute("mode", "edit");
        return "user/internal-form";
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Void> create(@RequestBody InternalUserDto dto) {
        try {
            mapper.insert(dto);
            return ApiResponse.ok("내부 사용자가 등록되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable String email,
                                    @RequestBody InternalUserDto dto) {
        try {
            dto.setEmail(email);
            mapper.update(dto);
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable String email) {
        try {
            mapper.delete(email);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }
}

// ════════════════════════════════════════════════════════════
//  External User
// ════════════════════════════════════════════════════════════
@Controller
@RequestMapping("/users/external")
class ExternalUserController {

    private final ExternalUserMapper mapper;

    ExternalUserController(ExternalUserMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String company,
                       @RequestParam(required = false) String partnerCode,
                       @RequestParam(required = false) String keyword) {
        model.addAttribute("users", mapper.findByCondition(company, partnerCode, keyword));
        model.addAttribute("company", company);
        model.addAttribute("partnerCode", partnerCode);
        model.addAttribute("keyword", keyword);
        return "user/external-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new ExternalUserDto());
        model.addAttribute("mode", "create");
        return "user/external-form";
    }

    @GetMapping("/{email}")
    public String editForm(@PathVariable String email, Model model) {
        model.addAttribute("user", mapper.findByEmail(email));
        model.addAttribute("mode", "edit");
        return "user/external-form";
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Void> create(@RequestBody ExternalUserDto dto) {
        try {
            mapper.insert(dto);
            return ApiResponse.ok("외부 사용자가 등록되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable String email,
                                    @RequestBody ExternalUserDto dto) {
        try {
            dto.setContactEmail(email);
            mapper.update(dto);
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{email}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable String email) {
        try {
            mapper.delete(email);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }
}

// ════════════════════════════════════════════════════════════
//  Report
// ════════════════════════════════════════════════════════════
@Controller
@RequestMapping("/reports")
class ReportController {

    private final ReportMapper mapper;

    ReportController(ReportMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("reports", mapper.findAll());
        return "report/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("report", new ReportDto());
        model.addAttribute("mode", "create");
        return "report/form";
    }

    @GetMapping("/{reportId}")
    public String editForm(@PathVariable Long reportId, Model model) {
        ReportDto report = mapper.findById(reportId);
        report.setParams(mapper.findParams(reportId));
        model.addAttribute("report", report);
        model.addAttribute("mode", "edit");
        return "report/form";
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Long> create(@RequestBody ReportDto dto) {
        try {
            mapper.insert(dto);
            if (dto.getParams() != null) {
                for (ParamDefDto p : dto.getParams()) {
                    p.setReportId(dto.getReportId());
                    mapper.insertParam(p);
                }
            }
            return ApiResponse.ok(dto.getReportId());
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{reportId}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable Long reportId,
                                    @RequestBody ReportDto dto) {
        try {
            dto.setReportId(reportId);
            mapper.update(dto);
            mapper.deleteParamsByReportId(reportId);
            if (dto.getParams() != null) {
                for (ParamDefDto p : dto.getParams()) {
                    p.setReportId(reportId);
                    mapper.insertParam(p);
                }
            }
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{reportId}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable Long reportId) {
        try {
            mapper.deleteParamsByReportId(reportId);
            mapper.delete(reportId);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }
}

// ════════════════════════════════════════════════════════════
//  Schedule Job
// ════════════════════════════════════════════════════════════
@Controller
@RequestMapping("/jobs")
class ScheduleJobController {

    private final ScheduleJobMapper jobMapper;
    private final ReportMapper reportMapper;
    private final InternalUserMapper internalUserMapper;
    private final ExternalUserMapper externalUserMapper;
    private final JasperApiService jasperApiService;

    ScheduleJobController(ScheduleJobMapper jobMapper, ReportMapper reportMapper,
                          InternalUserMapper internalUserMapper,
                          ExternalUserMapper externalUserMapper,
                          JasperApiService jasperApiService) {
        this.jobMapper = jobMapper;
        this.reportMapper = reportMapper;
        this.internalUserMapper = internalUserMapper;
        this.externalUserMapper = externalUserMapper;
        this.jasperApiService = jasperApiService;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String status) {
        List<ScheduleJobDto> jobs = status != null
            ? jobMapper.findByStatus(status)
            : jobMapper.findAll();
        model.addAttribute("jobs", jobs);
        model.addAttribute("selectedStatus", status);
        return "job/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("job", new ScheduleJobDto());
        model.addAttribute("reports", reportMapper.findAll());
        model.addAttribute("internalRoles",   internalUserMapper.findByCondition(null, null, null));
        model.addAttribute("externalUsers",   externalUserMapper.findAll());
        model.addAttribute("mode", "create");
        return "job/form";
    }

    @GetMapping("/{jobId}")
    public String editForm(@PathVariable Long jobId, Model model) {
        ScheduleJobDto job = jobMapper.findById(jobId);
        job.setInternalRecipients(jobMapper.findInternalRecipients(job.getNotificationId()));
        job.setExternalRecipients(jobMapper.findExternalRecipients(job.getNotificationId()));
        job.setReportParams(jobMapper.findJobParams(jobId));
        model.addAttribute("job", job);
        model.addAttribute("reports", reportMapper.findAll());
        model.addAttribute("internalUsers", internalUserMapper.findByCondition(null, null, null));
        model.addAttribute("externalUsers", externalUserMapper.findAll());
        model.addAttribute("mode", "edit");
        return "job/form";
    }

    @PostMapping
    @ResponseBody
    public ApiResponse<Long> create(@RequestBody ScheduleJobDto dto,
                                    @AuthenticationPrincipal UserDetails user) {
        try {
            dto.setCreatedBy(user.getUsername());
            jobMapper.insert(dto);
            jobMapper.insertTrigger(dto);
            jobMapper.insertNotification(dto);
            saveRecipients(dto);
            saveReportParams(dto);
            return ApiResponse.ok(dto.getJobId());
        } catch (Exception e) {
            return ApiResponse.fail("등록 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{jobId}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable Long jobId,
                                    @RequestBody ScheduleJobDto dto) {
        try {
            dto.setJobId(jobId);
            jobMapper.update(dto);
            jobMapper.updateTrigger(dto);
            jobMapper.updateNotification(dto);
            jobMapper.deleteRecipientInternalByNotification(dto.getNotificationId());
            jobMapper.deleteRecipientExternalByNotification(dto.getNotificationId());
            saveRecipients(dto);
            jobMapper.deleteJobParamsByJobId(jobId);
            saveReportParams(dto);
            return ApiResponse.ok("수정되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("수정 실패: " + e.getMessage());
        }
    }

    @PatchMapping("/{jobId}/status")
    @ResponseBody
    public ApiResponse<Void> updateStatus(@PathVariable Long jobId,
                                          @RequestBody java.util.Map<String, String> body) {
        try {
            jobMapper.updateStatus(jobId, body.get("status"));
            return ApiResponse.ok("상태가 변경되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("변경 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{jobId}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable Long jobId) {
        try {
            ScheduleJobDto job = jobMapper.findById(jobId);
            jobMapper.deleteRecipientInternalByNotification(job.getNotificationId());
            jobMapper.deleteRecipientExternalByNotification(job.getNotificationId());
            jobMapper.deleteNotification(jobId);
            jobMapper.deleteTrigger(jobId);
            jobMapper.deleteJobParamsByJobId(jobId);
            jobMapper.delete(jobId);
            return ApiResponse.ok("삭제되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("삭제 실패: " + e.getMessage());
        }
    }

    @PostMapping("/{jobId}/run")
    @ResponseBody
    public ApiResponse<Void> runNow(@PathVariable Long jobId) {
        try {
            jasperApiService.executeJob(jobId, "MANUAL");
            return ApiResponse.ok("즉시 실행되었습니다.");
        } catch (Exception e) {
            return ApiResponse.fail("실행 실패: " + e.getMessage());
        }
    }

    private void saveRecipients(ScheduleJobDto dto) {
        if (dto.getInternalRecipients() != null) {
            for (RecipientInternalDto r : dto.getInternalRecipients()) {
                r.setNotificationId(dto.getNotificationId());
                jobMapper.insertRecipientInternal(r);
            }
        }
        if (dto.getExternalRecipients() != null) {
            for (RecipientExternalDto r : dto.getExternalRecipients()) {
                r.setNotificationId(dto.getNotificationId());
                jobMapper.insertRecipientExternal(r);
            }
        }
    }

    private void saveReportParams(ScheduleJobDto dto) {
        if (dto.getReportParams() != null) {
            for (JobParamDto p : dto.getReportParams()) {
                p.setJobId(dto.getJobId());
                jobMapper.insertJobParam(p);
            }
        }
    }
}
