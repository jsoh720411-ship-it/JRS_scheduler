-- ============================================================
--  JasperReport Email Scheduling System
--  Database : MS SQL Server
--  Version  : Final
--  Created  : 2026-06-07
-- ============================================================

USE master;
GO

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'JasperReportDB')
BEGIN
    CREATE DATABASE JasperReportDB
    COLLATE Korean_Wansung_CI_AS;
END
GO

USE JasperReportDB;
GO


-- ============================================================
--  ① 관리자 테이블
-- ============================================================

IF OBJECT_ID('dbo.ADMIN_USER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.ADMIN_USER (
        admin_id            VARCHAR(50)     NOT NULL,
        password_hash       VARCHAR(256)    NOT NULL,           -- BCrypt 해시
        admin_name          NVARCHAR(100)   NOT NULL,
        is_active           BIT             NOT NULL DEFAULT 1,
        last_login_at       DATETIME2           NULL,
        created_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_ADMIN_USER PRIMARY KEY (admin_id)
    );
END
GO


-- ============================================================
--  ② 마스터 테이블
-- ============================================================

-- ------------------------------------------------------------
--  INTERNAL_USER : 내부 수신자
--  PK : (email, company, role) 복합키
--  한 사람(email)이 여러 role/company를 가질 수 있어 행이 여러 개 생길 수 있음.
--  예: qrok.choi@samsung.com 이 (C400,KPI_ALL)과 (C400,KPI_RNPS) 둘 다 가질 수 있음.
--  메일 발송 시 중복 수신은 SELECT DISTINCT / UNION으로 제거 (서비스 로직에서 처리)
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.INTERNAL_USER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.INTERNAL_USER (
        email               VARCHAR(200)    NOT NULL,
        company             VARCHAR(20)     NOT NULL,           -- 법인 코드
        role                VARCHAR(50)     NOT NULL,
        is_active           BIT             NOT NULL DEFAULT 1,
        created_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_INTERNAL_USER PRIMARY KEY (email, company, role)
    );
END
GO

-- ------------------------------------------------------------
--  EXTERNAL_USER : 외부 수신자
--  PK : contact_email
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.EXTERNAL_USER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.EXTERNAL_USER (
        contact_email       VARCHAR(200)    NOT NULL,
        company             VARCHAR(20)     NOT NULL,           -- 법인 코드
        partner_code        VARCHAR(20)     NOT NULL,
        role                VARCHAR(50)     NOT NULL,
        is_active           BIT             NOT NULL DEFAULT 1,
        created_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_EXTERNAL_USER PRIMARY KEY (contact_email)
    );
END
GO


-- ============================================================
--  ③ 레포트 테이블
-- ============================================================

-- ------------------------------------------------------------
--  REPORT : Jasper 레포트 마스터
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.REPORT', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.REPORT (
        report_id           BIGINT          NOT NULL IDENTITY(1,1),
        report_name         NVARCHAR(200)   NOT NULL,
        jasper_report_uri   VARCHAR(500)    NOT NULL,           -- source.reportUnitURI
        description         NVARCHAR(500)       NULL,
        is_active           BIT             NOT NULL DEFAULT 1,
        created_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_REPORT PRIMARY KEY (report_id)
    );
END
GO

-- ------------------------------------------------------------
--  REPORT_PARAM_DEF : 레포트 파라미터 정의 (메타)
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.REPORT_PARAM_DEF', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.REPORT_PARAM_DEF (
        param_def_id        BIGINT          NOT NULL IDENTITY(1,1),
        report_id           BIGINT          NOT NULL,
        param_name          VARCHAR(100)    NOT NULL,           -- parameterValues key
        param_type          VARCHAR(20)     NOT NULL,           -- STRING/DATE/NUMBER/BOOLEAN
        is_required         BIT             NOT NULL DEFAULT 0,
        default_value       VARCHAR(200)        NULL,
        display_order       INT             NOT NULL DEFAULT 0,

        CONSTRAINT PK_REPORT_PARAM_DEF  PRIMARY KEY (param_def_id),
        CONSTRAINT FK_RPD_REPORT
            FOREIGN KEY (report_id)     REFERENCES dbo.REPORT (report_id),
        CONSTRAINT UQ_RPD_REPORT_PARAM  UNIQUE (report_id, param_name),
        CONSTRAINT CK_RPD_PARAM_TYPE
            CHECK (param_type IN ('STRING','DATE','NUMBER','BOOLEAN'))
    );
