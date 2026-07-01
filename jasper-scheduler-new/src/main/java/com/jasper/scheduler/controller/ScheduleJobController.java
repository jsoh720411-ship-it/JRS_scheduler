package com.jasper.scheduler.controller;

import com.jasper.scheduler.dto.ApiResponse;
import com.jasper.scheduler.dto.JobParamDto;
import com.jasper.scheduler.dto.RecipientExternalDto;
import com.jasper.scheduler.dto.RecipientInternalDto;
import com.jasper.scheduler.dto.ScheduleJobDto;
import com.jasper.scheduler.mapper.ExternalUserMapper;
import com.jasper.scheduler.mapper.InternalUserMapper;
import com.jasper.scheduler.mapper.ReportMapper;
import com.jasper.scheduler.mapper.ScheduleJobMapper;
import com.jasper.scheduler.service.JasperApiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

    // 기존 Job의 모든 설정(트리거/메일/수신자/파라미터)을 그대로 복사해 새 Job 등록 폼을 띄운다.
    // 같은 리포트를 다른 시각/다른 수신자에게 보내야 할 때(Job을 직접 100개씩 새로 만들 필요 없이)
    // 트리거와 수신자만 바꿔서 저장하면 되도록 돕는 기능.
    @GetMapping("/{jobId}/copy")
    public String copyForm(@PathVariable Long jobId, Model model) {
        ScheduleJobDto original = jobMapper.findById(jobId);
        original.setInternalRecipients(jobMapper.findInternalRecipients(original.getNotificationId()));
        original.setExternalRecipients(jobMapper.findExternalRecipients(original.getNotificationId()));
        original.setReportParams(jobMapper.findJobParams(jobId));

        // ID 계열 필드 초기화 → 저장 시 새 레코드로 INSERT되도록
        original.setJobId(null);
        original.setNotificationId(null);

        // 복사본임을 명확히, 상태는 PAUSED로 (실수 발송 방지)
        original.setJobLabel("복사 - " + original.getJobLabel());
        original.setStatus("PAUSED");

        model.addAttribute("job", original);
        model.addAttribute("reports", reportMapper.findAll());
        model.addAttribute("internalUsers", internalUserMapper.findByCondition(null, null, null));
        model.addAttribute("externalUsers", externalUserMapper.findAll());
        model.addAttribute("mode", "copy");
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
