package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExternalUserDto {
    private String contactEmail;
    private String company;
    private String partnerCode;
    private String role;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
