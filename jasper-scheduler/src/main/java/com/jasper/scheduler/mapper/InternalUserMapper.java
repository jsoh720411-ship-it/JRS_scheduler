package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.InternalUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

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
