# Quota 시스템 재설계 문서

## 요구사항

1. **Vendor + Model별 토큰당 가격 정책**
   - 각 Vendor와 Model 조합마다 Input Token과 Output Token의 가격이 다름
   - 예: GPT-4o-mini는 $0.15/1M input tokens, $0.60/1M output tokens

2. **Client별 가격 기반 제한**
   - 예: Client 1은 월 30,000원 제한
   - 주기별로 리셋 (일별, 주별, 월별 등)

3. **Client별 토큰 기반 제한**
   - 예: Client 1은 1일 최대 1,000,000 토큰, 한달 최대 10,000,000 토큰
   - Vendor별, Model별로 세분화 가능
   - 주기별로 리셋

## 데이터베이스 설계

### 1. Token Pricing Policy (토큰 가격 정책)

```sql
CREATE TABLE token_pricing_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor VARCHAR(50) NOT NULL,
    model VARCHAR(255) NOT NULL,
    input_token_price_per_million DECIMAL(20, 8) NOT NULL,  -- 1M 토큰당 가격 (원)
    output_token_price_per_million DECIMAL(20, 8) NOT NULL, -- 1M 토큰당 가격 (원)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    UNIQUE KEY uk_pricing_vendor_model (vendor, model, deleted_at),
    INDEX idx_pricing_vendor (vendor),
    INDEX idx_pricing_model (model),
    INDEX idx_pricing_is_active (is_active)
);
```

### 2. Client Pricing Quota (가격 기반 제한)

```sql
CREATE TABLE client_pricing_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    max_amount DECIMAL(20, 2) NOT NULL,  -- 최대 금액 (원)
    current_amount DECIMAL(20, 2) NOT NULL DEFAULT 0,  -- 현재 사용 금액
    cycle_unit VARCHAR(50) NOT NULL,  -- DAYS, WEEKS, MONTHS
    cycle_started_at DATETIME(6) NOT NULL,
    next_reset_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    FOREIGN KEY (client_id) REFERENCES client(id),
    INDEX idx_pricing_quota_client_id (client_id),
    INDEX idx_pricing_quota_next_reset (next_reset_at),
    INDEX idx_pricing_quota_is_active (is_active)
);
```

### 3. Client Token Quota (토큰 기반 제한)

```sql
CREATE TABLE client_token_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    vendor VARCHAR(50),  -- NULL이면 모든 벤더
    model VARCHAR(255),  -- NULL이면 모든 모델
    max_tokens BIGINT NOT NULL,  -- 최대 토큰 수
    current_tokens BIGINT NOT NULL DEFAULT 0,  -- 현재 사용 토큰 수
    cycle_unit VARCHAR(50) NOT NULL,  -- DAYS, WEEKS, MONTHS
    cycle_started_at DATETIME(6) NOT NULL,
    next_reset_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    FOREIGN KEY (client_id) REFERENCES client(id),
    INDEX idx_token_quota_client_id (client_id),
    INDEX idx_token_quota_vendor (vendor),
    INDEX idx_token_quota_model (model),
    INDEX idx_token_quota_next_reset (next_reset_at),
    INDEX idx_token_quota_is_active (is_active)
);
```

## 엔티티 설계

### 1. TokenPricingPolicy
- vendor, model 조합별 가격 정보
- input/output 토큰 가격 분리

### 2. ClientPricingQuota
- Client별 가격 기반 제한
- 주기별 리셋

### 3. ClientTokenQuota
- Client별 토큰 기반 제한
- Vendor/Model별 세분화 가능 (NULL이면 전체)

## 서비스 설계

### 1. TokenPricingService
- 토큰 가격 조회
- 토큰 사용량 → 가격 계산

### 2. UsageTrackingService
- AiUsageLogs 기반 실시간 사용량 계산
- 가격 기반 사용량 집계
- 토큰 기반 사용량 집계

### 3. QuotaService (리팩토링)
- 가격 기반 할당/검증
- 토큰 기반 할당/검증
- 통합 할당 로직

## 사용 흐름

1. **API 호출 전**
   - TokenPricingService로 토큰당 가격 조회
   - 예상 비용 계산 (input + output 토큰)
   - ClientPricingQuota 검증 (가격 기반)
   - ClientTokenQuota 검증 (토큰 기반)

2. **API 호출 후**
   - 실제 사용 토큰 수 확인
   - 실제 비용 계산
   - ClientPricingQuota 업데이트
   - ClientTokenQuota 업데이트
   - AiUsageLogs 저장

3. **주기별 리셋**
   - Scheduler로 주기별 리셋
   - current_amount, current_tokens 초기화
   - next_reset_at 업데이트
