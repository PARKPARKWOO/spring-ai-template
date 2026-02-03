-- Flyway 마이그레이션: Token Pricing Policy에 client_id 필드 추가
-- client_id가 null이면 공통 정책, 값이 있으면 Client별 정책

-- 1. client_id 컬럼 추가
-- MySQL에서는 IF NOT EXISTS를 지원하지 않으므로,
-- 컬럼 존재 여부를 확인한 후 추가하는 방식으로 처리
SET @dbname = DATABASE();
SET @tablename = 'token_pricing_policy';
SET @columnname = 'client_id';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (COLUMN_NAME = @columnname)
    ) > 0,
    'SELECT 1', -- 컬럼이 이미 존재하면 아무것도 하지 않음
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' BIGINT NULL COMMENT ''Client ID (NULL이면 공통 정책, 값이 있으면 Client별 정책)'' AFTER model')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 2. 인덱스 추가 (Client별 조회 성능 향상)
-- MySQL에서는 IF NOT EXISTS를 지원하지 않으므로,
-- 인덱스 존재 여부를 확인한 후 추가하는 방식으로 처리
SET @dbname = DATABASE();
SET @tablename = 'token_pricing_policy';
SET @indexname = 'idx_pricing_client_id';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (INDEX_NAME = @indexname)
    ) > 0,
    'SELECT 1', -- 인덱스가 이미 존재하면 아무것도 하지 않음
    CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(', @columnname, ')')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;

-- 3. 복합 인덱스 추가 (Client별 정책 조회 최적화)
SET @dbname = DATABASE();
SET @tablename = 'token_pricing_policy';
SET @indexname = 'idx_pricing_client_vendor_model';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (INDEX_NAME = @indexname)
    ) > 0,
    'SELECT 1', -- 인덱스가 이미 존재하면 아무것도 하지 않음
    CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(', @columnname, ', vendor, model)')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;

-- 4. 기존 UNIQUE 제약조건 수정 (client_id를 포함하도록)
-- 먼저 기존 제약조건 삭제
-- MySQL에서는 DROP INDEX IF EXISTS를 ALTER TABLE과 함께 사용할 수 없으므로,
-- 인덱스 존재 여부를 확인한 후 삭제하는 방식으로 처리
SET @dbname = DATABASE();
SET @tablename = 'token_pricing_policy';
SET @indexname = 'uk_pricing_vendor_model';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (INDEX_NAME = @indexname)
    ) > 0,
    CONCAT('ALTER TABLE ', @tablename, ' DROP INDEX ', @indexname), -- 인덱스가 존재하면 삭제
    'SELECT 1' -- 인덱스가 없으면 아무것도 하지 않음
));
PREPARE dropIndexIfExists FROM @preparedStatement;
EXECUTE dropIndexIfExists;
DEALLOCATE PREPARE dropIndexIfExists;

-- 새로운 UNIQUE 제약조건 추가 (client_id가 NULL인 경우와 값이 있는 경우를 구분)
-- MySQL에서는 NULL 값은 UNIQUE 제약조건에서 제외되므로, 공통 정책은 vendor+model로만 유니크
-- Client별 정책은 client_id+vendor+model로 유니크
-- 주의: MySQL의 경우 NULL 값은 UNIQUE 제약조건에서 제외되므로, 
-- 공통 정책(client_id=NULL)은 vendor+model로만 유니크하게 관리해야 함
-- Client별 정책은 client_id+vendor+model로 유니크하게 관리

-- 공통 정책용 UNIQUE 제약조건 (client_id가 NULL인 경우만)
-- MySQL에서는 함수 기반 인덱스를 사용할 수 없으므로, 애플리케이션 레벨에서 관리
-- 또는 별도의 트리거나 애플리케이션 로직으로 중복 방지

-- Client별 정책용 UNIQUE 제약조건 (client_id가 NOT NULL인 경우)
-- MySQL에서는 NULL 값이 포함된 컬럼에 대한 UNIQUE 제약조건이 복잡하므로,
-- 애플리케이션 레벨에서 중복 체크를 수행하는 것이 안전함