END
GO


-- ============================================================
--  ④ Job 설계 테이블
-- ============================================================

-- ------------------------------------------------------------
--  SCHEDULE_JOB : API body 최상위 + repositoryDestination
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.SCHEDULE_JOB', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.SCHEDULE_JOB (
        job_id                      BIGINT          NOT NULL IDENTITY(1,1),
        report_id                   BIGINT          NOT NULL,

        -- API 최상위 필드
        job_label                   NVARCHAR(200)   NOT NULL,   -- label
        job_description             NVARCHAR(500)       NULL,   -- description
        output_formats              VARCHAR(100)    NOT NULL,   -- "PDF,XLS" 콤마구분
        base_output_filename        VARCHAR(200)    NOT NULL,   -- baseOutputFilename
        output_timezone             VARCHAR(50)     NOT NULL DEFAULT 'Asia/Seoul',

        -- API: repositoryDestination
        repo_folder_uri             VARCHAR(500)        NULL,
        repo_save_to_repository     BIT             NOT NULL DEFAULT 0,
        repo_overwrite_files        BIT             NOT NULL DEFAULT 1,
        repo_sequential_filenames   BIT             NOT NULL DEFAULT 0,

        -- Jasper 서버 응답값
        jasper_job_id               VARCHAR(100)        NULL,

        -- 운영
        status                      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
        created_by                  VARCHAR(50)     NOT NULL,   -- → ADMIN_USER.admin_id
        created_at                  DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at                  DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_SCHEDULE_JOB  PRIMARY KEY (job_id),
        CONSTRAINT FK_SJ_REPORT
            FOREIGN KEY (report_id) REFERENCES dbo.REPORT (report_id),
        CONSTRAINT CK_SJ_STATUS
            CHECK (status IN ('ACTIVE','PAUSED','DISABLED'))
    );
END
GO

-- ------------------------------------------------------------
--  JOB_TRIGGER : API trigger (simpleTrigger / calendarTrigger)
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.JOB_TRIGGER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_TRIGGER (
        trigger_id                  BIGINT          NOT NULL IDENTITY(1,1),
        job_id                      BIGINT          NOT NULL,

        trigger_type                VARCHAR(10)     NOT NULL,   -- SIMPLE / CALENDAR
        timezone                    VARCHAR(50)     NOT NULL DEFAULT 'Asia/Seoul',
        start_type                  TINYINT         NOT NULL DEFAULT 1, -- 1=즉시 2=지정날짜
        start_date                  DATETIME2           NULL,
        end_date                    DATETIME2           NULL,
        misfire_instruction         INT             NOT NULL DEFAULT 0,

        -- SIMPLE 전용
        occurrence_count            INT                 NULL,   -- -1=무제한
        recurrence_interval         INT                 NULL,
        recurrence_interval_unit    VARCHAR(10)         NULL,   -- MINUTE/HOUR/DAY/WEEK

        -- CALENDAR 전용
        cron_expression             VARCHAR(100)        NULL,

        CONSTRAINT PK_JOB_TRIGGER   PRIMARY KEY (trigger_id),
        CONSTRAINT FK_JT_JOB
            FOREIGN KEY (job_id)    REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT UQ_JT_JOB        UNIQUE (job_id),
        CONSTRAINT CK_JT_TRIGGER_TYPE
            CHECK (trigger_type IN ('SIMPLE','CALENDAR')),
        CONSTRAINT CK_JT_INTERVAL_UNIT
            CHECK (recurrence_interval_unit IS NULL
                OR recurrence_interval_unit IN ('MINUTE','HOUR','DAY','WEEK'))
    );
