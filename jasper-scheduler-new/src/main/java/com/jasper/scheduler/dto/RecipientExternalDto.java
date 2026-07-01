package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class RecipientExternalDto {
    private Long   recipientId;
    private Long   notificationId;

    // DIRECT: contactEmail에 특정 이메일 지정
    // BY_PARTNER_CODE: partnerCode로 EXTERNAL_USER를 동적 조회 → 전체 발송
    private String recipientMode;   // DIRECT / BY_PARTNER_CODE

    private String contactEmail;    // DIRECT 전용
    private String partnerCode;     // BY_PARTNER_CODE 전용

    private String recipientType;   // TO / CC / BCC
}
