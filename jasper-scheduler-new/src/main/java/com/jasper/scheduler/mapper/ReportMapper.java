package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.ParamDefDto;
import com.jasper.scheduler.dto.ReportDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReportMapper {
    List<ReportDto> findAll();
    ReportDto findById(@Param("reportId") Long reportId);
    int insert(ReportDto dto);
    int update(ReportDto dto);
    int delete(@Param("reportId") Long reportId);

    List<ParamDefDto> findParams(@Param("reportId") Long reportId);
    int insertParam(ParamDefDto dto);
    int deleteParam(@Param("paramDefId") Long paramDefId);
    int deleteParamsByReportId(@Param("reportId") Long reportId);
}
