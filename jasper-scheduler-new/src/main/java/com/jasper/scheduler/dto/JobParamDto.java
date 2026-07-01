package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class JobParamDto {
    private Long paramId;
    private Long jobId;
    private Long paramDefId;
    private String paramName;
    private String paramValue;
    private boolean dynamic;         // isDynamic → dynamic: 동일한 이유
    private String dynamicExpr;
}