END
GO

-- ------------------------------------------------------------
--  JOB_REPORT_PARAM : API source.parameters.parameterValues
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.JOB_REPORT_PARAM', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_REPORT_PARAM (
        param_id            BIGINT          NOT NULL IDENTITY(1,1),
        job_id              BIGINT          NOT NULL,
        param_def_id        BIGINT          NOT NULL,
        param_value         VARCHAR(500)        NULL,
        is_dynamic          BIT             NOT NULL DEFAULT 0,
        dynamic_expr        VARCHAR(200)        NULL,           -- TODAY-1D, MONTH_START 등

        CONSTRAINT PK_JOB_REPORT_PARAM  PRIMARY KEY (param_id),
        CONSTRAINT FK_JRP_JOB
            FOREIGN KEY (job_id)        REFERENCES dbo.SCHEDULE_JOB     (job_id),
        CONSTRAINT FK_JRP_PARAM_DEF
            FOREIGN KEY (param_def_id)  REFERENCES dbo.REPORT_PARAM_DEF (param_def_id),
        CONSTRAINT UQ_JRP_JOB_PARAM     UNIQUE (job_id, param_def_id)
    );
END
GO


-- ============================================================
--  ⑤ 메일 알림 & 수신자 테이블
-- ============================================================

-- ------------------------------------------------------------
--  JOB_MAIL_NOTIFICATION : API mailNotification 설정
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.JOB_MAIL_NOTIFICATION', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_MAIL_NOTIFICATION (
        notification_id             BIGINT          NOT NULL IDENTITY(1,1),
        job_id                      BIGINT          NOT NULL,

        subject                     NVARCHAR(500)   NOT NULL,
        message_text                NVARCHAR(MAX)       NULL,
        result_send_type            VARCHAR(30)     NOT NULL DEFAULT 'SEND_ATTACHMENT',
        skip_empty_reports          BIT             NOT NULL DEFAULT 1,
        skip_notif_on_fail          BIT             NOT NULL DEFAULT 0,
        include_stacktrace_on_fail  BIT             NOT NULL DEFAULT 0,

        CONSTRAINT PK_JOB_MAIL_NOTIFICATION PRIMARY KEY (notification_id),
        CONSTRAINT FK_JMN_JOB
            FOREIGN KEY (job_id)    REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT UQ_JMN_JOB       UNIQUE (job_id),
        CONSTRAINT CK_JMN_SEND_TYPE
            CHECK (result_send_type IN ('SEND_ATTACHMENT','SEND_INLINE_EMBED','DONT_SEND'))
    );
END
GO

-- ------------------------------------------------------------
--  JOB_RECIPIENT_INTERNAL : 내부 수신자
--  recipient_mode : BY_ROLE  → role 컬럼 기준으로 INTERNAL_USER 동적 조회
--                  BY_COMPANY → company 컬럼 기준으로 INTERNAL_USER 동적 조회
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.JOB_RECIPIENT_INTERNAL', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_RECIPIENT_INTERNAL (
        recipient_id        BIGINT          NOT NULL IDENTITY(1,1),
        notification_id     BIGINT          NOT NULL,
        recipient_mode      VARCHAR(12)     NOT NULL,           -- BY_ROLE / BY_COMPANY
        role                VARCHAR(50)         NULL,           -- BY_ROLE 전용
        company             VARCHAR(20)         NULL,           -- BY_COMPANY 전용
        recipient_type      CHAR(3)         NOT NULL,           -- TO /CC /BCC

        CONSTRAINT PK_JRI               PRIMARY KEY (recipient_id),
        CONSTRAINT FK_JRI_NOTIFICATION
            FOREIGN KEY (notification_id) REFERENCES dbo.JOB_MAIL_NOTIFICATION (notification_id),
        CONSTRAINT UQ_JRI               UNIQUE (notification_id, recipient_mode, role, company, recipient_type),
        CONSTRAINT CK_JRI_MODE
            CHECK (recipient_mode IN ('BY_ROLE','BY_COMPANY')),
        CONSTRAINT CK_JRI_TYPE
            CHECK (recipient_type IN ('TO ','CC ','BCC')),
        CONSTRAINT CK_JRI_BY_ROLE
            CHECK (recipient_mode <> 'BY_ROLE'    OR role    IS NOT NULL),
        CONSTRAINT CK_JRI_BY_COMPANY
            CHECK (recipient_mode <> 'BY_COMPANY' OR company IS NOT NULL)
    );
