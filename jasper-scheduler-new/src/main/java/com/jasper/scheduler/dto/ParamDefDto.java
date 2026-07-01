package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class ParamDefDto {
    private Long paramDefId;
    private Long reportId;
    private String paramName;
    private String paramType;
    private boolean required;        // isRequired → required: Lombok/Jackson/MyBatis 네이밍 충돌 방지
    private String defaultValue;
    private int displayOrder;
}
