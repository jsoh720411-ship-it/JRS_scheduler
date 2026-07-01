package com.jasper.scheduler.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {
    String findPasswordHashById(@Param("adminId") String adminId);
    void updateLastLogin(@Param("adminId") String adminId);
}