END
GO

-- ------------------------------------------------------------
--  JOB_RECIPIENT_EXTERNAL : 외부 수신자
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.JOB_RECIPIENT_EXTERNAL', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_RECIPIENT_EXTERNAL (
        recipient_id        BIGINT          NOT NULL IDENTITY(1,1),
        notification_id     BIGINT          NOT NULL,
        contact_email       VARCHAR(200)    NOT NULL,           -- → EXTERNAL_USER.contact_email
        recipient_type      CHAR(3)         NOT NULL,           -- TO /CC /BCC

        CONSTRAINT PK_JRE               PRIMARY KEY (recipient_id),
        CONSTRAINT FK_JRE_NOTIFICATION
            FOREIGN KEY (notification_id) REFERENCES dbo.JOB_MAIL_NOTIFICATION (notification_id),
        CONSTRAINT FK_JRE_USER
            FOREIGN KEY (contact_email)   REFERENCES dbo.EXTERNAL_USER (contact_email),
        CONSTRAINT UQ_JRE               UNIQUE (notification_id, contact_email, recipient_type),
        CONSTRAINT CK_JRE_TYPE
            CHECK (recipient_type IN ('TO ','CC ','BCC'))
    );
END
GO


-- ============================================================
--  ⑥ 실행 이력 & 발송 추적 테이블
-- ============================================================

-- ------------------------------------------------------------
--  JOB_EXECUTION_LOG : Job 실행 결과 이력
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.JOB_EXECUTION_LOG', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_EXECUTION_LOG (
        execution_id        BIGINT          NOT NULL IDENTITY(1,1),
        job_id              BIGINT          NOT NULL,
        jasper_fire_time    DATETIME2           NULL,
        execution_status    VARCHAR(20)     NOT NULL,           -- SUCCESS/FAILED/EMPTY_SKIP
        output_file_uri     VARCHAR(500)        NULL,
        error_message       NVARCHAR(MAX)       NULL,
        triggered_by        VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULE', -- SCHEDULE/MANUAL
        created_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_JEL   PRIMARY KEY (execution_id),
        CONSTRAINT FK_JEL_JOB
            FOREIGN KEY (job_id)    REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT CK_JEL_STATUS
            CHECK (execution_status IN ('SUCCESS','FAILED','EMPTY_SKIP')),
        CONSTRAINT CK_JEL_TRIGGERED_BY
            CHECK (triggered_by IN ('SCHEDULE','MANUAL'))
    );
END
GO

