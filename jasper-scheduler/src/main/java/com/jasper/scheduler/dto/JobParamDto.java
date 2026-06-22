package com.jasper.scheduler.dto;

import lombok.Data;

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
