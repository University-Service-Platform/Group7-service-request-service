-- USM-G7 Sprint 1 - service_request_db schema
-- Owned entirely by service-request-service. No other service may read this
-- schema directly - work-order-service only ever reaches it over REST.

CREATE TABLE service_request (
    request_id              VARCHAR(20)   NOT NULL PRIMARY KEY,
    requester_id             VARCHAR(64)   NOT NULL,
    category                 VARCHAR(20)   NOT NULL,
    location                 VARCHAR(255)  NOT NULL,
    priority                 VARCHAR(20)   NOT NULL,
    description               TEXT          NOT NULL,
    attachment_reference     VARCHAR(500)  NULL,
    status                    VARCHAR(20)   NOT NULL,
    responsible_service_unit VARCHAR(100)  NULL,
    rejection_reason         VARCHAR(500)  NULL,
    confirmation_feedback    VARCHAR(500)  NULL,
    reported_time             DATETIME      NOT NULL,
    acknowledged_time        DATETIME      NULL,
    assigned_time             DATETIME      NULL,
    resolved_time             DATETIME      NULL,
    closed_time                DATETIME      NULL,
    version                    BIGINT        NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_service_request_requester ON service_request (requester_id);
CREATE INDEX idx_service_request_status ON service_request (status);
CREATE INDEX idx_service_request_category ON service_request (category);
