-- API 키 양방향 암호화 적용: 암호문 저장을 위해 api_key 컬럼 길이 확장
-- 암호화 형식: "enc:" + base64(iv || ciphertext)
ALTER TABLE api_key MODIFY COLUMN api_key VARCHAR(512) NOT NULL;
