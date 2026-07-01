package com.jasper.scheduler.dto;

import lombok.Data;

@Data
public class DailyStatDto {
    private String dayLabel;
    private int successCount;
    private int failedCount;
}
