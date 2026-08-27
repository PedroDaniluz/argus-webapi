CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    email          TEXT NOT NULL UNIQUE,
    password_hash  TEXT NOT NULL,
    name           TEXT NOT NULL,
    role           TEXT NOT NULL,             -- ADMIN | OPERATOR | VIEWER
    token_version  INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    disabled_at    TIMESTAMPTZ
);

CREATE TABLE device (
    id          UUID PRIMARY KEY,
    code        TEXT NOT NULL UNIQUE,         -- 'trator-01'
    label       TEXT NOT NULL,
    key_hash    TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ
);

CREATE TABLE location (
    id           BIGSERIAL PRIMARY KEY,
    device_id    UUID NOT NULL REFERENCES device(id),
    ts           TIMESTAMPTZ NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    lon          DOUBLE PRECISION NOT NULL,
    speed_mps    REAL,
    course_deg   REAL,
    sats         SMALLINT,
    hdop         REAL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_location UNIQUE (device_id, ts)
);

CREATE INDEX ix_location_ts      ON location USING BRIN (ts);
CREATE INDEX ix_location_dev_ts  ON location (device_id, ts DESC);
