# jasper-scheduler

JasperReports Server와 연동하여 리포트를 정기적으로 이메일 발송하는
Spring Boot 기반 스케줄링 관리 시스템입니다.

## 핵심 설계

1. **Jasper Server 자체 스케줄러(Quartz)는 사용하지 않습니다.**
   반복 주기 판단은 이 애플리케이션의 `ScheduleRunner`가 1분마다 폴링하며
   직접 수행하고, Jasper에는 매번 "1회 즉시 실행" 트리거만 전송합니다.
   (이중 스케줄링/중복 발송 방지)

2. **Jasper REST API 호출은 PUT 메서드를 사용합니다.**
   `/rest_v2/jobs` 리소스 생성은 Jasper 공식 스펙상 PUT이며, 이 프로젝트는
   실제 curl로 검증된 요청 형식을 기준으로 구현되어 있습니다.

3. **외부 수신자는 두 가지 모드를 지원합니다.**
   - `DIRECT`: 특정 이메일 1건을 직접 지정
   - `BY_PARTNER_CODE`: 파트너 코드에 속한 `EXTERNAL_USER` 전체를 실행 시점에
     동적으로 조회하여 발송 (담당자가 바뀌어도 Job을 수정할 필요 없음)

## 시작하기

### 1. DB 생성
`src/main/resources/db/jasper_report_email_ddl.sql` 을 MS SQL Server에서 1회 실행합니다.
(IF OBJECT_ID(...) IS NULL 로 감싸져 있어 재실행해도 안전합니다.)

### 2. 설정 파일 작성
`src/main/resources/application.yml.example` 을 복사해 `application.yml` 로 만들고
DB 접속정보와 Jasper Server 접속정보를 채웁니다.
(`application.yml`은 `.gitignore`에 포함되어 git에는 올라가지 않습니다.)

### 3. 실행
```
mvn spring-boot:run
```

### 4. 로그인
최초 기동 시 기본 관리자 계정이 자동 생성됩니다.
```
admin / admin1234
```
운영 환경에서는 최초 로그인 후 반드시 비밀번호를 변경하세요.

## 주요 기능
- 리포트(Report) 등록 및 파라미터 정의
- 스케줄 Job 등록 (SIMPLE 반복주기 / CALENDAR cron 모두 지원)
- 내부 수신자: BY_ROLE / BY_COMPANY 동적 그룹 발송
- 외부 수신자: DIRECT / BY_PARTNER_CODE 동적 그룹 발송
- Job 복사 기능 (같은 리포트를 다른 시각/수신자로 여러 Job 만들 때)
- 즉시 실행, 실행 이력/재전송, 대시보드
