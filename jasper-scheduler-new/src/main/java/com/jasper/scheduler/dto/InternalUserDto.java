package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InternalUserDto {
    private String email;
    private String company;
    private String role;
    private boolean active;          // isActive → active: Jackson JSON 키 "active", MyBatis setter setActive()
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String origEmail;
    private String origCompany;
    private String origRole;
}
