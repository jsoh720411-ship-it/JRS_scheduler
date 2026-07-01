package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.DailyStatDto;
import com.jasper.scheduler.dto.ExecutionSummaryDto;
import com.jasper.scheduler.dto.UpcomingJobDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

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
