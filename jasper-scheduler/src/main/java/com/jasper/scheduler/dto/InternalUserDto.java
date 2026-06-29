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

    // 수정 시 원래 행(PK)을 식별하기 위한 필드.
    // company/role을 바꾸는 수정은 PK가 바뀌는 것이므로 컨트롤러에서
    // delete(origEmail, origCompany, origRole) + insert(새 값) 으로 처리한다.
    private String origEmail;
    private String origCompany;
    private String origRole;
}
