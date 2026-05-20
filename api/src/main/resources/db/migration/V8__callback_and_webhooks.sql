ALTER TABLE submission ADD COLUMN callback_url VARCHAR(2048);

CREATE TABLE webhook_delivery (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submission(id),
    url           VARCHAR(2048) NOT NULL,
    payload       JSONB NOT NULL,
    attempts      INT NOT NULL DEFAULT 0,
    next_attempt  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error    TEXT,
    last_status   INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX webhook_delivery_next_idx ON webhook_delivery (next_attempt);

CREATE TABLE webhook_delivery_dead (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    url           VARCHAR(2048) NOT NULL,
    payload       JSONB NOT NULL,
    attempts      INT NOT NULL,
    last_error    TEXT,
    last_status   INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
