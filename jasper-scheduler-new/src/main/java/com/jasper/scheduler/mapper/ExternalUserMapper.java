package com.jasper.scheduler.mapper;

import com.jasper.scheduler.dto.ExternalUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ExternalUserMapper {
    List<ExternalUserDto> findAll();

    ExternalUserDto findByEmail(@Param("contactEmail") String contactEmail);

    List<ExternalUserDto> findByCondition(@Param("company") String company,
                                          @Param("partnerCode") String partnerCode,
                                          @Param("keyword") String keyword);

    // BY_PARTNER_CODE 수신자 모드: 파트너 코드에 속한 활성 사용자 이메일 전체 조회
    List<String> findEmailsByPartnerCode(@Param("partnerCode") String partnerCode);

    // 파트너 코드 목록 (Job 폼의 드롭다운/자동완성용)
    List<String> findDistinctPartnerCodes();

    int insert(ExternalUserDto dto);
    int update(ExternalUserDto dto);
    int delete(@Param("contactEmail") String contactEmail);
}
