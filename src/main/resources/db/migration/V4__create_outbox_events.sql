-- Shared transactional outbox (Form B) — one table for the whole project.
-- Personal data: payload may hold personal data in clear (UC-004: name, security number,
-- birth date for the KYC application). Retention: published rows pruned after
-- app.outbox.prune-after (P7D) by OutboxPruneJob; pending and dead-lettered rows are kept.
CREATE TABLE outbox_events (
    event_id        uuid          NOT NULL,
    aggregate_id    varchar(200)  NOT NULL,
    event_type      varchar(120)  NOT NULL,
    payload         jsonb         NOT NULL,
    occurred_at     timestamptz   NOT NULL,
    published_at    timestamptz   NULL,
    attempts        smallint      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz   NOT NULL,
    claimed_until   timestamptz   NULL,
    last_error      text          NULL,
    dead_lettered   boolean       NOT NULL DEFAULT false,
    CONSTRAINT pk_outbox_events PRIMARY KEY (event_id)
);

CREATE INDEX ix_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL AND NOT dead_lettered;

CREATE INDEX ix_outbox_events_published_at
    ON outbox_events (published_at)
    WHERE published_at IS NOT NULL;
