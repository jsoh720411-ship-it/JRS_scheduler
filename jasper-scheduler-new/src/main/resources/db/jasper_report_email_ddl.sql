-- ============================================================
--  jasper-scheduler  전체 DDL
--  대상 DB : MS SQL Server (DATETIME2, IDENTITY, BIT)
--  실행 방법 : SQL Server Management Studio 또는 sqlcmd로 한 번만 실행
--  특징 : IF OBJECT_ID(...) IS NULL 으로 감싸져 있어 중복 실행 안전
-- ============================================================

-- ============================================================
--  ① 관리자 계정
-- ============================================================
IF OBJECT_ID('dbo.ADMIN_USER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.ADMIN_USER (
        admin_id        VARCHAR(50)     NOT NULL,
        password_hash   VARCHAR(200)    NOT NULL,
        admin_name      NVARCHAR(100)   NOT NULL,
        is_active       BIT             NOT NULL DEFAULT 1,
        last_login_at   DATETIME2           NULL,
        created_at      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_ADMIN_USER PRIMARY KEY (admin_id)
    );
END
GO

-- ============================================================
--  ② 사용자 테이블
-- ============================================================

-- 내부 사용자 (email + company + role 복합 PK — 한 사람이 여러 role 보유 가능)
IF OBJECT_ID('dbo.INTERNAL_USER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.INTERNAL_USER (
        email           VARCHAR(200)    NOT NULL,
        company         VARCHAR(20)     NOT NULL,           -- 법인 코드
        role            VARCHAR(50)     NOT NULL,
        is_active       BIT             NOT NULL DEFAULT 1,
        created_at      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_INTERNAL_USER PRIMARY KEY (email, company, role)
    );
END
GO

-- 외부 사용자 (파트너사 담당자)
-- partner_code: 같은 코드를 가진 사용자들을 그룹으로 묶어
--               Job 등록 시 "이 파트너 코드 전체에게 발송"이 가능하도록 함
IF OBJECT_ID('dbo.EXTERNAL_USER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.EXTERNAL_USER (
        contact_email   VARCHAR(200)    NOT NULL,
        company         VARCHAR(20)     NOT NULL,           -- 법인 코드
        partner_code    VARCHAR(20)     NOT NULL,           -- 파트너 코드 (그룹 단위 발송 기준)
        role            VARCHAR(50)     NOT NULL,
        is_active       BIT             NOT NULL DEFAULT 1,
        created_at      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_EXTERNAL_USER PRIMARY KEY (contact_email)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_EXTERNAL_USER_PARTNER_CODE'
                 AND object_id = OBJECT_ID('dbo.EXTERNAL_USER'))
    CREATE INDEX IX_EXTERNAL_USER_PARTNER_CODE ON dbo.EXTERNAL_USER (partner_code);
GO

-- ============================================================
--  ③ 레포트 테이블
-- ============================================================

