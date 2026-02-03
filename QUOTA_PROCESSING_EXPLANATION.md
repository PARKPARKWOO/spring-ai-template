# Quota 처리 방식 상세 설명

## 📋 개요

이 시스템은 **가격 기반(Price-based)**과 **토큰 기반(Token-based)** 두 가지 쿼터를 지원하며, 클라이언트별로 정책을 설정할 수 있습니다. 정책 기반으로 자동 분기 처리되며, 동시성 제어를 위해 Pessimistic Locking을 사용합니다.

---

## 🏗️ 아키텍처

### 1. 쿼터 타입

#### 1.1 가격 기반 쿼터 (ClientPricingQuota)
- **목적**: 클라이언트의 월간/주간/일간 예산 제한
- **예시**: "Client 1은 월 30,000원 제한"
- **단위**: 원화 (BigDecimal)
- **필드**:
  - `maxAmount`: 최대 금액
  - `currentAmount`: 현재 사용 금액
  - `cycleUnit`: 주기 단위 (DAYS, WEEKS, MONTHS)

#### 1.2 토큰 기반 쿼터 (ClientTokenQuota)
- **목적**: 클라이언트의 일간/주간/월간 토큰 사용량 제한
- **예시**: "Client 1은 1일 최대 1,000,000 토큰, 한달 최대 10,000,000 토큰"
- **단위**: 토큰 수 (Long)
- **필드**:
  - `maxTokens`: 최대 토큰 수
  - `currentTokens`: 현재 사용 토큰 수
  - `vendor`: 벤더 (NULL이면 모든 벤더)
  - `model`: 모델 (NULL이면 모든 모델)
  - `cycleUnit`: 주기 단위 (DAYS, WEEKS, MONTHS)

---

## 🎯 정책 기반 분기 처리

### QuotaPolicy Enum

```kotlin
enum class QuotaPolicy {
    PRICING_ONLY,  // 가격 기반만 적용
    TOKEN_ONLY,    // 토큰 기반만 적용
    BOTH,          // 둘 다 적용
    NONE           // 제한 없음
}
```

### 정책 결정 로직

```kotlin
fun getQuotaPolicy(clientId, vendor, model, contentType): QuotaPolicy {
    val hasPricingQuota = clientPricingQuotaRepository.existsByClientIdAndIsActiveTrue(clientId)
    val hasTokenQuota = clientTokenQuotaRepository.existsByClientIdAndVendorAndModelAndIsActiveTrue(
        clientId, vendor, model
    )
    
    return when {
        hasPricingQuota && hasTokenQuota -> BOTH
        hasPricingQuota -> PRICING_ONLY
        hasTokenQuota -> TOKEN_ONLY
        else -> NONE
    }
}
```

---

## 🔄 처리 프로세스

### 1. 할당(Allocation) 프로세스

#### 1.1 통합 할당 메서드 (`allocateQuota`)

```kotlin
@Transactional
fun allocateQuota(
    clientId: Long,
    vendor: Vendor,
    model: String,
    inputTokens: Int,
    outputTokens: Int,
    contentType: ContentType = ContentType.TEXT,
)
```

**처리 흐름:**

1. **정책 확인**: `getQuotaPolicy()`로 클라이언트의 쿼터 정책 확인
2. **정책별 분기 처리**:
   - `PRICING_ONLY`: 비용 계산 → 가격 기반 쿼터 할당
   - `TOKEN_ONLY`: 토큰 합계 계산 → 토큰 기반 쿼터 할당
   - `BOTH`: 비용 + 토큰 모두 계산 → 둘 다 할당
   - `NONE`: 아무것도 하지 않음

#### 1.2 가격 기반 할당 (`allocatePricingQuota`)

```kotlin
@Transactional
fun allocatePricingQuota(clientId: Long, amount: BigDecimal) {
    // 1. Pessimistic Lock으로 쿼터 조회
    val quotas = clientPricingQuotaRepository.findByClientIdWithLock(clientId)
    
    // 2. 모든 활성 쿼터에 대해 할당 가능 여부 확인
    val canAllocate = quotas.all { it.canAllocate(amount) }
    
    // 3. 할당 불가능하면 예외 발생
    if (!canAllocate) {
        throw AiServiceException(AI_QUOTA_EXCEEDED, "...")
    }
    
    // 4. 모든 쿼터에 할당
    quotas.forEach { it.allocate(amount) }
    clientPricingQuotaRepository.saveAll(quotas)
}
```

**특징:**
- 모든 활성 쿼터를 확인 (예: 일간 + 월간 쿼터가 모두 있으면 둘 다 확인)
- 하나라도 초과하면 전체 실패
- Pessimistic Lock으로 동시성 제어

#### 1.3 토큰 기반 할당 (`allocateTokenQuota`)

