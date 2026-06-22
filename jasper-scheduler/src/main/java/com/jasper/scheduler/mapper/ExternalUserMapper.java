package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.ExternalUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

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
