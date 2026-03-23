-- 동일 vendor에서 여러 API Key를 사용할 수 있도록 유니크 제약 제거
ALTER TABLE api_key DROP INDEX uk_application_vendor;

-- FREE/PAID 티어 구분 컬럼 추가 (기존 키는 FREE로 설정)
ALTER TABLE api_key
    ADD COLUMN tier VARCHAR(10) NOT NULL DEFAULT 'FREE' AFTER vendor;

CREATE INDEX idx_api_key_tier ON api_key (application_id, vendor, tier);
