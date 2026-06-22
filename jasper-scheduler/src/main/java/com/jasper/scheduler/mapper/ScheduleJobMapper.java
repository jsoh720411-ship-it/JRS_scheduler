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
