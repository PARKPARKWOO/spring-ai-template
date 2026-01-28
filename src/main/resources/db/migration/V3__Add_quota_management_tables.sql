-- Flyway 마이그레이션: Quota 관리 시스템 테이블 추가

-- 1. Token Pricing Policy 테이블
CREATE TABLE IF NOT EXISTS token_pricing_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor VARCHAR(50) NOT NULL,
    model VARCHAR(255) NOT NULL,
    input_token_price_per_million DECIMAL(20, 8) NOT NULL COMMENT 'Input Token 가격 (1M 토큰당 원화)',
    output_token_price_per_million DECIMAL(20, 8) NOT NULL COMMENT 'Output Token 가격 (1M 토큰당 원화)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    UNIQUE KEY uk_pricing_vendor_model (vendor, model, deleted_at),
    INDEX idx_pricing_vendor (vendor),
    INDEX idx_pricing_model (model),
    INDEX idx_pricing_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Client Pricing Quota 테이블 (가격 기반 제한)
CREATE TABLE IF NOT EXISTS client_pricing_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    max_amount DECIMAL(20, 2) NOT NULL COMMENT '최대 금액 (원화)',
    current_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '현재 사용 금액 (원화)',
    cycle_unit VARCHAR(50) NOT NULL COMMENT 'DAYS, WEEKS, MONTHS',
    cycle_started_at DATETIME(6) NOT NULL,
    next_reset_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    FOREIGN KEY (client_id) REFERENCES client(id),
    INDEX idx_pricing_quota_client_id (client_id),
    INDEX idx_pricing_quota_next_reset (next_reset_at),
    INDEX idx_pricing_quota_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Client Token Quota 테이블 (토큰 기반 제한)
CREATE TABLE IF NOT EXISTS client_token_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    vendor VARCHAR(50) COMMENT 'NULL이면 모든 벤더',
    model VARCHAR(255) COMMENT 'NULL이면 모든 모델',
    max_tokens BIGINT NOT NULL COMMENT '최대 토큰 수',
    current_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '현재 사용 토큰 수',
    cycle_unit VARCHAR(50) NOT NULL COMMENT 'DAYS, WEEKS, MONTHS',
    cycle_started_at DATETIME(6) NOT NULL,
    next_reset_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    FOREIGN KEY (client_id) REFERENCES client(id),
    INDEX idx_token_quota_client_id (client_id),
    INDEX idx_token_quota_vendor (vendor),
    INDEX idx_token_quota_model (model),
    INDEX idx_token_quota_next_reset (next_reset_at),
    INDEX idx_token_quota_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 예시 데이터: Token Pricing Policy
-- GPT-4o-mini: $0.15/1M input, $0.60/1M output (환율 1,333 기준)
INSERT INTO token_pricing_policy (vendor, model, input_token_price_per_million, output_token_price_per_million, is_active, created_at, updated_at, deleted_at)
VALUES 
    ('OPENAI', 'gpt-4o-mini', 200.00, 800.00, TRUE, NOW(6), NOW(6), NULL),
    ('OPENAI', 'gpt-4o', 500.00, 1500.00, TRUE, NOW(6), NOW(6), NULL),
    ('OPENAI', 'gpt-3.5-turbo', 15.00, 20.00, TRUE, NOW(6), NOW(6), NULL),
    ('ANTHROPIC', 'claude-3-5-sonnet-20241022', 300.00, 1500.00, TRUE, NOW(6), NOW(6), NULL),
    ('ANTHROPIC', 'claude-3-opus-20240229', 1500.00, 7500.00, TRUE, NOW(6), NOW(6), NULL),
    ('GOOGLE', 'gemini-2.0-flash-exp', 0.00, 0.00, TRUE, NOW(6), NOW(6), NULL),
    ('GOOGLE', 'gemini-1.5-pro', 125.00, 500.00, TRUE, NOW(6), NOW(6), NULL),
    ('X_AI', 'grok-beta', 100.00, 300.00, TRUE, NOW(6), NOW(6), NULL)
ON DUPLICATE KEY UPDATE updated_at = NOW(6);

-- 예시 데이터: Client Pricing Quota (HectoFinancial - 월 30,000원 제한)
SET @hecto_financial_client_id = (SELECT id FROM client WHERE name = 'HectoFinancial' AND deleted_at IS NULL LIMIT 1);

INSERT INTO client_pricing_quota (client_id, max_amount, current_amount, cycle_unit, cycle_started_at, next_reset_at, is_active, created_at, updated_at, deleted_at)
SELECT 
    @hecto_financial_client_id,
    30000.00, -- 월 30,000원 제한
    0.00,
    'MONTHS',
    NOW(6),
    DATE_ADD(NOW(6), INTERVAL 1 MONTH),
    TRUE,
    NOW(6),
    NOW(6),
    NULL
WHERE @hecto_financial_client_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM client_pricing_quota 
    WHERE client_id = @hecto_financial_client_id 
    AND deleted_at IS NULL
  );

-- 예시 데이터: Client Token Quota (HectoFinancial - 1일 최대 1,000,000 토큰, 한달 최대 10,000,000 토큰)
INSERT INTO client_token_quota (client_id, vendor, model, max_tokens, current_tokens, cycle_unit, cycle_started_at, next_reset_at, is_active, created_at, updated_at, deleted_at)
SELECT 
    @hecto_financial_client_id,
    NULL, -- 모든 벤더
    NULL, -- 모든 모델
    1000000, -- 1일 최대 1,000,000 토큰
    0,
    'DAYS',
    NOW(6),
    DATE_ADD(NOW(6), INTERVAL 1 DAY),
    TRUE,
    NOW(6),
    NOW(6),
    NULL
WHERE @hecto_financial_client_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM client_token_quota 
    WHERE client_id = @hecto_financial_client_id 
    AND vendor IS NULL 
    AND model IS NULL
    AND cycle_unit = 'DAYS'
    AND deleted_at IS NULL
  );

INSERT INTO client_token_quota (client_id, vendor, model, max_tokens, current_tokens, cycle_unit, cycle_started_at, next_reset_at, is_active, created_at, updated_at, deleted_at)
SELECT 
    @hecto_financial_client_id,
    NULL, -- 모든 벤더
    NULL, -- 모든 모델
    10000000, -- 한달 최대 10,000,000 토큰
    0,
    'MONTHS',
    NOW(6),
    DATE_ADD(NOW(6), INTERVAL 1 MONTH),
    TRUE,
    NOW(6),
    NOW(6),
    NULL
WHERE @hecto_financial_client_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM client_token_quota 
    WHERE client_id = @hecto_financial_client_id 
    AND vendor IS NULL 
    AND model IS NULL
    AND cycle_unit = 'MONTHS'
    AND deleted_at IS NULL
  );
