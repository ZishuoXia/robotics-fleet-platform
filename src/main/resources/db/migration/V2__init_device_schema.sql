-- =====================================================================
-- V2: Device registry. Each device belongs to exactly one tenant —
-- "tenant_id" is part of every domain table for data isolation.
-- =====================================================================

CREATE TABLE devices (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenants(id),
    serial_number   VARCHAR(128) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    model           VARCHAR(64),
    firmware_version VARCHAR(32),
    status          VARCHAR(32)  NOT NULL DEFAULT 'PROVISIONED',
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT uk_devices_serial UNIQUE (serial_number)
);

CREATE INDEX idx_devices_tenant_id ON devices(tenant_id);
CREATE INDEX idx_devices_status    ON devices(status);

COMMENT ON COLUMN devices.status IS
    'Lifecycle state: PROVISIONED (registered, never connected), ONLINE, OFFLINE, DECOMMISSIONED';
