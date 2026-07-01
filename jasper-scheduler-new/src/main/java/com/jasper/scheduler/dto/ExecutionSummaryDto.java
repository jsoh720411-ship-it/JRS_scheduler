package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;

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
    private String outputFileUri;
}
