package com.jasper.scheduler.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InternalUserDto {
    private String email;
    private String company;
    private String role;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
