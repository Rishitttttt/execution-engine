-- language: reference table seeded by V2
CREATE TABLE language (
    id                INT PRIMARY KEY,
    name              VARCHAR(64)  NOT NULL,
    version           VARCHAR(64),
    source_file       VARCHAR(255),
    compile_command   TEXT,
    run_command       TEXT NOT NULL,
    default_cpu_time  DOUBLE PRECISION NOT NULL DEFAULT 2.0,
    default_memory    INT          NOT NULL DEFAULT 128000,
    max_cpu_time      DOUBLE PRECISION NOT NULL DEFAULT 15.0,
    max_memory        INT          NOT NULL DEFAULT 512000,
    max_source_size   INT          NOT NULL DEFAULT 65536,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- submission: the source of truth for every job
CREATE TABLE submission (
    id                                 BIGSERIAL PRIMARY KEY,
    token                              UUID NOT NULL UNIQUE,
    language_id                        INT  NOT NULL REFERENCES language(id),
    source_code                        TEXT NOT NULL,
    std_in                             TEXT,
    expected_output                    TEXT,
    status                             INT NOT NULL DEFAULT 1,
    std_out                            TEXT,
    std_err                            TEXT,
    compile_output                     TEXT,
    message                            TEXT,
    exit_code                          INT,
    exit_signal                        INT,
    time                               DOUBLE PRECISION,
    wall_time                          DOUBLE PRECISION,
    memory                             INT,
    execution_host                     VARCHAR(128),
    cpu_time_limit                     DOUBLE PRECISION,
    cpu_extra_time_limit               DOUBLE PRECISION,
    wall_time_limit                    DOUBLE PRECISION,
    memory_limit                       INT,
    stack_limit                        INT,
    max_process_and_or_thread_limit    INT,
    max_file_size_limit                INT,
    idempotency_key                    UUID,
    created_at                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at                        TIMESTAMPTZ,
    CONSTRAINT submission_status_range CHECK (status BETWEEN 1 AND 14)
);

CREATE INDEX submission_status_created_idx ON submission (status, created_at);
CREATE INDEX submission_cursor_idx         ON submission (created_at DESC, id DESC);
CREATE INDEX submission_language_idx       ON submission (language_id);
CREATE UNIQUE INDEX submission_idempotency_uidx
    ON submission (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- outbox: transactional buffer for API → Redis durability (used in A2)
CREATE TABLE outbox (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL REFERENCES submission(id),
    payload       JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts      INT         NOT NULL DEFAULT 0,
    next_attempt  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX outbox_next_attempt_idx ON outbox (next_attempt);
