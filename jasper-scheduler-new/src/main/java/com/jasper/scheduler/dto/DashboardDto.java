package com.jasper.scheduler.dto;

import lombok.Data;
import java.util.List;

@Data
public class DashboardDto {
    private int totalSuccess;
    private int totalFailed;
    private int totalPending;
    private int totalSent;
    private List<ExecutionSummaryDto> recentExecutions;
    private List<UpcomingJobDto> upcomingJobs;
    private List<DailyStatDto> dailyStats;
}
