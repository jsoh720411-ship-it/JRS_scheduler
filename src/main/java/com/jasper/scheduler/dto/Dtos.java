package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

// ── Dashboard DTO ────────────────────────────────────────────
@Data
public class DashboardDto {
    private int totalSuccess;
    private int totalFailed;
    private int totalPending;
    private int totalSent;
    private List<ExecutionSummaryDto> recentExecutions;
    private List<UpcomingJobDto> upcomingJobs;
    private List<DailyStatDto> dailyStats;
}

@Data
public class ExecutionSummaryDto {
    private Long executionId;
    private Long jobId;
    private String jobLabel;
    private String reportName;
    private LocalDateTime jasperFireTime;
    private String executionStatus;
    private String triggeredBy;
    private int sentCount;
    private String errorMessage;
}

@Data
public class UpcomingJobDto {
    private Long jobId;
    private String jobLabel;
    private String outputFormats;
    private String status;
    private String nextFireTime;
}

@Data
public class DailyStatDto {
    private String dayLabel;
    private int successCount;
    private int failedCount;
}

// ── InternalUser DTO ─────────────────────────────────────────
@Data
public class InternalUserDto {
    private String email;
    private String company;
    private String role;
    private boolean isActive;
}

// ── ExternalUser DTO ─────────────────────────────────────────
@Data
public class ExternalUserDto {
    private String contactEmail;
    private String company;
    private String partnerCode;
    private String role;
    private boolean isActive;
}

// ── Report DTO ───────────────────────────────────────────────
@Data
public class ReportDto {
    private Long reportId;
    private String reportName;
    private String jasperReportUri;
    private String description;
    private boolean isActive;
    private List<ParamDefDto> params;
}

@Data
public class ParamDefDto {
    private Long paramDefId;
    private Long reportId;
    private String paramName;
    private String paramType;
    private boolean isRequired;
    private String defaultValue;
    private int displayOrder;
}

// ── ScheduleJob DTO ──────────────────────────────────────────
@Data
public class ScheduleJobDto {
    // Job 기본
    private Long jobId;
    private Long reportId;
    private String reportName;
    private String jobLabel;
    private String jobDescription;
    private String outputFormats;
    private String baseOutputFilename;
    private String outputTimezone;
    private String status;
    private String createdBy;

    // Repository
    private String repoFolderUri;
    private boolean repoSaveToRepository;
    private boolean repoOverwriteFiles;
    private boolean repoSequentialFilenames;
    private String jasperJobId;

    // Trigger
    private String triggerType;
    private String timezone;
    private int startType;
    private String startDate;
    private String endDate;
    private int misfireInstruction;
    private Integer occurrenceCount;
    private Integer recurrenceInterval;
    private String recurrenceIntervalUnit;
    private String cronExpression;

    // Mail Notification
    private Long notificationId;
    private String subject;
    private String messageText;
    private String resultSendType;
    private boolean skipEmptyReports;
    private boolean skipNotifOnFail;
    private boolean includeStacktraceOnFail;

    // Recipients
    private List<RecipientInternalDto> internalRecipients;
    private List<RecipientExternalDto> externalRecipients;

    // Report Params
    private List<JobParamDto> reportParams;
}

@Data
public class RecipientInternalDto {
    private Long recipientId;
    private Long notificationId;
    private String recipientMode;   // BY_ROLE / BY_COMPANY
    private String role;
    private String company;
    private String recipientType;   // TO / CC / BCC
}

@Data
public class RecipientExternalDto {
    private Long recipientId;
    private Long notificationId;
    private String contactEmail;
    private String recipientType;   // TO / CC / BCC
}

@Data
public class JobParamDto {
    private Long paramId;
    private Long jobId;
    private Long paramDefId;
    private String paramName;
    private String paramValue;
    private boolean isDynamic;
    private String dynamicExpr;
}

// ── API Response ─────────────────────────────────────────────
@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true; r.data = data; return r;
    }
    public static <T> ApiResponse<T> ok(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true; r.message = message; return r;
    }
    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false; r.message = message; return r;
    }
}
