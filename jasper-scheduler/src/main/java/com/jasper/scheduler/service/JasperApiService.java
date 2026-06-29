package com.jasper.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasper.scheduler.config.RestTemplateConfig;
import com.jasper.scheduler.dto.*;
import com.jasper.scheduler.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class JasperApiService {

    private static final Logger log = LoggerFactory.getLogger(JasperApiService.class);

    private final RestTemplate restTemplate;
    private final RestTemplateConfig config;
    private final ScheduleJobMapper jobMapper;
    private final InternalUserMapper internalUserMapper;
    private final ExecutionLogMapper executionLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JasperApiService(RestTemplate restTemplate, RestTemplateConfig config,
                            ScheduleJobMapper jobMapper, InternalUserMapper internalUserMapper,
                            ExecutionLogMapper executionLogMapper) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.jobMapper = jobMapper;
        this.internalUserMapper = internalUserMapper;
        this.executionLogMapper = executionLogMapper;
    }

    // ── Jasper API 호출 (Job 생성/재전송) ─────────────────────
    public void executeJob(Long jobId, String triggeredBy) {
        ScheduleJobDto job = jobMapper.findById(jobId);
        if (job == null) {
            log.warn("Job not found: {}", jobId);
            return;
        }

        ExecutionSummaryDto execLog = new ExecutionSummaryDto();
        execLog.setJobId(jobId);
        execLog.setJasperFireTime(LocalDateTime.now());
        execLog.setTriggeredBy(triggeredBy);

        try {
            // 1. 수신자 이메일 수집 (Set으로 중복 자동 제거, 순서 보존)
            Set<String> toAddresses  = new LinkedHashSet<>();
            Set<String> ccAddresses  = new LinkedHashSet<>();
            Set<String> bccAddresses = new LinkedHashSet<>();
            collectEmails(job, toAddresses, ccAddresses, bccAddresses);

            // 2. API Body 조립
            Map<String, Object> body = buildApiBody(job, toAddresses, ccAddresses, bccAddresses);

            // 3. Jasper REST API 호출
            // 주의: Jasper REST v2의 jobs 리소스는 생성도 PUT을 사용한다 (POST 아님 — 공식 문서/검증된 curl 기준)
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = config.getJasperUrl() + "/rest_v2/jobs";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);

            String jasperJobId = response.getBody() != null
                ? String.valueOf(response.getBody().get("id")) : null;
            jobMapper.updateJasperJobId(jobId, jasperJobId);

            // 4. 성공 이력 저장
            execLog.setExecutionStatus("SUCCESS");
            Long execId = executionLogMapper.insertExecution(execLog);

            // 5. 수신자별 발송 로그
            saveMailSendLogs(execId, toAddresses, "TO ", "INTERNAL");
            saveMailSendLogs(execId, ccAddresses, "CC ", "INTERNAL");
            saveMailSendLogs(execId, bccAddresses, "BCC", "INTERNAL");

            log.info("Job {} executed successfully. jasperJobId={}", jobId, jasperJobId);

        } catch (Exception e) {
            log.error("Job {} execution failed: {}", jobId, e.getMessage(), e);
            execLog.setExecutionStatus("FAILED");
            execLog.setErrorMessage(e.getMessage());
            executionLogMapper.insertExecution(execLog);
        }
    }

    // ── 수신자 이메일 수집 ─────────────────────────────────────
    // 같은 사람이 여러 role/company 규칙에 동시에 걸려도 Set 덕분에 한 번만 들어간다.
    // 또한 TO > CC > BCC 우선순위로, 같은 사람이 여러 카테고리에 걸려도 한 곳에만 남긴다.
    private void collectEmails(ScheduleJobDto job,
                                Set<String> toList, Set<String> ccList, Set<String> bccList) {

        List<RecipientInternalDto> internals =
            jobMapper.findInternalRecipients(job.getNotificationId());

        for (RecipientInternalDto r : internals) {
            List<String> emails;
            if ("BY_ROLE".equals(r.getRecipientMode())) {
                emails = internalUserMapper.findEmailsByRole(r.getRole());
            } else {
                emails = internalUserMapper.findEmailsByCompany(r.getCompany());
            }
            addToSet(r.getRecipientType().trim(), emails, toList, ccList, bccList);
        }

        List<RecipientExternalDto> externals =
            jobMapper.findExternalRecipients(job.getNotificationId());

        for (RecipientExternalDto r : externals) {
            addToSet(r.getRecipientType().trim(),
                Collections.singletonList(r.getContactEmail()), toList, ccList, bccList);
        }
    }

    private void addToSet(String type, List<String> emails,
                           Set<String> to, Set<String> cc, Set<String> bcc) {
        for (String email : emails) {
            // TO에 이미 있으면 CC/BCC에 굳이 또 넣지 않는다 (우선순위: TO > CC > BCC)
            if (to.contains(email)) continue;
            if ("TO".equals(type)) {
                to.add(email);
                cc.remove(email);
                bcc.remove(email);
            } else if ("CC".equals(type)) {
                if (!cc.contains(email)) cc.add(email);
            } else if ("BCC".equals(type)) {
                if (!cc.contains(email)) bcc.add(email);
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
        String[] formats = job.getOutputFormats().split(",");
        Map<String, Object> outputFormats = new HashMap<>();
        outputFormats.put("outputFormat", formats);
        body.put("outputFormats", outputFormats);

        // source
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("reportUnitURI", job.getJasperReportUri());
        Map<String, Object> paramValues = buildParamValues(job.getJobId());
        if (!paramValues.isEmpty()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("parameterValues", paramValues);
            source.put("parameters", parameters);
        }
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

    // ── 파라미터 조립 (동적 날짜 계산 포함) ────────────────────
    private Map<String, Object> buildParamValues(Long jobId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<JobParamDto> params = jobMapper.findJobParams(jobId);
        for (JobParamDto p : params) {
            String value = p.isDynamic()
                ? resolveDynamicExpr(p.getDynamicExpr())
                : p.getParamValue();
            result.put(p.getParamName(), new String[]{value});
        }
        return result;
    }

    private String resolveDynamicExpr(String expr) {
        LocalDateTime now = LocalDateTime.now();
        switch (expr.toUpperCase()) {
            case "TODAY":        return now.toLocalDate().toString();
            case "TODAY-1D":     return now.minusDays(1).toLocalDate().toString();
            case "MONTH_START":  return now.withDayOfMonth(1).toLocalDate().toString();
            case "MONTH_END":    return now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).toLocalDate().toString();
            case "PREV_MONTH_START": return now.minusMonths(1).withDayOfMonth(1).toLocalDate().toString();
            case "PREV_MONTH_END":
                LocalDateTime prev = now.minusMonths(1);
                return prev.withDayOfMonth(prev.toLocalDate().lengthOfMonth()).toLocalDate().toString();
            default: return expr;
        }
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

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String creds = config.getJasperUsername() + ":" + config.getJasperPassword();
        String encoded = Base64.getEncoder()
            .encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encoded);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private void saveMailSendLogs(Long execId, Collection<String> emails,
                                   String type, String userType) {
        for (String email : emails) {
            executionLogMapper.insertMailSendLog(execId, email, type, userType, "SENT", null);
        }
    }
}
