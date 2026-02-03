-- Flyway 마이그레이션: Client 테이블에 role 컬럼 추가

-- 1. role 컬럼 추가
-- MySQL에서는 IF NOT EXISTS를 지원하지 않으므로,
-- 컬럼 존재 여부를 확인한 후 추가하는 방식으로 처리
SET @dbname = DATABASE();
SET @tablename = 'client';
SET @columnname = 'role';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (COLUMN_NAME = @columnname)
    ) > 0,
    'SELECT 1', -- 컬럼이 이미 존재하면 아무것도 하지 않음
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(50) NOT NULL DEFAULT ''CLIENT'' AFTER description')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
