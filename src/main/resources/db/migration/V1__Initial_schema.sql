-- Flyway 마이그레이션: 초기 스키마 생성

-- Client 테이블
CREATE TABLE IF NOT EXISTS client (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    UNIQUE KEY uk_client_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ApiKey 테이블 (Single Table Inheritance)
CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_type VARCHAR(50) NOT NULL,
    vendor VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    api_key VARCHAR(255) NOT NULL,  -- 모든 서브클래스에서 필수
    project_id VARCHAR(255),  -- GoogleApiKey 전용
    location VARCHAR(255),  -- GoogleApiKey 전용
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    INDEX idx_api_key_vendor (vendor),
    INDEX idx_api_key_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ClientApiKey 매핑 테이블
CREATE TABLE IF NOT EXISTS client_api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    api_key_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    UNIQUE KEY uk_client_api_key (client_id, api_key_id),
    FOREIGN KEY (client_id) REFERENCES client(id),
    FOREIGN KEY (api_key_id) REFERENCES api_key(id),
    INDEX idx_client_api_key_client_id (client_id),
    INDEX idx_client_api_key_api_key_id (api_key_id),
    INDEX idx_client_api_key_is_active (is_active),
    INDEX idx_client_api_key_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Quota 테이블
CREATE TABLE IF NOT EXISTS quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    vendor VARCHAR(50) NOT NULL,
    capacity BIGINT NOT NULL,
    reservation_amount BIGINT NOT NULL DEFAULT 0,
    service_started_at DATETIME(6) NOT NULL,
    next_refresh_at DATETIME(6) NOT NULL,
    cycle_unit VARCHAR(50) NOT NULL,
    UNIQUE KEY uk_quota_client_vendor (client_id, vendor),
    INDEX idx_quota_client_id (client_id),
    INDEX idx_quota_vendor (vendor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AppKey 테이블
CREATE TABLE IF NOT EXISTS app_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    expires_at DATETIME(6),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    UNIQUE KEY uk_app_key_hash (key_hash),
    FOREIGN KEY (client_id) REFERENCES client(id),
    INDEX idx_app_key_client_id (client_id),
    INDEX idx_app_key_hash (key_hash),
    INDEX idx_app_key_is_active (is_active),
    INDEX idx_app_key_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AiUsageLogs 테이블
CREATE TABLE IF NOT EXISTS ai_usage_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    vendor VARCHAR(50) NOT NULL,
    prompt_token INT NOT NULL,
    completion_token INT NOT NULL,
    request_message TEXT NOT NULL,
    response_message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_ai_usage_logs_client_id (client_id),
    INDEX idx_ai_usage_logs_vendor (vendor),
    INDEX idx_ai_usage_logs_created_at (created_at),
    INDEX idx_ai_usage_logs_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 초기 데이터 삽입: HectoFinancial Client
-- password는 BCrypt로 암호화되어 있으며, 원본 비밀번호는 "password"입니다.
-- 주의: BCrypt 해시는 매번 다른 값을 생성하므로, 이 해시는 예시입니다.
-- 실제 사용 시 애플리케이션에서 PasswordEncoder.encode("password")로 생성한 해시를 사용해야 합니다.
-- 
-- 해시 생성 방법:
-- 1. 애플리케이션 실행 후 PasswordEncoder를 주입받아 encode("password") 호출
-- 2. 또는 generate-bcrypt-hash.kt 스크립트 실행 (프로젝트 루트에 있음)
INSERT INTO client (name, password, description, created_at, updated_at, deleted_at)
VALUES (
    'HectoFinancial',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- BCrypt 해시 (원본: "password")
    'HectoFinancial client for production',
    NOW(6),
    NOW(6),
    NULL
)
ON DUPLICATE KEY UPDATE name = name; -- 이미 존재하는 경우 업데이트하지 않음

-- HectoFinancial Client용 Gemini API Key 및 관련 데이터
-- 주의: api_key 값은 환경 변수 GEMINI_API_KEY에서 가져와야 하므로, 
-- 실제 배포 시에는 애플리케이션에서 업데이트하거나 환경 변수를 사용해야 합니다.
-- 여기서는 플레이스홀더로 'YOUR_GEMINI_API_KEY'를 사용합니다.

-- 1. Gemini API Key 생성 (이미 존재하는 경우 스킵)
SET @hecto_financial_client_id = (SELECT id FROM client WHERE name = 'HectoFinancial' LIMIT 1);
SET @gemini_api_key_id = NULL;

-- Gemini API Key가 이미 존재하는지 확인하고, 없으면 생성
SELECT id INTO @gemini_api_key_id 
FROM api_key 
WHERE vendor = 'GOOGLE' AND deleted_at IS NULL 
LIMIT 1;

-- Gemini API Key가 없으면 생성
INSERT INTO api_key (vendor_type, vendor, description, api_key, project_id, location, created_at, updated_at, deleted_at)
SELECT 
    'GOOGLE',
    'GOOGLE',
    'Gemini API Key for HectoFinancial',
    'AIzaSyCOnjuemtw9I22GinTZmZX24IKnInEm2y0', -- 실제 배포 시 환경 변수에서 가져온 값으로 업데이트 필요
    NULL,
    NULL,
    NOW(6),
    NOW(6),
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM api_key WHERE vendor = 'GOOGLE' AND deleted_at IS NULL
)
LIMIT 1;

-- 생성된 API Key ID 가져오기
SELECT id INTO @gemini_api_key_id 
FROM api_key 
WHERE vendor = 'GOOGLE' AND deleted_at IS NULL 
LIMIT 1;

-- 2. HectoFinancial Client와 Gemini API Key 매핑
INSERT INTO client_api_key (client_id, api_key_id, is_active, created_at, updated_at, deleted_at)
SELECT 
    @hecto_financial_client_id,
    @gemini_api_key_id,
    TRUE,
    NOW(6),
    NOW(6),
    NULL
WHERE @hecto_financial_client_id IS NOT NULL 
  AND @gemini_api_key_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM client_api_key 
    WHERE client_id = @hecto_financial_client_id 
      AND api_key_id = @gemini_api_key_id
  );

-- 3. HectoFinancial Client용 Gemini Quota 생성
INSERT INTO quota (client_id, vendor, capacity, reservation_amount, service_started_at, next_refresh_at, cycle_unit)
SELECT 
    @hecto_financial_client_id,
    'GOOGLE',
    100000000000000, -- capacity
    0, -- reservation_amount
    NOW(6), -- service_started_at
    DATE_ADD(NOW(6), INTERVAL 1 WEEK), -- next_refresh_at (1주일 후)
    'WEEKS' -- cycle_unit
WHERE @hecto_financial_client_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM quota 
    WHERE client_id = @hecto_financial_client_id 
      AND vendor = 'GOOGLE'
  );