-- ------------------------------------------------------------
--  MAIL_SEND_LOG : 수신자별 메일 발송 이력
-- ------------------------------------------------------------
IF OBJECT_ID('dbo.MAIL_SEND_LOG', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.MAIL_SEND_LOG (
        send_log_id         BIGINT          NOT NULL IDENTITY(1,1),
        execution_id        BIGINT          NOT NULL,
        recipient_email     VARCHAR(200)    NOT NULL,           -- 발송 시점 이메일 스냅샷
        recipient_type      CHAR(3)         NOT NULL,           -- TO /CC /BCC
        user_type           VARCHAR(10)     NOT NULL,           -- INTERNAL/EXTERNAL
        send_status         VARCHAR(20)     NOT NULL,           -- SENT/FAILED/BOUNCED
        sent_at             DATETIME2           NULL,
        error_message       NVARCHAR(MAX)       NULL,

        CONSTRAINT PK_MSL   PRIMARY KEY (send_log_id),
        CONSTRAINT FK_MSL_EXECUTION
            FOREIGN KEY (execution_id)  REFERENCES dbo.JOB_EXECUTION_LOG (execution_id),
        CONSTRAINT CK_MSL_RECIPIENT_TYPE
            CHECK (recipient_type IN ('TO ','CC ','BCC')),
        CONSTRAINT CK_MSL_USER_TYPE
            CHECK (user_type IN ('INTERNAL','EXTERNAL')),
        CONSTRAINT CK_MSL_SEND_STATUS
            CHECK (send_status IN ('SENT','FAILED','BOUNCED'))
    );
END
GO


-- ============================================================
--  인덱스
-- ============================================================

CREATE INDEX IX_INTERNAL_USER_COMPANY      ON dbo.INTERNAL_USER        (company);
CREATE INDEX IX_INTERNAL_USER_ROLE         ON dbo.INTERNAL_USER        (role);
CREATE INDEX IX_EXTERNAL_USER_COMPANY      ON dbo.EXTERNAL_USER        (company);
CREATE INDEX IX_EXTERNAL_USER_PARTNER      ON dbo.EXTERNAL_USER        (partner_code);
CREATE INDEX IX_SJ_REPORT                  ON dbo.SCHEDULE_JOB         (report_id);
CREATE INDEX IX_SJ_STATUS                  ON dbo.SCHEDULE_JOB         (status);
CREATE INDEX IX_JEL_JOB_CREATED            ON dbo.JOB_EXECUTION_LOG    (job_id, created_at DESC);
CREATE INDEX IX_JEL_STATUS                 ON dbo.JOB_EXECUTION_LOG    (execution_status);
CREATE INDEX IX_JEL_CREATED_AT             ON dbo.JOB_EXECUTION_LOG    (created_at DESC);
CREATE INDEX IX_MSL_EXECUTION              ON dbo.MAIL_SEND_LOG        (execution_id);
CREATE INDEX IX_MSL_EMAIL                  ON dbo.MAIL_SEND_LOG        (recipient_email);
CREATE INDEX IX_MSL_SENT_AT                ON dbo.MAIL_SEND_LOG        (sent_at DESC);

GO


-- ============================================================
--  기초 데이터
-- ============================================================

-- 관리자 계정은 DDL에서 직접 만들지 않습니다.
-- 애플리케이션 최초 기동 시 DefaultAdminInitializer가
-- admin / admin1234 계정을 자동으로 생성합니다.
-- (DDL에 평문을 미리 BCrypt로 인코딩해서 넣으면 실수로 틀린 해시가
--  들어갈 위험이 있어, 앱이 PasswordEncoder로 직접 인코딩하도록 변경했습니다.)
GO


-- ============================================================
--  삭제 스크립트 (롤백용 참고)
-- ============================================================
/*
USE JasperReportDB;

DROP TABLE IF EXISTS dbo.MAIL_SEND_LOG;
DROP TABLE IF EXISTS dbo.JOB_EXECUTION_LOG;
DROP TABLE IF EXISTS dbo.JOB_RECIPIENT_EXTERNAL;
DROP TABLE IF EXISTS dbo.JOB_RECIPIENT_INTERNAL;
DROP TABLE IF EXISTS dbo.JOB_MAIL_NOTIFICATION;
DROP TABLE IF EXISTS dbo.JOB_REPORT_PARAM;
DROP TABLE IF EXISTS dbo.JOB_TRIGGER;
DROP TABLE IF EXISTS dbo.SCHEDULE_JOB;
DROP TABLE IF EXISTS dbo.REPORT_PARAM_DEF;
DROP TABLE IF EXISTS dbo.REPORT;
DROP TABLE IF EXISTS dbo.EXTERNAL_USER;
DROP TABLE IF EXISTS dbo.INTERNAL_USER;
DROP TABLE IF EXISTS dbo.ADMIN_USER;
*/
