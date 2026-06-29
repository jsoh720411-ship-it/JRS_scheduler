package com.jasper.scheduler.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * 프론트엔드(job/form.html)에서 넘어오는 날짜 문자열을 안전하게 파싱합니다.
 *
 * 지원 포맷:
 *   - "2026-07-01 08:00:00"  (toSqlDateTime()이 만드는 형식)
 *   - "2026-07-01T08:00"     (datetime-local 원본 형식, 혹시 변환 누락 시 대비)
 *   - "" 또는 null           → null 반환 (SQL Server에 NULL로 바인딩됨)
 *
 * 빈 문자열을 그대로 JDBC에 흘려보내면 SQL Server가 타입을 잘못 추측하거나
 * "Conversion failed" 에러를 내므로, 여기서 한 번에 안전하게 걸러줍니다.
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final List<DateTimeFormatter> FORMATS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter fmt : FORMATS) {
            try {
                return LocalDateTime.parse(value, fmt);
            } catch (DateTimeParseException ignored) {
                // 다음 포맷 시도
            }
        }
        // 어떤 포맷으로도 안 맞으면 null 처리 (예외로 등록 전체를 막지 않음)
        return null;
    }
}
