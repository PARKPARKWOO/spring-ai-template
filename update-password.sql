-- HectoFinancial 클라이언트의 비밀번호를 "password"로 업데이트하는 SQL
-- 주의: 이 쿼리는 BCrypt 해시값을 직접 업데이트하지만,
-- BCrypt는 매번 다른 해시를 생성하므로 애플리케이션에서 생성한 해시값을 사용해야 합니다.
--
-- 방법 1: 애플리케이션 실행 후 자동으로 업데이트됨 (ApiKeyInitializer에서 처리)
-- 방법 2: 아래 쿼리를 실행하기 전에 애플리케이션에서 PasswordEncoder.encode("password")로 생성한 해시값을 사용하세요

-- 임시 해시값 (실제로는 애플리케이션에서 생성한 값을 사용해야 함)
-- 이 값은 예시이며, 실제로는 애플리케이션에서 생성한 해시값으로 교체해야 합니다.
UPDATE client 
SET password = (
    -- 여기에 애플리케이션에서 생성한 BCrypt 해시값을 넣으세요
    -- 예: SELECT password FROM (SELECT '${BCrypt_Hash}' as password) AS tmp
    -- 또는 애플리케이션 시작 시 자동으로 업데이트되므로 이 쿼리는 필요 없을 수 있습니다.
    password
),
updated_at = NOW(6)
WHERE name = 'HectoFinancial' 
  AND deleted_at IS NULL;

-- 참고: 애플리케이션 시작 시 ApiKeyInitializer에서 자동으로 비밀번호를 업데이트하므로
-- 이 SQL 쿼리를 수동으로 실행할 필요는 없습니다.
-- 애플리케이션을 재시작하면 자동으로 올바른 해시값으로 업데이트됩니다.
