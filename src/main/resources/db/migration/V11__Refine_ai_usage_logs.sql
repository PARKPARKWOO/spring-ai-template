-- ai_usage_logs 정리:
--  · request_message / response_message TEXT 컬럼 제거 (개인정보 + 디스크 사용량 큼, 토큰 메타로 충분)
--  · api_key_id, application_id 추가 (키별 로드밸런싱 검증 + 소비 서비스 추적)
--  · client_id NULL 허용 (현 시점 application_id → client_id 매핑 코드 없음)
--  · 분석용 composite 인덱스 추가

ALTER TABLE ai_usage_logs DROP COLUMN request_message;
ALTER TABLE ai_usage_logs DROP COLUMN response_message;

ALTER TABLE ai_usage_logs MODIFY client_id BIGINT NULL;

ALTER TABLE ai_usage_logs ADD COLUMN api_key_id BIGINT NULL AFTER client_id;
ALTER TABLE ai_usage_logs ADD COLUMN application_id VARCHAR(64) NULL AFTER api_key_id;

CREATE INDEX idx_ai_usage_logs_api_key_created
    ON ai_usage_logs (api_key_id, created_at);
CREATE INDEX idx_ai_usage_logs_application_created
    ON ai_usage_logs (application_id, created_at);
CREATE INDEX idx_ai_usage_logs_vendor_created
    ON ai_usage_logs (vendor, created_at);
