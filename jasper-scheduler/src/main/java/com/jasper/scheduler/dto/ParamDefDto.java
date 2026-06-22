package com.jasper.scheduler.dto;

import lombok.Data;

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