```kotlin
@Transactional
fun allocateTokenQuota(
    clientId: Long,
    vendor: Vendor,
    model: String,
    contentType: ContentType,
    tokens: Long
) {
    // 1. Pessimistic Lock으로 쿼터 조회 (Vendor/Model 매칭)
    val quotas = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(
        clientId, vendor, model
    )
    
    // 2. ContentType 필터링 (현재는 모든 쿼터에 적용)
    val applicableQuotas = quotas.filter { /* ContentType 로직 */ }
    
    // 3. 할당 가능 여부 확인 및 할당
    val canAllocate = applicableQuotas.all { it.canAllocate(tokens) }
    if (!canAllocate) {
        throw AiServiceException(AI_QUOTA_EXCEEDED, "...")
    }
    
    applicableQuotas.forEach { it.allocate(tokens) }
    clientTokenQuotaRepository.saveAll(applicableQuotas)
}
```

**특징:**
- Vendor/Model별로 매칭되는 쿼터만 조회
- NULL 값은 "모든 벤더/모델" 의미
- 가장 구체적인 쿼터부터 우선 적용 (Model > Vendor > 전체)

---

### 2. 조정(Adjustment) 프로세스

#### 2.1 통합 조정 메서드 (`adjustByActualUsage`)

```kotlin
@Transactional
fun adjustByActualUsage(
    clientId: Long,
    vendor: Vendor,
    model: String,
    contentType: ContentType,
    allocatedInputTokens: Int,
    allocatedOutputTokens: Int,
    actualInputTokens: Int,
    actualOutputTokens: Int,
)
```

**처리 흐름:**

1. **정책 확인**: `getQuotaPolicy()`로 정책 확인
2. **정책별 분기 처리**:
   - `PRICING_ONLY`: 할당 비용 vs 실제 비용 차이 계산 → 조정
   - `TOKEN_ONLY`: 할당 토큰 vs 실제 토큰 차이 계산 → 조정
   - `BOTH`: 비용 + 토큰 모두 조정
   - `NONE`: 아무것도 하지 않음

#### 2.2 조정 로직

**가격 기반 조정:**
```kotlin
fun adjustByActualUsage(allocatedAmount: BigDecimal, actualAmount: BigDecimal) {
    val difference = actualAmount.subtract(allocatedAmount)
    currentAmount = currentAmount.add(difference)
    // 예: 할당 100원, 실제 80원 → -20원 차이 → currentAmount 감소
    // 예: 할당 100원, 실제 120원 → +20원 차이 → currentAmount 증가
}
```

**토큰 기반 조정:**
```kotlin
fun adjustByActualUsage(allocatedTokens: Long, actualTokens: Long) {
    val difference = actualTokens - allocatedTokens
    currentTokens += difference
    // 예: 할당 1000 토큰, 실제 800 토큰 → -200 차이 → currentTokens 감소
    // 예: 할당 1000 토큰, 실제 1200 토큰 → +200 차이 → currentTokens 증가
}
```

**이유:**
- AI 모델 호출 전에는 예상 토큰 수로 할당
- 실제 응답 후 실제 사용량으로 조정
- 차이만큼 증감하여 정확한 사용량 추적

---

## 🔒 동시성 제어

### Pessimistic Locking

모든 쿼터 조회 시 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 사용:

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT q FROM ClientPricingQuota q WHERE ...")
fun findByClientIdWithLock(clientId: Long): List<ClientPricingQuota>
```

**동작 방식:**
- `SELECT ... FOR UPDATE` 쿼리 실행
- 트랜잭션 종료까지 해당 행에 배타적 락 유지
- 동시 요청 시 순차 처리 보장

**장점:**
- Race condition 방지
- 정확한 쿼터 검증 및 할당
- 데이터 일관성 보장

**단점:**
- 동시성 감소 (락 대기 시간 발생)
- 데드락 가능성 (주의 필요)

---

## ⏰ 주기별 리셋

### 스케줄러 (`QuotaResetScheduler`)

```kotlin
@Scheduled(cron = "0 0 * * * *") // 매 시간 정각
fun resetQuotas() {
    quotaManagementService.resetQuotas()
}
```

### 리셋 로직 (`resetQuotas`)

```kotlin
@Transactional
fun resetQuotas() {
    // 1. 리셋이 필요한 가격 기반 쿼터 조회
    val pricingQuotas = clientPricingQuotaRepository.findQuotasNeedingReset()
    pricingQuotas.forEach { it.reset() }
    clientPricingQuotaRepository.saveAll(pricingQuotas)
    
    // 2. 리셋이 필요한 토큰 기반 쿼터 조회
    val tokenQuotas = clientTokenQuotaRepository.findQuotasNeedingReset()
    tokenQuotas.forEach { it.reset() }
    clientTokenQuotaRepository.saveAll(tokenQuotas)
}
```

**리셋 조건:**
```kotlin
fun needsReset(): Boolean {
    return LocalDateTime.now().isAfter(nextResetAt) || 
           LocalDateTime.now().isEqual(nextResetAt)
}
```

**리셋 동작:**
```kotlin
fun reset() {
    currentAmount = BigDecimal.ZERO  // 또는 currentTokens = 0
    cycleStartedAt = LocalDateTime.now()
    nextResetAt = cycleStartedAt.plus(1, chronoUnit)
    updatedAt = LocalDateTime.now()
}
```

---

## 🔄 실제 사용 흐름

### AI 모델 호출 시

```kotlin
// 1. 요청 전: 예상 토큰 수로 할당
quotaManagementService.allocateQuota(
    clientId = clientId,
    vendor = vendor,
    model = modelName,
    inputTokens = tokenCount,
    outputTokens = 0, // 예상 output 토큰은 0
    contentType = ContentType.TEXT,
)

