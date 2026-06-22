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
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class JasperApiService {

    private static final Logger log = LoggerFactory.getLogger(JasperApiService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            // 1. 수신자 이메일 수집
            List<String> toAddresses  = new ArrayList<>();
            List<String> ccAddresses  = new ArrayList<>();
            List<String> bccAddresses = new ArrayList<>();
            collectEmails(job, toAddresses, ccAddresses, bccAddresses);

            // 2. API Body 조립
            Map<String, Object> body = buildApiBody(job, toAddresses, ccAddresses, bccAddresses);

            // 3. Jasper REST API 호출
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = config.getJasperUrl() + "/rest_v2/jobs";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

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
    private void collectEmails(ScheduleJobDto job,
                                List<String> toList, List<String> ccList, List<String> bccList) {

        List<RecipientInternalDto> internals =
            jobMapper.findInternalRecipients(job.getNotificationId());

        for (RecipientInternalDto r : internals) {
            List<String> emails;
            if ("BY_ROLE".equals(r.getRecipientMode())) {
                emails = internalUserMapper.findEmailsByRole(r.getRole());
            } else {
                emails = internalUserMapper.findEmailsByCompany(r.getCompany());
            }
            addToList(r.getRecipientType().trim(), emails, toList, ccList, bccList);
        }

        List<RecipientExternalDto> externals =
            jobMapper.findExternalRecipients(job.getNotificationId());

        for (RecipientExternalDto r : externals) {
            addToList(r.getRecipientType().trim(),
                Collections.singletonList(r.getContactEmail()), toList, ccList, bccList);
        }
    }

    private void addToList(String type, List<String> emails,
                            List<String> to, List<String> cc, List<String> bcc) {
        if ("TO".equals(type))  to.addAll(emails);
        else if ("CC".equals(type))  cc.addAll(emails);
        else if ("BCC".equals(type)) bcc.addAll(emails);
    }

    // ── API Body 조립 ──────────────────────────────────────────
    private Map<String, Object> buildApiBody(ScheduleJobDto job,
                                              List<String> to, List<String> cc, List<String> bcc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label",              job.getJobLabel());
        body.put("description",        job.getJobDescription());
        body.put("baseOutputFilename", job.getBaseOutputFilename());
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
        body.put("trigger", buildTrigger(job));

        // repositoryDestination
        Map<String, Object> repo = new LinkedHashMap<>();
        repo.put("folderURI",           job.getRepoFolderUri());
        repo.put("saveToRepository",    job.isRepoSaveToRepository());
        repo.put("overwriteFiles",      job.isRepoOverwriteFiles());
        repo.put("sequentialFilenames", job.isRepoSequentialFilenames());
        body.put("repositoryDestination", repo);

        // mailNotification
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("subject",     job.getSubject());
        mail.put("messageText", job.getMessageText());
        mail.put("resultSendType",               job.getResultSendType());
        mail.put("skipEmptyReports",             job.isSkipEmptyReports());
        mail.put("skipNotificationWhenJobFails", job.isSkipNotifOnFail());
        mail.put("includingStackTraceWhenJobFails", job.isIncludeStacktraceOnFail());

        if (!to.isEmpty())  mail.put("toAddresses",  Collections.singletonMap("address", to));
        if (!cc.isEmpty())  mail.put("ccAddresses",  Collections.singletonMap("address", cc));
        if (!bcc.isEmpty()) mail.put("bccAddresses", Collections.singletonMap("address", bcc));

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
    private Map<String, Object> buildTrigger(ScheduleJobDto job) {
        Map<String, Object> trigger = new LinkedHashMap<>();
        if ("CALENDAR".equals(job.getTriggerType())) {
            Map<String, Object> cal = new LinkedHashMap<>();
            cal.put("timezone",         job.getTimezone());
            cal.put("startType",        job.getStartType());
            cal.put("misfireInstruction", job.getMisfireInstruction());
            if (job.getStartDate() != null) cal.put("startDate", job.getStartDate());
            if (job.getEndDate()   != null) cal.put("endDate",   job.getEndDate());
            cal.put("cronExpression", job.getCronExpression());
            trigger.put("calendarTrigger", cal);
        } else {
            Map<String, Object> simple = new LinkedHashMap<>();
            simple.put("timezone",                job.getTimezone());
            simple.put("startType",               job.getStartType());
            simple.put("occurrenceCount",         job.getOccurrenceCount());
            simple.put("recurrenceInterval",      job.getRecurrenceInterval());
            simple.put("recurrenceIntervalUnit",  job.getRecurrenceIntervalUnit());
            simple.put("misfireInstruction",      job.getMisfireInstruction());
            trigger.put("simpleTrigger", simple);
        }
        return trigger;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String creds = config.getJasperUsername() + ":" + config.getJasperPassword();
        String encoded = Base64.getEncoder()
            .encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    private void saveMailSendLogs(Long execId, List<String> emails,
                                   String type, String userType) {
        for (String email : emails) {
            executionLogMapper.insertMailSendLog(execId, email, type, userType, "SENT", null);
        }
    }
}
