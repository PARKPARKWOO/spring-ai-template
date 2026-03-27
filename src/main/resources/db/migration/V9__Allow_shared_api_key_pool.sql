-- 공용 API Key 풀: application_id를 NULL 허용 (NULL = 모든 클라이언트가 공유하는 공용 키)
ALTER TABLE api_key MODIFY COLUMN application_id VARCHAR(64) NULL;

-- 기존 'default' application_id를 NULL로 변환 (공용 키로 전환)
UPDATE api_key SET application_id = NULL WHERE application_id = 'default';

-- 기존 인덱스 제거 후 NULL 포함 인덱스 재생성
DROP INDEX idx_api_key_tier ON api_key;
CREATE INDEX idx_api_key_app_vendor_tier ON api_key (application_id, vendor, tier);
CREATE INDEX idx_api_key_shared ON api_key (vendor, tier, deleted_at);
