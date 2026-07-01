package com.jasper.scheduler.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jasper.scheduler.config.FlexibleLocalDateTimeDeserializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScheduleJobDto {
    // Job 기본
    private Long jobId;
    private Long reportId;
    private String reportName;
    private String jasperReportUri;     // REPORT.jasper_report_uri (조인 결과)
    private String jobLabel;
    private String jobDescription;
    private String outputFormats;
    private String baseOutputFilename;
    private String outputTimezone;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    // Repository
    private String repoFolderUri;
    private boolean repoSaveToRepository;
    private boolean repoOverwriteFiles;
    private boolean repoSequentialFilenames;
    private String jasperJobId;

    // Trigger
    private String triggerType;         // SIMPLE / CALENDAR
    private String timezone;
    private int startType;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startDate;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endDate;

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
