package com.jasper.scheduler.entity;

import lombok.Data;
import java.time.LocalDateTime;

// ── ADMIN_USER ──────────────────────────────────────────────
@Data
class AdminUser {
    private String adminId;
    private String passwordHash;
    private String adminName;
    private boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}

// ── INTERNAL_USER ───────────────────────────────────────────
@Data
class InternalUser {
    private String email;
    private String company;
    private String role;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ── EXTERNAL_USER ───────────────────────────────────────────
@Data
class ExternalUser {
    private String contactEmail;
    private String company;
    private String partnerCode;
    private String role;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ── REPORT ──────────────────────────────────────────────────
@Data
class Report {
    private Long reportId;
    private String reportName;
    private String jasperReportUri;
    private String description;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ── REPORT_PARAM_DEF ────────────────────────────────────────
@Data
class ReportParamDef {
    private Long paramDefId;
    private Long reportId;
    private String paramName;
    private String paramType;
    private boolean isRequired;
    private String defaultValue;
    private int displayOrder;
}

// ── SCHEDULE_JOB ────────────────────────────────────────────
@Data
class ScheduleJob {
    private Long jobId;
    private Long reportId;
    private String jobLabel;
    private String jobDescription;
    private String outputFormats;
    private String baseOutputFilename;
    private String outputTimezone;
    private String repoFolderUri;
    private boolean repoSaveToRepository;
    private boolean repoOverwriteFiles;
    private boolean repoSequentialFilenames;
    private String jasperJobId;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// ── JOB_TRIGGER ─────────────────────────────────────────────
@Data
class JobTrigger {
    private Long triggerId;
    private Long jobId;
    private String triggerType;
    private String timezone;
    private int startType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int misfireInstruction;
    private Integer occurrenceCount;
    private Integer recurrenceInterval;
    private String recurrenceIntervalUnit;
    private String cronExpression;
}

// ── JOB_MAIL_NOTIFICATION ────────────────────────────────────
@Data
class JobMailNotification {
    private Long notificationId;
    private Long jobId;
    private String subject;
    private String messageText;
    private String resultSendType;
    private boolean skipEmptyReports;
    private boolean skipNotifOnFail;
    private boolean includeStacktraceOnFail;
}

// ── JOB_RECIPIENT_INTERNAL ──────────────────────────────────
@Data
class JobRecipientInternal {
    private Long recipientId;
    private Long notificationId;
    private String recipientMode;
    private String role;
    private String company;
    private String recipientType;
}

// ── JOB_RECIPIENT_EXTERNAL ──────────────────────────────────
@Data
class JobRecipientExternal {
    private Long recipientId;
    private Long notificationId;
    private String contactEmail;
    private String recipientType;
}

// ── JOB_EXECUTION_LOG ───────────────────────────────────────
@Data
class JobExecutionLog {
    private Long executionId;
    private Long jobId;
    private LocalDateTime jasperFireTime;
    private String executionStatus;
    private String outputFileUri;
    private String errorMessage;
    private String triggeredBy;
    private LocalDateTime createdAt;
}

// ── MAIL_SEND_LOG ───────────────────────────────────────────
@Data
class MailSendLog {
    private Long sendLogId;
    private Long executionId;
    private String recipientEmail;
    private String recipientType;
    private String userType;
    private String sendStatus;
    private LocalDateTime sentAt;
    private String errorMessage;
}
