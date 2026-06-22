package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.*;
import com.jasper.scheduler.mapper.ExternalUserMapper;
import com.jasper.scheduler.mapper.InternalUserMapper;
import com.jasper.scheduler.mapper.ReportMapper;
import com.jasper.scheduler.mapper.ScheduleJobMapper;
import com.jasper.scheduler.service.JasperApiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/jobs")
public class ScheduleJobController {

    private final ScheduleJobMapper jobMapper;
    private final ReportMapper reportMapper;
    private final InternalUserMapper internalUserMapper;
    private final ExternalUserMapper externalUserMapper;
    private final JasperApiService jasperApiService;

    public ScheduleJobController(ScheduleJobMapper jobMapper, ReportMapper reportMapper,
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
    public String list(Model model, @RequestParam(required = false) String status) {
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
        model.addAttribute("internalUsers", internalUserMapper.findByCondition(null, null, null));
        model.addAttribute("externalUsers", externalUserMapper.findAll());
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
                                          @RequestBody Map<String, String> body) {
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
