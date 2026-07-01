package com.jasper.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasper.scheduler.config.RestTemplateConfig;
import com.jasper.scheduler.dto.ExecutionSummaryDto;
import com.jasper.scheduler.dto.JobParamDto;
import com.jasper.scheduler.dto.RecipientExternalDto;
import com.jasper.scheduler.dto.RecipientInternalDto;
import com.jasper.scheduler.dto.ScheduleJobDto;
import com.jasper.scheduler.mapper.ExecutionLogMapper;
import com.jasper.scheduler.mapper.ExternalUserMapper;
import com.jasper.scheduler.mapper.InternalUserMapper;
import com.jasper.scheduler.mapper.ScheduleJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JasperReports Server REST API 연동 서비스.
 *
 * 중요한 설계 원칙 2가지:
 *
 * ① HTTP 메서드는 PUT을 사용한다.
 *    Jasper REST v2의 /rest_v2/jobs 리소스는 생성도 PUT 메서드를 쓴다
 *    (공식 문서 및 curl 검증 기준). POST가 아니다.
 *
 * ② Jasper Server 자체 스케줄러(Quartz)는 사용하지 않는다.
 *    반복 주기/실행 시점 판단은 전부 ScheduleRunner(자바)가 직접 수행하고,
 *    Jasper에는 호출 시점마다 "1회만 즉시 실행"하는 단발성 트리거만 보낸다.
 *    이렇게 해야 우리 앱과 Jasper가 동시에 반복 발송하는 이중 스케줄링을 막을 수 있다.
 */
@Service
public class JasperApiService {

    private static final Logger log = LoggerFactory.getLogger(JasperApiService.class);

