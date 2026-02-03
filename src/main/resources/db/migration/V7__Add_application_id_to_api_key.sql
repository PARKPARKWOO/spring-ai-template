-- Application별 API 키 매핑: api_key에 application_id 추가
-- 기존 행은 application_id='default'로 설정 (호환용)
ALTER TABLE api_key
    ADD COLUMN application_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
    ADD UNIQUE KEY uk_application_vendor (application_id, vendor);

-- 인덱스로 조회 성능 확보
CREATE INDEX idx_api_key_application_vendor ON api_key (application_id, vendor);
