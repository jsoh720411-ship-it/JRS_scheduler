package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

// ── AdminUser ────────────────────────────────────────────────
@Mapper
public interface AdminUserMapper {
    String findPasswordHashById(@Param("adminId") String adminId);
    void updateLastLogin(@Param("adminId") String adminId);
}

// ── InternalUser ─────────────────────────────────────────────
@Mapper
public interface InternalUserMapper {
    List<InternalUserDto> findAll();
    List<InternalUserDto> findByCondition(@Param("company") String company,
                                          @Param("role") String role,
                                          @Param("keyword") String keyword);
    InternalUserDto findByEmail(@Param("email") String email);
    int insert(InternalUserDto dto);
    int update(InternalUserDto dto);
    int delete(@Param("email") String email);
    List<String> findEmailsByRole(@Param("role") String role);
    List<String> findEmailsByCompany(@Param("company") String company);
}

// ── ExternalUser ─────────────────────────────────────────────
@Mapper
public interface ExternalUserMapper {
    List<ExternalUserDto> findAll();
    List<ExternalUserDto> findByCondition(@Param("company") String company,
                                          @Param("partnerCode") String partnerCode,
                                          @Param("keyword") String keyword);
    ExternalUserDto findByEmail(@Param("contactEmail") String contactEmail);
    int insert(ExternalUserDto dto);
    int update(ExternalUserDto dto);
    int delete(@Param("contactEmail") String contactEmail);
}

// ── Report ───────────────────────────────────────────────────
@Mapper
public interface ReportMapper {
    List<ReportDto> findAll();
    ReportDto findById(@Param("reportId") Long reportId);
    int insert(ReportDto dto);
    int update(ReportDto dto);
    int delete(@Param("reportId") Long reportId);

    List<ParamDefDto> findParams(@Param("reportId") Long reportId);
    int insertParam(ParamDefDto dto);
    int updateParam(ParamDefDto dto);
    int deleteParam(@Param("paramDefId") Long paramDefId);
    int deleteParamsByReportId(@Param("reportId") Long reportId);
}

// ── ScheduleJob ──────────────────────────────────────────────
@Mapper
public interface ScheduleJobMapper {
    List<ScheduleJobDto> findAll();
    List<ScheduleJobDto> findByStatus(@Param("status") String status);
    ScheduleJobDto findById(@Param("jobId") Long jobId);
    int insert(ScheduleJobDto dto);
    int update(ScheduleJobDto dto);
    int updateStatus(@Param("jobId") Long jobId, @Param("status") String status);
    int updateJasperJobId(@Param("jobId") Long jobId, @Param("jasperJobId") String jasperJobId);
    int delete(@Param("jobId") Long jobId);

    // Trigger
    int insertTrigger(ScheduleJobDto dto);
    int updateTrigger(ScheduleJobDto dto);
    int deleteTrigger(@Param("jobId") Long jobId);

    // Mail Notification
    int insertNotification(ScheduleJobDto dto);
    int updateNotification(ScheduleJobDto dto);
    int deleteNotification(@Param("jobId") Long jobId);

    // Recipients
    int insertRecipientInternal(RecipientInternalDto dto);
    int insertRecipientExternal(RecipientExternalDto dto);
    int deleteRecipientInternalByNotification(@Param("notificationId") Long notificationId);
    int deleteRecipientExternalByNotification(@Param("notificationId") Long notificationId);
    List<RecipientInternalDto> findInternalRecipients(@Param("notificationId") Long notificationId);
    List<RecipientExternalDto> findExternalRecipients(@Param("notificationId") Long notificationId);

    // Report Params
    int insertJobParam(JobParamDto dto);
    int deleteJobParamsByJobId(@Param("jobId") Long jobId);
    List<JobParamDto> findJobParams(@Param("jobId") Long jobId);
}

// ── Execution Log ────────────────────────────────────────────
@Mapper
public interface ExecutionLogMapper {
    Long insertExecution(ExecutionSummaryDto dto);
    int updateExecution(ExecutionSummaryDto dto);
    List<ExecutionSummaryDto> findRecentExecutions(@Param("limit") int limit);
    List<ExecutionSummaryDto> findByFilter(@Param("status") String status,
                                           @Param("jobId") Long jobId,
                                           @Param("limit") int limit);
    ExecutionSummaryDto findById(@Param("executionId") Long executionId);

    int insertMailSendLog(@Param("executionId") Long executionId,
                          @Param("email") String email,
                          @Param("recipientType") String recipientType,
                          @Param("userType") String userType,
                          @Param("status") String status,
                          @Param("error") String error);

    // Dashboard 집계
    int countTodayByStatus(@Param("status") String status);
    int countPendingJobs();
    int countTotalSentThisMonth();
    List<DailyStatDto> findDailyStats(@Param("days") int days);
    List<UpcomingJobDto> findUpcomingJobs(@Param("limit") int limit);
}
