package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportDto {
    private Long reportId;
    private String reportName;
    private String jasperReportUri;
    private String description;
    private boolean isActive;
    private LocalDateTime createdAt;
    private List<ParamDefDto> params;
}
