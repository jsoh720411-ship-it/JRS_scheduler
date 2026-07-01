package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class UpcomingJobDto {
    private Long jobId;
    private String jobLabel;
    private String outputFormats;
    private String status;
    private String nextFireTime;
}
