package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.JobParamDto;
import com.jasper.scheduler.dto.RecipientExternalDto;
import com.jasper.scheduler.dto.RecipientInternalDto;
import com.jasper.scheduler.dto.ScheduleJobDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ScheduleJobMapper {
    List<ScheduleJobDto> findAll();
    List<ScheduleJobDto> findByStatus(@Param("status") String status);
    ScheduleJobDto findById(@Param("jobId") Long jobId);

    int insert(ScheduleJobDto job);
    int update(ScheduleJobDto job);
    int updateStatus(@Param("jobId") Long jobId, @Param("status") String status);
    int updateJasperJobId(@Param("jobId") Long jobId, @Param("jasperJobId") String jasperJobId);
    int delete(@Param("jobId") Long jobId);

    int insertTrigger(ScheduleJobDto job);
    int updateTrigger(ScheduleJobDto job);
    int deleteTrigger(@Param("jobId") Long jobId);

    int insertNotification(ScheduleJobDto job);
    int updateNotification(ScheduleJobDto job);
    int deleteNotification(@Param("jobId") Long jobId);

    int insertRecipientInternal(RecipientInternalDto r);
    int deleteRecipientInternalByNotification(@Param("notificationId") Long notificationId);
    List<RecipientInternalDto> findInternalRecipients(@Param("notificationId") Long notificationId);

    int insertRecipientExternal(RecipientExternalDto r);
    int deleteRecipientExternalByNotification(@Param("notificationId") Long notificationId);
    List<RecipientExternalDto> findExternalRecipients(@Param("notificationId") Long notificationId);

    int insertJobParam(JobParamDto p);
    int deleteJobParamsByJobId(@Param("jobId") Long jobId);
    List<JobParamDto> findJobParams(@Param("jobId") Long jobId);
}