IF OBJECT_ID('dbo.REPORT', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.REPORT (
        report_id           BIGINT          NOT NULL IDENTITY(1,1),
        report_name         NVARCHAR(200)   NOT NULL,
        jasper_report_uri   VARCHAR(500)    NOT NULL,           -- Jasper Server URI
        description         NVARCHAR(500)       NULL,
        is_active           BIT             NOT NULL DEFAULT 1,
        created_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_REPORT PRIMARY KEY (report_id)
    );
END
GO

-- 레포트 파라미터 정의 (메타 정보)
IF OBJECT_ID('dbo.REPORT_PARAM_DEF', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.REPORT_PARAM_DEF (
        param_def_id    BIGINT          NOT NULL IDENTITY(1,1),
        report_id       BIGINT          NOT NULL,
        param_name      VARCHAR(100)    NOT NULL,
        param_type      VARCHAR(20)     NOT NULL DEFAULT 'STRING',  -- STRING / DATE / NUMBER
        is_required     BIT             NOT NULL DEFAULT 0,
        default_value   VARCHAR(200)        NULL,
        display_order   INT             NOT NULL DEFAULT 0,

        CONSTRAINT PK_REPORT_PARAM_DEF PRIMARY KEY (param_def_id),
        CONSTRAINT FK_RPD_REPORT
            FOREIGN KEY (report_id)    REFERENCES dbo.REPORT (report_id)
    );
END
GO

-- ============================================================
--  ④ 스케줄 Job
-- ============================================================

IF OBJECT_ID('dbo.SCHEDULE_JOB', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.SCHEDULE_JOB (
        job_id                      BIGINT          NOT NULL IDENTITY(1,1),
        report_id                   BIGINT          NOT NULL,
        job_label                   NVARCHAR(200)   NOT NULL,
        job_description             NVARCHAR(500)       NULL,
        output_formats               VARCHAR(100)    NOT NULL DEFAULT 'PDF',  -- 예: PDF,XLS
        base_output_filename         VARCHAR(200)        NULL,
        output_timezone              VARCHAR(50)     NOT NULL DEFAULT 'Asia/Seoul',
        repo_folder_uri               VARCHAR(500)        NULL,
        repo_save_to_repository       BIT             NOT NULL DEFAULT 0,
        repo_overwrite_files          BIT             NOT NULL DEFAULT 0,
        repo_sequential_filenames     BIT             NOT NULL DEFAULT 0,
        status                        VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE/PAUSED/DISABLED
        jasper_job_id                 VARCHAR(50)         NULL,
        created_by                    VARCHAR(50)         NULL,
        created_at                    DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        updated_at                    DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_SCHEDULE_JOB PRIMARY KEY (job_id),
        CONSTRAINT FK_SJ_REPORT
            FOREIGN KEY (report_id)   REFERENCES dbo.REPORT (report_id),
        CONSTRAINT CK_SJ_STATUS
            CHECK (status IN ('ACTIVE','PAUSED','DISABLED'))
    );
END
GO

-- ============================================================
--  ⑤ 트리거 (실행 스케줄)
--     주의: Jasper Server 자체 스케줄러는 사용하지 않는다.
--     반복/주기 판단은 ScheduleRunner(자바)가 이 테이블을 기준으로 직접 수행하고,
--     Jasper에는 매번 "1회 즉시 실행" 트리거만 전송한다.
-- ============================================================

IF OBJECT_ID('dbo.JOB_TRIGGER', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_TRIGGER (
        trigger_id               BIGINT          NOT NULL IDENTITY(1,1),
        job_id                   BIGINT          NOT NULL,

        -- 트리거 유형: SIMPLE(반복 간격) / CALENDAR(Cron)
        trigger_type             VARCHAR(10)     NOT NULL DEFAULT 'CALENDAR',

        timezone                  VARCHAR(50)     NOT NULL DEFAULT 'Asia/Seoul',

        -- start_type: 1=즉시, 2=날짜 지정
        start_type                INT             NOT NULL DEFAULT 1,
        start_date                 DATETIME2           NULL,
        end_date                   DATETIME2           NULL,
        misfire_instruction        INT             NOT NULL DEFAULT 0,

        -- CALENDAR 전용
        cron_expression             VARCHAR(100)        NULL,   -- Spring 6필드 cron (초 분 시 일 월 요일)

        -- SIMPLE 전용
        occurrence_count             INT                 NULL,   -- -1=무제한
        recurrence_interval          INT                 NULL,
        recurrence_interval_unit     VARCHAR(10)         NULL,  -- MINUTE/HOUR/DAY/WEEK

        CONSTRAINT PK_JOB_TRIGGER PRIMARY KEY (trigger_id),
        CONSTRAINT FK_JT_JOB
            FOREIGN KEY (job_id)          REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT UQ_JT_JOB              UNIQUE (job_id),
        CONSTRAINT CK_JT_TYPE
            CHECK (trigger_type IN ('SIMPLE','CALENDAR')),
        CONSTRAINT CK_JT_INTERVAL_UNIT
            CHECK (recurrence_interval_unit IN ('MINUTE','HOUR','DAY','WEEK') OR recurrence_interval_unit IS NULL)
    );
END
GO

-- Job별 레포트 파라미터 값
IF OBJECT_ID('dbo.JOB_REPORT_PARAM', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_REPORT_PARAM (
        param_id        BIGINT          NOT NULL IDENTITY(1,1),
        job_id          BIGINT          NOT NULL,
        param_def_id    BIGINT          NOT NULL,
        param_value     VARCHAR(500)        NULL,
        is_dynamic      BIT             NOT NULL DEFAULT 0,
        dynamic_expr    VARCHAR(100)        NULL,   -- 예: TODAY-1D / MONTH_START / PREV_MONTH_END

        CONSTRAINT PK_JOB_REPORT_PARAM PRIMARY KEY (param_id),
        CONSTRAINT FK_JRP_JOB
            FOREIGN KEY (job_id)         REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT FK_JRP_PARAM_DEF
            FOREIGN KEY (param_def_id)   REFERENCES dbo.REPORT_PARAM_DEF (param_def_id)
    );
END
GO

-- ============================================================
--  ⑥ 메일 알림 설정
-- ============================================================

IF OBJECT_ID('dbo.JOB_MAIL_NOTIFICATION', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_MAIL_NOTIFICATION (
        notification_id                 BIGINT          NOT NULL IDENTITY(1,1),
        job_id                          BIGINT          NOT NULL,
        subject                         NVARCHAR(300)       NULL,
        message_text                    NVARCHAR(MAX)       NULL,
        result_send_type                VARCHAR(30)     NOT NULL DEFAULT 'SEND_ATTACHMENT',
            -- SEND_ATTACHMENT / SEND_INLINE_EMBED / DONT_SEND
        skip_empty_reports              BIT             NOT NULL DEFAULT 0,
        skip_notif_on_fail              BIT             NOT NULL DEFAULT 0,
        include_stacktrace_on_fail      BIT             NOT NULL DEFAULT 0,

        CONSTRAINT PK_JMN             PRIMARY KEY (notification_id),
        CONSTRAINT FK_JMN_JOB
            FOREIGN KEY (job_id)      REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT UQ_JMN_JOB         UNIQUE (job_id)
    );
END
GO

-- ============================================================
--  ⑦ 수신자
-- ============================================================

-- 내부 수신자 규칙 (BY_ROLE / BY_COMPANY → 실행 시 동적 조회)
IF OBJECT_ID('dbo.JOB_RECIPIENT_INTERNAL', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_RECIPIENT_INTERNAL (
        recipient_id        BIGINT          NOT NULL IDENTITY(1,1),
        notification_id     BIGINT          NOT NULL,
        recipient_mode      VARCHAR(12)     NOT NULL,           -- BY_ROLE / BY_COMPANY
        role                 VARCHAR(50)         NULL,           -- BY_ROLE 전용
        company               VARCHAR(20)         NULL,           -- BY_COMPANY 전용
        recipient_type        CHAR(3)         NOT NULL,           -- 'TO ' / 'CC ' / 'BCC'

        CONSTRAINT PK_JRI                PRIMARY KEY (recipient_id),
        CONSTRAINT FK_JRI_NOTIFICATION
            FOREIGN KEY (notification_id) REFERENCES dbo.JOB_MAIL_NOTIFICATION (notification_id),
        CONSTRAINT UQ_JRI                UNIQUE (notification_id, recipient_mode, role, company, recipient_type),
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

-- 외부 수신자 규칙
--   DIRECT          : contact_email 에 지정된 단일 주소
--   BY_PARTNER_CODE : partner_code 에 속한 EXTERNAL_USER(활성) 전체를 실행 시 동적 조회
IF OBJECT_ID('dbo.JOB_RECIPIENT_EXTERNAL', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_RECIPIENT_EXTERNAL (
        recipient_id        BIGINT          NOT NULL IDENTITY(1,1),
        notification_id     BIGINT          NOT NULL,
        recipient_mode       VARCHAR(16)     NOT NULL DEFAULT 'BY_PARTNER_CODE',
            -- DIRECT / BY_PARTNER_CODE
        contact_email         VARCHAR(200)        NULL,   -- DIRECT 전용
        partner_code          VARCHAR(20)         NULL,   -- BY_PARTNER_CODE 전용
        recipient_type        CHAR(3)         NOT NULL,   -- 'TO ' / 'CC ' / 'BCC'

        CONSTRAINT PK_JRE                PRIMARY KEY (recipient_id),
        CONSTRAINT FK_JRE_NOTIFICATION
            FOREIGN KEY (notification_id) REFERENCES dbo.JOB_MAIL_NOTIFICATION (notification_id),
        CONSTRAINT FK_JRE_USER
            FOREIGN KEY (contact_email)   REFERENCES dbo.EXTERNAL_USER (contact_email),
        CONSTRAINT UQ_JRE
            UNIQUE (notification_id, recipient_mode, contact_email, partner_code, recipient_type),
        CONSTRAINT CK_JRE_MODE
            CHECK (recipient_mode IN ('DIRECT','BY_PARTNER_CODE')),
        CONSTRAINT CK_JRE_TYPE
            CHECK (recipient_type IN ('TO ','CC ','BCC')),
        CONSTRAINT CK_JRE_DIRECT
            CHECK (recipient_mode <> 'DIRECT'           OR contact_email IS NOT NULL),
        CONSTRAINT CK_JRE_BY_PARTNER
            CHECK (recipient_mode <> 'BY_PARTNER_CODE'  OR partner_code  IS NOT NULL)
    );
END
GO

-- ============================================================
--  ⑧ 실행 이력
-- ============================================================

IF OBJECT_ID('dbo.JOB_EXECUTION_LOG', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.JOB_EXECUTION_LOG (
        execution_id        BIGINT          NOT NULL IDENTITY(1,1),
        job_id               BIGINT          NOT NULL,
        jasper_fire_time      DATETIME2       NOT NULL,
        execution_status      VARCHAR(10)     NOT NULL DEFAULT 'PENDING',  -- PENDING/SUCCESS/FAILED
        output_file_uri        VARCHAR(500)        NULL,
        error_message           NVARCHAR(MAX)       NULL,
        triggered_by             VARCHAR(20)         NULL,   -- SCHEDULE / MANUAL
        created_at                DATETIME2       NOT NULL DEFAULT SYSDATETIME(),

        CONSTRAINT PK_JEL             PRIMARY KEY (execution_id),
        CONSTRAINT FK_JEL_JOB
            FOREIGN KEY (job_id)      REFERENCES dbo.SCHEDULE_JOB (job_id),
        CONSTRAINT CK_JEL_STATUS
            CHECK (execution_status IN ('PENDING','SUCCESS','FAILED'))
    );
END
GO

IF OBJECT_ID('dbo.MAIL_SEND_LOG', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.MAIL_SEND_LOG (
        send_id             BIGINT          NOT NULL IDENTITY(1,1),
        execution_id         BIGINT          NOT NULL,
        recipient_email       VARCHAR(200)    NOT NULL,
        recipient_type         CHAR(3)         NOT NULL,   -- 'TO ' / 'CC ' / 'BCC'
        user_type                VARCHAR(10)         NULL,   -- INTERNAL / EXTERNAL
        send_status               VARCHAR(10)     NOT NULL DEFAULT 'SENT',  -- SENT / FAILED
        sent_at                    DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
        error_message               NVARCHAR(MAX)       NULL,

        CONSTRAINT PK_MSL             PRIMARY KEY (send_id),
        CONSTRAINT FK_MSL_EXECUTION
            FOREIGN KEY (execution_id) REFERENCES dbo.JOB_EXECUTION_LOG (execution_id)
    );
END
GO

-- ============================================================
--  ⑨ 초기 데이터 (선택) — 기본 admin 계정은 DefaultAdminInitializer가
--     앱 최초 기동 시 자동 생성하므로 (admin/admin1234), 여기서는 INSERT 생략.
-- ============================================================
