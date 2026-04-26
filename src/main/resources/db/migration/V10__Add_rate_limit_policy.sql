CREATE TABLE rate_limit_policy (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor         VARCHAR(32) NOT NULL,
    tier           VARCHAR(16) NOT NULL,
    application_id VARCHAR(64) NULL,
    rpm            INT NOT NULL,
    rpd            INT NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    deleted_at     DATETIME(6) NULL,
    UNIQUE KEY uk_rate_limit_policy_vendor_tier_app (vendor, tier, application_id),
    KEY idx_rate_limit_policy_lookup (vendor, tier, deleted_at)
);

INSERT INTO rate_limit_policy (vendor, tier, application_id, rpm, rpd, created_at, updated_at)
VALUES
    ('GOOGLE',    'FREE', NULL, 10,  250,   NOW(6), NOW(6)),
    ('GOOGLE',    'PAID', NULL, 300, 10000, NOW(6), NOW(6)),
    ('OPENAI',    'FREE', NULL, 3,   200,   NOW(6), NOW(6)),
    ('OPENAI',    'PAID', NULL, 500, 50000, NOW(6), NOW(6)),
    ('ANTHROPIC', 'FREE', NULL, 5,   100,   NOW(6), NOW(6)),
    ('ANTHROPIC', 'PAID', NULL, 50,  10000, NOW(6), NOW(6)),
    ('X_AI',      'FREE', NULL, 5,   100,   NOW(6), NOW(6)),
    ('X_AI',      'PAID', NULL, 60,  10000, NOW(6), NOW(6));