    private final RestTemplate restTemplate;
    private final RestTemplateConfig config;
    private final ScheduleJobMapper jobMapper;
    private final InternalUserMapper internalUserMapper;
    private final ExternalUserMapper externalUserMapper;
    private final ExecutionLogMapper executionLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JasperApiService(RestTemplate restTemplate, RestTemplateConfig config,
                            ScheduleJobMapper jobMapper, InternalUserMapper internalUserMapper,
                            ExternalUserMapper externalUserMapper,
                            ExecutionLogMapper executionLogMapper) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.jobMapper = jobMapper;
        this.internalUserMapper = internalUserMapper;
        this.externalUserMapper = externalUserMapper;
        this.executionLogMapper = executionLogMapper;
    }

    /**
     * Job을 실행한다 (리포트 생성 + 메일 발송).
     * triggeredBy: "SCHEDULE"(ScheduleRunner 자동 폴링) 또는 "MANUAL"(즉시실행 버튼)
     */
    public void executeJob(Long jobId, String triggeredBy) {
        ScheduleJobDto job = jobMapper.findById(jobId);
        if (job == null) {
            log.warn("Job {} not found - 실행을 건너뜁니다.", jobId);
            return;
        }

        ExecutionSummaryDto exec = new ExecutionSummaryDto();
        exec.setJobId(jobId);
        exec.setJasperFireTime(LocalDateTime.now());
        exec.setTriggeredBy(triggeredBy);
        exec.setExecutionStatus("PENDING");
        executionLogMapper.insertExecution(exec);

        try {
            // 1. 수신자 수집 (내부 + 외부, TO > CC > BCC 우선순위로 중복 제거)
            Set<String> toList = new LinkedHashSet<>();
            Set<String> ccList = new LinkedHashSet<>();
            Set<String> bccList = new LinkedHashSet<>();
            collectRecipients(job, toList, ccList, bccList);

            if (toList.isEmpty() && ccList.isEmpty() && bccList.isEmpty()) {
                throw new IllegalStateException("수신자가 한 명도 없습니다. (BY_ROLE/BY_COMPANY/BY_PARTNER_CODE 조건을 확인하세요)");
            }

            // 2. API body 조립
            Map<String, Object> body = buildApiBody(job, toList, ccList, bccList);

            // 3. Jasper REST API 호출
            // 주의: Jasper REST v2의 jobs 리소스는 생성도 PUT을 사용한다 (POST 아님 — 공식 문서/검증된 curl 기준)
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = config.getJasperUrl() + "/rest_v2/jobs";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);

            String jasperJobId = null;
            if (response.getBody() != null && response.getBody().get("id") != null) {
                jasperJobId = String.valueOf(response.getBody().get("id"));
            }

            // 4. 성공 기록
            exec.setExecutionStatus("SUCCESS");
            executionLogMapper.updateExecution(exec);

            for (String email : toList)  logMailSend(exec.getExecutionId(), email, "TO ", "SENT", null);
            for (String email : ccList)  logMailSend(exec.getExecutionId(), email, "CC ", "SENT", null);
            for (String email : bccList) logMailSend(exec.getExecutionId(), email, "BCC", "SENT", null);

            log.info("Job {} 실행 성공 (jasperJobId={}, 수신자 TO:{} CC:{} BCC:{})",
                jobId, jasperJobId, toList.size(), ccList.size(), bccList.size());

        } catch (Exception e) {
            log.error("Job {} 실행 실패: {}", jobId, e.getMessage(), e);
            exec.setExecutionStatus("FAILED");
            exec.setErrorMessage(e.getMessage());
            executionLogMapper.updateExecution(exec);
            throw new RuntimeException("Jasper Job 실행 실패: " + e.getMessage(), e);
        }
    }

    private void logMailSend(Long executionId, String email, String type, String status, String error) {
        try {
            executionLogMapper.insertMailSendLog(executionId, email, type, "MIXED", status, error);
        } catch (Exception e) {
            log.warn("메일 발송 로그 기록 실패: {}", e.getMessage());
        }
    }

    // ── 수신자 수집 ─────────────────────────────────────────────
    private void collectRecipients(ScheduleJobDto job,
                                    Set<String> toList, Set<String> ccList, Set<String> bccList) {

        // 내부 수신자 (BY_ROLE / BY_COMPANY → 동적 조회)
        List<RecipientInternalDto> internals = jobMapper.findInternalRecipients(job.getNotificationId());
        for (RecipientInternalDto r : internals) {
            List<String> emails;
            if ("BY_ROLE".equals(r.getRecipientMode())) {
                emails = internalUserMapper.findEmailsByRole(r.getRole());
            } else {
                emails = internalUserMapper.findEmailsByCompany(r.getCompany());
            }
            addToSet(r.getRecipientType().trim(), emails, toList, ccList, bccList);
        }

        // 외부 수신자 (DIRECT / BY_PARTNER_CODE)
        List<RecipientExternalDto> externals = jobMapper.findExternalRecipients(job.getNotificationId());
        for (RecipientExternalDto r : externals) {
            List<String> emails;
            if ("BY_PARTNER_CODE".equals(r.getRecipientMode())) {
                // 파트너 코드에 속한 활성 외부 사용자 전체를 동적으로 조회
                emails = externalUserMapper.findEmailsByPartnerCode(r.getPartnerCode());
            } else {
                // DIRECT: 지정된 이메일 한 건
                emails = Collections.singletonList(r.getContactEmail());
            }
            addToSet(r.getRecipientType().trim(), emails, toList, ccList, bccList);
        }
    }

    // TO > CC > BCC 우선순위로 중복 제거: 이미 상위 등급에 있는 이메일은 하위 등급에 추가하지 않는다.
    private void addToSet(String type, List<String> emails,
                          Set<String> toList, Set<String> ccList, Set<String> bccList) {
        for (String email : emails) {
            if (email == null || email.trim().isEmpty()) continue;
            String e = email.trim();
            if (toList.contains(e) || ccList.contains(e) || bccList.contains(e)) continue;

            switch (type) {
                case "TO":  toList.add(e);  break;
                case "CC":  ccList.add(e);  break;
                case "BCC": bccList.add(e); break;
                default:    log.warn("알 수 없는 recipientType: {}", type);
            }
        }
    }

    // ── API Body 조립 ──────────────────────────────────────────
    private Map<String, Object> buildApiBody(ScheduleJobDto job,
                                              Collection<String> to, Collection<String> cc, Collection<String> bcc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version",            0);   // Jasper 리소스 표준 버전 필드 (생성 시 항상 0)
        body.put("label",              job.getJobLabel());
        body.put("description",        job.getJobDescription());
        body.put("baseOutputFilename", job.getBaseOutputFilename());
        body.put("outputLocale",       config.getOutputLocale());
        body.put("outputTimeZone",     job.getOutputTimezone());

        // outputFormats
        String[] formats = job.getOutputFormats() != null
            ? job.getOutputFormats().split(",") : new String[]{"PDF"};
        body.put("outputFormats", Collections.singletonMap("outputFormat", formats));

        // source
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("reportUnitURI", job.getJasperReportUri());
        Map<String, Object> paramValues = new LinkedHashMap<>();
        List<JobParamDto> params = jobMapper.findJobParams(job.getJobId());
        if (params != null) {
            for (JobParamDto p : params) {
                String value = p.isDynamic() ? resolveDynamicExpr(p.getDynamicExpr()) : p.getParamValue();
                paramValues.put(p.getParamName(), Collections.singletonList(value));
            }
        }
        source.put("parameters", Collections.singletonMap("parameterValues", paramValues));
        body.put("source", source);

        // trigger
        // Jasper Server 자체 스케줄러(Quartz)는 사용하지 않는다.
        // 반복/주기 판단은 ScheduleRunner(자바 프로그램)가 직접 수행하고,
        // Jasper에는 호출 시점에 "1회만 즉시 실행"하는 단발성 트리거만 전달한다.
        body.put("trigger", buildImmediateTrigger(job));

        // repositoryDestination
        Map<String, Object> repo = new LinkedHashMap<>();
        repo.put("version",              0);
        repo.put("folderURI",            job.getRepoFolderUri());
        repo.put("saveToRepository",     job.isRepoSaveToRepository());
        repo.put("overwriteFiles",       job.isRepoOverwriteFiles());
        repo.put("sequentialFilenames",  job.isRepoSequentialFilenames());
        repo.put("usingDefaultReportOutputFolderURI", false);
        body.put("repositoryDestination", repo);

        // mailNotification
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("version",     0);
        mail.put("subject",     job.getSubject());
        mail.put("messageText", job.getMessageText());
        mail.put("resultSendType",               job.getResultSendType());
        mail.put("skipEmptyReports",             job.isSkipEmptyReports());
        mail.put("skipNotificationWhenJobFails", job.isSkipNotifOnFail());
        mail.put("includingStackTraceWhenJobFails", job.isIncludeStacktraceOnFail());

        // 검증된 요청과 동일하게, 비어있어도 키 자체는 항상 포함 (예: "bccAddresses": {"address": []})
        mail.put("toAddresses",  Collections.singletonMap("address", to));
        mail.put("ccAddresses",  Collections.singletonMap("address", cc));
        mail.put("bccAddresses", Collections.singletonMap("address", bcc));

        body.put("mailNotification", mail);
        return body;
    }

    // ── Trigger 조립 ───────────────────────────────────────────
    // 로컬 DB의 트리거 타입(SIMPLE/CALENDAR)이 무엇이든, Jasper에는 항상
    // "지금 1회만 즉시 실행"하는 동일한 단발성 simpleTrigger만 보낸다.
    // 실제 반복 스케줄링은 ScheduleRunner가 자바 코드로 직접 판단해서
    // executeJob()을 그때그때 호출하는 방식으로 처리하기 때문에,
    // Jasper 쪽에 별도의 반복 주기를 등록할 필요가 없다 (중복 발송 방지).
    private Map<String, Object> buildImmediateTrigger(ScheduleJobDto job) {
        Map<String, Object> simple = new LinkedHashMap<>();
        simple.put("version",                0);
        simple.put("timezone",               job.getTimezone());
        simple.put("startType",              1);   // 1 = 즉시(NOW)
        simple.put("occurrenceCount",        1);   // 1회만 실행 (Jasper가 스스로 반복하지 않음)
        simple.put("recurrenceInterval",     1);   // occurrenceCount=1이라 실제로는 사용되지 않음
        simple.put("recurrenceIntervalUnit", "DAY");
        simple.put("misfireInstruction",     0);

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("simpleTrigger", simple);
        return trigger;
    }

    // 동적 날짜 파라미터 표현식 해석 (TODAY-1D, MONTH_START 등)
    private String resolveDynamicExpr(String expr) {
        if (expr == null) return null;
        LocalDateTime now = LocalDateTime.now();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        switch (expr) {
            case "TODAY":         return now.format(fmt);
            case "TODAY-1D":      return now.minusDays(1).format(fmt);
            case "TODAY+1D":      return now.plusDays(1).format(fmt);
            case "MONTH_START":   return now.withDayOfMonth(1).format(fmt);
            case "MONTH_END":     return now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).format(fmt);
            case "PREV_MONTH_START":
                return now.minusMonths(1).withDayOfMonth(1).format(fmt);
            case "PREV_MONTH_END":
                LocalDateTime prevMonth = now.minusMonths(1);
                return prevMonth.withDayOfMonth(prevMonth.toLocalDate().lengthOfMonth()).format(fmt);
            default:
                log.warn("알 수 없는 dynamicExpr: {}", expr);
                return now.format(fmt);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String creds = config.getJasperUsername() + ":" + config.getJasperPassword();
        String encoded = Base64.getEncoder()
            .encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encoded);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }
}
