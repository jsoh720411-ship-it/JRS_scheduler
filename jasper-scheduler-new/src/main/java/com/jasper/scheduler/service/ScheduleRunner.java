package com.jasper.scheduler.service;

import com.jasper.scheduler.dto.ScheduleJobDto;
import com.jasper.scheduler.mapper.ScheduleJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 1분마다 ACTIVE 상태의 Job들을 폴링하면서 "지금이 발송 시점인가"를 직접 판단하는 스케줄러.
 *
 * Jasper Server 자체 스케줄러(Quartz)는 사용하지 않는다. 이 클래스가 유일한
 * 스케줄링 주체(single source of truth)이며, 발송 시점이라고 판단되면
 * JasperApiService.executeJob()을 호출해 Jasper에 "1회 즉시 실행"을 요청한다.
 */
@Service
public class ScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleRunner.class);

    private final ScheduleJobMapper jobMapper;
    private final JasperApiService jasperApiService;

    public ScheduleRunner(ScheduleJobMapper jobMapper, JasperApiService jasperApiService) {
        this.jobMapper = jobMapper;
        this.jasperApiService = jasperApiService;
    }

    @Scheduled(cron = "0 * * * * *")  // 매분 0초에 실행
    public void poll() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleJobDto> activeJobs = jobMapper.findByStatus("ACTIVE");

        for (ScheduleJobDto job : activeJobs) {
            try {
                if (shouldFire(job, now)) {
                    log.info("Job {} ({}) 발송 시점 도달 - 실행합니다.", job.getJobId(), job.getJobLabel());
                    jasperApiService.executeJob(job.getJobId(), "SCHEDULE");
                }
            } catch (Exception e) {
                log.error("Job {} 폴링 중 오류: {}", job.getJobId(), e.getMessage(), e);
            }
        }
    }

    private boolean shouldFire(ScheduleJobDto job, LocalDateTime now) {
        if ("CALENDAR".equals(job.getTriggerType())) {
            if (job.getCronExpression() == null) {
                return false;
            }
            try {
                CronExpression cron = CronExpression.parse(job.getCronExpression());
                LocalDateTime next = cron.next(now.minusMinutes(1));
                return next != null
                    && !next.isAfter(now)
                    && next.isAfter(now.minusMinutes(1));
            } catch (Exception e) {
                log.warn("Invalid cron expression for job {}: {}", job.getJobId(), job.getCronExpression());
                return false;
            }
        } else if ("SIMPLE".equals(job.getTriggerType())) {
            return shouldFireSimple(job, now);
        }
        return false;
    }

    /**
     * SIMPLE 트리거 실행 시각 판단.
     * - 기준 시작 시각: start_date가 있으면 그것을, 없으면(start_type=1 즉시) Job 생성 시각(created_at)을 기준으로 함
     * - occurrence_count: null이면 1회만, -1이면 무제한, 그 외는 지정 횟수만큼만 반복
     * - 매 폴링(1분)마다 "이번 분 사이에 들어오는 회차가 있는가"를 계산해서 판단 (CALENDAR 트리거와 동일한 1분 윈도우 방식)
     */
    private boolean shouldFireSimple(ScheduleJobDto job, LocalDateTime now) {
        LocalDateTime start = job.getStartDate() != null ? job.getStartDate() : job.getCreatedAt();
        if (start == null) {
            log.warn("Job {}: SIMPLE 트리거의 기준 시작 시각이 없어 실행 여부를 판단할 수 없습니다.", job.getJobId());
            return false;
        }
        if (now.isBefore(start)) {
            return false; // 아직 시작 전
        }
        if (job.getEndDate() != null && now.isAfter(job.getEndDate())) {
            return false; // 종료 시각을 지남
        }

        int interval = (job.getRecurrenceInterval() != null && job.getRecurrenceInterval() > 0)
            ? job.getRecurrenceInterval() : 1;
        Duration step = intervalDuration(job.getRecurrenceIntervalUnit(), interval);
        if (step == null || step.isZero() || step.isNegative()) {
            log.warn("Job {}: 잘못된 recurrenceIntervalUnit '{}' - 실행하지 않음.",
                job.getJobId(), job.getRecurrenceIntervalUnit());
            return false;
        }

        long elapsedMillis = Duration.between(start, now).toMillis();
        long occurrenceIndex = elapsedMillis / step.toMillis();   // 0 = 시작 시각의 첫 실행
        LocalDateTime occurrenceTime = start.plus(step.multipliedBy(occurrenceIndex));

        Integer rawCount = job.getOccurrenceCount();
        int effectiveCount = (rawCount == null) ? 1 : rawCount;  // null이면 "1회만 실행"으로 간주
        boolean unlimited = effectiveCount == -1;                 // DDL 정의: -1 = 무제한
        if (!unlimited && occurrenceIndex >= effectiveCount) {
            return false; // 지정된 반복 횟수를 모두 실행함
        }

        LocalDateTime windowStart = now.minusMinutes(1);
        return !occurrenceTime.isBefore(windowStart) && !occurrenceTime.isAfter(now);
    }

    // DDL 제약상 recurrence_interval_unit은 MINUTE/HOUR/DAY/WEEK만 허용됨
    private Duration intervalDuration(String unit, int interval) {
        if (unit == null) return null;
        switch (unit.toUpperCase()) {
            case "MINUTE": return Duration.ofMinutes(interval);
            case "HOUR":   return Duration.ofHours(interval);
            case "DAY":    return Duration.ofDays(interval);
            case "WEEK":   return Duration.ofDays(7L * interval);
            default:       return null;
        }
    }
}
