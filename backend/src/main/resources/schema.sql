-- AI 面试模拟系统 - SQLite 建表脚本（幂等：CREATE TABLE IF NOT EXISTS）
-- 启动时由 spring.sql.init 自动执行；已存在的库不会重复建表。

CREATE TABLE IF NOT EXISTS ai_model_config (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    provider   VARCHAR(100) NOT NULL,
    api_url    VARCHAR(500),
    api_key    VARCHAR(500),
    model_name VARCHAR(200),
    is_active  BOOLEAN,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS voice_config (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    provider   VARCHAR(50)  NOT NULL,
    secret_id  VARCHAR(300),
    secret_key VARCHAR(500),
    app_id     VARCHAR(100),
    region     VARCHAR(50),
    asr_url    VARCHAR(300),
    tts_url    VARCHAR(300),
    asr_prompt VARCHAR(2000),
    is_active  BOOLEAN,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS historical_analysis (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id    BIGINT NOT NULL,
    analysis_data VARCHAR(20000),
    generated_at  TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS interview_project (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           VARCHAR(200) NOT NULL,
    target_position VARCHAR(200),
    description    VARCHAR(2000),
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS interview_session (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id             BIGINT NOT NULL,
    position_requirement_id BIGINT,
    type                   VARCHAR(20),
    status                 VARCHAR(20),
    time_limit             INTEGER,
    started_at             TIMESTAMP,
    completed_at           TIMESTAMP,
    outline                TEXT,
    reference_written_test BOOLEAN,
    created_at             TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS interviewer_persona (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         VARCHAR(200) NOT NULL,
    description  VARCHAR(2000),
    style_config VARCHAR(5000),
    is_preset    BOOLEAN,
    created_at   TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS mock_interview_message (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id BIGINT NOT NULL,
    role       VARCHAR(20),
    content    VARCHAR(10000),
    audio_path VARCHAR(500),
    persona_id BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS position_requirement (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id      BIGINT NOT NULL,
    job_title       VARCHAR(200),
    experience      VARCHAR(500),
    seniority       VARCHAR(500),
    company_type    VARCHAR(500),
    tech_stack      VARCHAR(5000),
    job_description VARCHAR(10000),
    source          VARCHAR(20),
    created_at      TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS question_analysis (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id             BIGINT NOT NULL,
    question_ref_id        BIGINT,
    question_content       VARCHAR(5000),
    answer_content         VARCHAR(10000),
    accuracy               FLOAT,
    accuracy_reason        VARCHAR(1000),
    depth                  FLOAT,
    depth_reason           VARCHAR(1000),
    clarity                FLOAT,
    clarity_reason         VARCHAR(1000),
    fluency                FLOAT,
    fluency_reason         VARCHAR(1000),
    tone_evaluation        VARCHAR(255),
    category               VARCHAR(200),
    difficulty             VARCHAR(100),
    focus_point            VARCHAR(1000),
    answer_approach        VARCHAR(3000),
    example                VARCHAR(3000),
    improvement_suggestion VARCHAR(5000)
);

CREATE TABLE IF NOT EXISTS resume (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id        BIGINT NOT NULL,
    file_path         VARCHAR(500),
    original_filename VARCHAR(255),
    parsed_text       VARCHAR(50000),
    analysis_result   VARCHAR(50000),
    created_at        TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS session_analysis (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id              BIGINT NOT NULL,
    overall_score           FLOAT,
    strengths               VARCHAR(5000),
    weaknesses              VARCHAR(5000),
    knowledge_gaps          VARCHAR(5000),
    overall_level           VARCHAR(200),
    expected_salary_range   VARCHAR(100),
    personality_summary     VARCHAR(5000),
    character_traits        VARCHAR(3000),
    character_defects       VARCHAR(5000),
    communication_evaluation VARCHAR(5000),
    humor_summary           VARCHAR(500),
    improvement_plan        VARCHAR(10000),
    created_at              TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS weakness_tag (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id       BIGINT NOT NULL,
    knowledge_point  VARCHAR(200),
    mastery_level    VARCHAR(20),
    occurrence_count INTEGER,
    last_session_id  BIGINT,
    updated_at       TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS written_test_answer (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id BIGINT NOT NULL,
    session_id  BIGINT NOT NULL,
    user_answer VARCHAR(5000),
    is_correct  BOOLEAN,
    score       FLOAT,
    -- 错题本：订正成功的时间（NULL = 待订正）与重练次数
    corrected_at VARCHAR(40),
    retry_count  INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS written_test_question (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id       BIGINT NOT NULL,
    type             VARCHAR(20),
    content          VARCHAR(5000),
    options          VARCHAR(10000),
    answer           VARCHAR(5000),
    explanation      VARCHAR(5000),
    knowledge_points VARCHAR(2000),
    order_num        INTEGER
);

CREATE TABLE IF NOT EXISTS prompt_template (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    occupation VARCHAR(100) NOT NULL,
    scene      VARCHAR(50)  NOT NULL,
    content    TEXT NOT NULL,
    is_active  BOOLEAN,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(occupation, scene)
);

-- ============ 索引 ============
-- 所有查询都是按 session_id / project_id 过滤 + 按时间排序；原先这些表全部没有索引，
-- 每个请求都是全表扫。CREATE INDEX IF NOT EXISTS 幂等，旧库下次启动自动补齐
-- （spring.sql.init.mode=always，schema.sql 每次启动都会执行）。
CREATE INDEX IF NOT EXISTS idx_session_project       ON interview_session(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_session_project_type  ON interview_session(project_id, type, status, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_session       ON mock_interview_message(session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_question_session      ON written_test_question(session_id, order_num);
CREATE INDEX IF NOT EXISTS idx_answer_session        ON written_test_answer(session_id);
CREATE INDEX IF NOT EXISTS idx_answer_question       ON written_test_answer(question_id);
CREATE INDEX IF NOT EXISTS idx_session_analysis      ON session_analysis(session_id);
CREATE INDEX IF NOT EXISTS idx_question_analysis     ON question_analysis(session_id);
CREATE INDEX IF NOT EXISTS idx_weakness_project      ON weakness_tag(project_id);
CREATE INDEX IF NOT EXISTS idx_resume_project        ON resume(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_position_project      ON position_requirement(project_id);
CREATE INDEX IF NOT EXISTS idx_history_project       ON historical_analysis(project_id, generated_at DESC);
