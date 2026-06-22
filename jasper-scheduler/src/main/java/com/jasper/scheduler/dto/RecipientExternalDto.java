package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class RecipientExternalDto {
    private Long recipientId;
    private Long notificationId;
    private String contactEmail;
    private String recipientType;   // TO / CC / BCC
}
