package com.jasper.scheduler.service;

import com.jasper.scheduler.dto.ScheduleJobDto;
import com.jasper.scheduler.mapper.ScheduleJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleRunner.class);

    private final ScheduleJobMapper jobMapper;
    private final JasperApiService jasperApiService;

    public ScheduleRunner(ScheduleJobMapper jobMapper, JasperApiService jasperApiService) {
        this.jobMapper = jobMapper;
        this.jasperApiService = jasperApiService;
    }

    /**
     * 1분마다 ACTIVE 상태의 Job을 조회하여 실행 시각이 된 건을 실행
     */
    @Scheduled(fixedDelay = 60000)
    public void runScheduledJobs() {
        List<ScheduleJobDto> activeJobs = jobMapper.findByStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();

        for (ScheduleJobDto job : activeJobs) {
            try {
                if (shouldFire(job, now)) {
                    log.info("Firing job: {} ({})", job.getJobLabel(), job.getJobId());
                    jasperApiService.executeJob(job.getJobId(), "SCHEDULE");
                }
            } catch (Exception e) {
                log.error("Error checking job {}: {}", job.getJobId(), e.getMessage());
            }
        }
    }

    private boolean shouldFire(ScheduleJobDto job, LocalDateTime now) {
        if ("CALENDAR".equals(job.getTriggerType()) && job.getCronExpression() != null) {
            try {
                CronExpression cron = CronExpression.parse(job.getCronExpression());
                LocalDateTime next = cron.next(now.minusMinutes(1));
                return next != null
                    && !next.isAfter(now)
                    && next.isAfter(now.minusMinutes(1));
            } catch (Exception e) {
                log.warn("Invalid cron expression for job {}: {}", job.getJobId(), job.getCronExpression());
            }
        }
        return false;
    }
}
