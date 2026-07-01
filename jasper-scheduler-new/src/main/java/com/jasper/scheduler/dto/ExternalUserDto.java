package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExternalUserDto {
    private String contactEmail;
    private String company;
    private String partnerCode;
    private String role;
    private boolean active;          // isActive → active: Jackson JSON 키 "active", MyBatis setter setActive()
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
