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

    // 한 이메일이 여러 role/company를 가질 수 있어 List로 반환
    List<InternalUserDto> findByEmail(@Param("email") String email);

    int insert(InternalUserDto dto);

    // PK가 (email, company, role) 복합키이므로 원래 행 식별용 origEmail/origCompany/origRole 필요
    int update(InternalUserDto dto);

    int delete(@Param("email") String email,
              @Param("company") String company,
              @Param("role") String role);

    // 사람 전체(모든 role) 삭제
    int deleteAllByEmail(@Param("email") String email);

    List<String> findEmailsByRole(@Param("role") String role);
    List<String> findEmailsByCompany(@Param("company") String company);
}
