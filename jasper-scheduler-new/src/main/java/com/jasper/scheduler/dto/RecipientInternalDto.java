package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class RecipientInternalDto {
    private Long recipientId;
    private Long notificationId;
    private String recipientMode;   // BY_ROLE / BY_COMPANY
    private String role;
    private String company;
    private String recipientType;   // TO / CC / BCC
}