// 2. AI 모델 호출
val response = chatModel.call(prompt)

// 3. 응답 후: 실제 사용량으로 조정
quotaManagementService.adjustByActualUsage(
    clientId = clientId,
    vendor = vendor,
    model = modelName,
    contentType = ContentType.TEXT,
    allocatedInputTokens = tokenCount,
    allocatedOutputTokens = 0,
    actualInputTokens = response.metadata.usage.promptTokens,
    actualOutputTokens = response.metadata.usage.generationTokens,
)
```

### 실패 시 롤백

```kotlin
val response = runCatching {
    chatModel.call(prompt)
}.getOrElse {
    // 실패 시 할당된 쿼터 롤백 (0으로 조정)
    quotaManagementService.adjustByActualUsage(
        clientId = clientId,
        vendor = vendor,
        model = modelName,
        contentType = ContentType.TEXT,
        allocatedInputTokens = tokenCount,
        allocatedOutputTokens = 0,
        actualInputTokens = 0,  // 실제 사용 없음
        actualOutputTokens = 0,
    )
    return AiApiResponse(vendor, it.message)
}
```

---

## 📊 예시 시나리오

### 시나리오 1: 가격 기반만 적용

**설정:**
- Client 1: 월 30,000원 제한

**요청:**
- GPT-4o-mini 호출 (예상 비용: 500원)

**처리:**
1. `getQuotaPolicy()` → `PRICING_ONLY`
2. `TokenPricingService.calculateCost()` → 500원 계산
3. `allocatePricingQuota(500원)` → 현재 29,500원 남음 → 할당 성공
4. AI 모델 호출
5. 실제 비용 480원 → `adjustByActualUsage(500원, 480원)` → 20원 반환

### 시나리오 2: 토큰 기반만 적용

**설정:**
- Client 2: 1일 최대 1,000,000 토큰

**요청:**
- Claude 호출 (예상: input 1000, output 500)

**처리:**
1. `getQuotaPolicy()` → `TOKEN_ONLY`
2. `allocateTokenQuota(1500 토큰)` → 현재 998,500 토큰 남음 → 할당 성공
3. AI 모델 호출
4. 실제: input 950, output 520 → `adjustByActualUsage(1500, 1470)` → 30 토큰 반환

### 시나리오 3: 둘 다 적용

**설정:**
- Client 3: 월 50,000원 제한 + 1일 최대 2,000,000 토큰

**요청:**
- Gemini 호출 (예상: input 2000, output 1000, 비용 1000원)

**처리:**
1. `getQuotaPolicy()` → `BOTH`
2. `allocatePricingQuota(1000원)` + `allocateTokenQuota(3000 토큰)` → 둘 다 확인
3. AI 모델 호출
4. 실제: input 1950, output 1050, 비용 980원
5. `adjustPricingQuota(1000원, 980원)` + `adjustTokenQuota(3000, 3000)` → 조정

---

## 🎯 주요 특징

### 1. 정책 기반 자동 분기
- 클라이언트별로 다른 정책 자동 감지
- 코드 수정 없이 정책 변경 가능

### 2. 예약(Reservation) 방식
- 요청 전 예상 사용량으로 할당
- 실제 사용 후 차이만큼 조정
- 정확한 사용량 추적

### 3. 동시성 안전성
- Pessimistic Lock으로 Race Condition 방지
- 트랜잭션 내에서 원자적 처리

### 4. 유연한 쿼터 설정
- 가격 기반: 예산 제한
- 토큰 기반: 사용량 제한 (Vendor/Model별 세분화 가능)
- 주기별 자동 리셋

### 5. 실패 처리
- 할당 실패 시 예외 발생
- AI 호출 실패 시 자동 롤백

---

## ⚠️ 주의사항

1. **데드락 가능성**: 여러 쿼터를 동시에 락할 때 주의
2. **성능**: Pessimistic Lock은 동시성 감소 → 필요시 Optimistic Lock 고려
3. **정확성**: 예상 토큰 수와 실제 토큰 수 차이로 인한 오차 가능
4. **리셋 타이밍**: 스케줄러는 매 시간 실행 → 정확한 리셋 시간 보장 안 됨

---

## 🔧 개선 가능 사항

1. **ContentType 필터링**: 현재 TODO 상태 → 구현 필요
2. **Optimistic Locking**: 동시성 향상을 위한 옵션 추가
3. **쿼터 알림**: 임계값 도달 시 알림 기능
4. **통계/모니터링**: 쿼터 사용량 대시보드
