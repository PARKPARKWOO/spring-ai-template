-- Flyway 마이그레이션: ai_usage_logs 테이블에 content_type 컬럼 추가

-- content_type 컬럼 추가 (기본값: TEXT)
-- MySQL 8.0.18 이하에서는 IF NOT EXISTS를 지원하지 않으므로,
-- 컬럼 존재 여부를 확인한 후 추가하는 방식으로 처리
SET @dbname = DATABASE();
SET @tablename = 'ai_usage_logs';
SET @columnname = 'content_type';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (COLUMN_NAME = @columnname)
    ) > 0,
    'SELECT 1', -- 컬럼이 이미 존재하면 아무것도 하지 않음
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(50) NOT NULL DEFAULT ''TEXT'' AFTER vendor')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;


-- 복합 인덱스 추가 (성능 최적화용)
-- MySQL 5.7 이하에서는 IF NOT EXISTS를 지원하지 않으므로,
-- 인덱스 존재 여부를 확인한 후 추가하는 방식으로 처리
SET @dbname = DATABASE();
SET @tablename = 'ai_usage_logs';
SET @indexname = 'idx_ai_usage_logs_client_id_session_id';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (INDEX_NAME = @indexname)
    ) > 0,
    'SELECT 1', -- 인덱스가 이미 존재하면 아무것도 하지 않음
    CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(client_id, session_id)')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;
