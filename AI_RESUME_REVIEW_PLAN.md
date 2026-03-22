# Spring-AI 모듈 - AI 이력서 리뷰 & 직무별 예상 질문 기능 계획

## 역할
LLM 호출의 **중앙 게이트웨이**로서, mirror-view-backend로부터 gRPC 요청을 받아 AI 벤더(OpenAI, Anthropic, Google Gemini, xAI)에게 전달하고 결과를 반환.

---

## 1. 현재 구현 상태

### ✅ 완료된 기능
| 기능 | 설명 |
|------|------|
| 멀티벤더 AI 호출 | OpenAI, Anthropic, Google Gemini, xAI 지원 |
| gRPC 서버 | `AiApiGrpcService` - Chat / Embedding 엔드포인트 |
| REST API | `POST /api/ai`, `POST /api/ai/stream`, `POST /api/ai/embedding` |
| Quota 관리 | 가격/토큰 기반 이중 할당 시스템 |
| Structured Output | OpenAI, Gemini, xAI JSON Schema 지원 |
| 스트리밍 | SSE 기반 스트리밍 응답 |
| 사용량 로깅 | 벤더별 토큰 사용량 추적 |

### 현재 gRPC 인터페이스
```protobuf
service AiApiService {
  rpc Chat(AiApiRequest) returns (AiApiResponse);
  rpc Embedding(EmbeddingApiRequest) returns (EmbeddingApiResponse);
}
```

### 현재 AI 호출 흐름 (퀴즈 생성 시)
```
Backend(AiGrpcClient)
  → gRPC Chat(AiApiRequest)
    → AiApiGrpcService.chat()
      → AiService.call()
        → AiApiFactory.getClient(vendor)
        → client.call(messages, options)  // ex: GeminiClient
        → Quota 할당/조정
      → AiApiResponse 반환
```

---

## 2. 추가/수정이 필요한 사항

### 2-1. 프롬프트 정의 (핵심)

Spring-AI 모듈에서 직접 프롬프트를 관리하지 않고, **Backend에서 시스템 프롬프트를 구성하여 전달**하는 현재 구조를 유지합니다. 단, 아래 프롬프트는 Backend의 AI 클라이언트에서 사용됩니다.

#### 이력서 리뷰용 프롬프트 (Backend → Spring-AI로 전달)

```
당신은 10년 경력의 시니어 채용 담당자이자 이력서 컨설턴트입니다.
제출된 이력서를 분석하여 상세한 피드백을 제공합니다.

{JD가 있는 경우}
채용공고(JD)와의 적합도도 함께 평가합니다.

반드시 다음 JSON 형식으로만 응답하세요:
{
  "overallFeedback": "종합 피드백 (2-3문장)",
  "score": 0~100,
  "strengths": ["강점1", "강점2", ...],
  "weaknesses": ["약점1", "약점2", ...],
  "suggestions": ["개선제안1", "개선제안2", ...],
  "sections": [
    {
      "sectionName": "항목명 (경력사항/기술스택/프로젝트/자기소개/포맷팅 등)",
      "score": 0~100,
      "feedback": "해당 항목에 대한 상세 피드백",
      "improvement": "구체적인 개선 방안",
      "sortOrder": 0
    }
  ]
}

평가 기준:
1. 명확성: 경험과 성과가 구체적 수치로 표현되었는가
2. 관련성: 지원 직무와 관련된 경험이 잘 드러나는가
3. 구조: 논리적 흐름과 가독성이 좋은가
4. 기술스택: 요구 기술과의 매칭도
5. 차별성: 다른 지원자 대비 어필 포인트가 있는가
```

#### 직무별 예상 질문용 프롬프트 (Backend → Spring-AI로 전달)

```
당신은 IT 기업의 시니어 면접관입니다.
지원자의 이력서와 채용공고(JD)를 바탕으로 실제 면접에서 물을 수 있는 예상 질문을 생성합니다.

반드시 다음 JSON 형식으로만 응답하세요:
{
  "title": "면접 예상 질문 제목 (ex: 카카오 백엔드 개발자 면접 예상 질문)",
  "questions": [
    {
      "category": "TECHNICAL|BEHAVIORAL|SITUATIONAL|PROJECT|MOTIVATION|CULTURE_FIT",
      "question": "면접 질문",
      "intent": "면접관이 이 질문을 통해 확인하고자 하는 것",
      "sampleAnswer": "지원자의 이력서를 기반으로 한 모범 답변 예시",
      "tip": "답변 시 주의사항 또는 팁",
      "difficulty": "EASY|MEDIUM|HARD",
      "sortOrder": 0
    }
  ]
}

질문 유형별 가이드:
- TECHNICAL: 이력서에 기재된 기술스택 기반 심층 질문
- BEHAVIORAL: 과거 경험/행동 기반 질문 (STAR 기법)
- SITUATIONAL: 가상 상황 대처 능력 평가
- PROJECT: 이력서에 기재된 프로젝트 심층 질문
- MOTIVATION: 지원 동기, 회사/직무 이해도
- CULTURE_FIT: 팀워크, 가치관, 조직 적합성

유형별 최소 1개 이상의 질문을 포함하세요.
난이도를 고르게 분배하세요.
```

### 2-2. Spring-AI 모듈 자체 수정사항

현재 Spring-AI 모듈은 **범용 AI 게이트웨이**로 설계되어 있어, 이력서 리뷰/예상 질문 기능을 위해 **모듈 자체의 수정은 최소화**됩니다.

#### 수정 불필요 (기존 그대로 사용)
| 항목 | 이유 |
|------|------|
| gRPC Chat 인터페이스 | 현재 `Chat(AiApiRequest) → AiApiResponse` 그대로 사용 가능 |
| 멀티벤더 호출 | 벤더 선택은 Backend에서 `ModelSpec`으로 지정 |
| Quota 관리 | 기존 정책 그대로 적용 |
| Structured Output | JSON Schema를 `responseSchema` 필드로 전달하면 됨 |
| 사용량 로깅 | applicationId로 구분 가능 |

#### 검토/수정 가능한 항목

| 항목 | 내용 | 우선순위 |
|------|------|----------|
| **maxTokens 조정** | 현재 기본 2000 토큰 → 이력서 리뷰 응답은 더 길 수 있음. Backend에서 요청 시 옵션으로 조정하거나, gRPC proto에 `maxTokens` 필드 추가 검토 | 중 |
| **타임아웃** | 이력서 리뷰는 응답이 길어 120s 이상 소요 가능. Backend에서 `timeoutSeconds` 조정으로 대응 가능 | 낮음 |
| **캐싱 전략** | 동일 이력서+JD 조합의 반복 리뷰 요청 시 캐싱 검토 (Caffeine) | 낮음 |

### 2-3. gRPC Proto 확장 검토

현재 proto로 충분히 동작하지만, 향후 확장을 위해 검토할 수 있는 항목:

```protobuf
// 현재 (변경 불필요)
message AiApiRequest {
  string application_id = 1;
  repeated ModelSpec models = 2;
  repeated ChatMessage messages = 3;
  optional int32 timeout_seconds = 4;
  optional string session_id = 5;
}

// 향후 확장 가능 (선택)
message AiApiRequest {
  // ... 기존 필드 ...
  optional int32 max_tokens = 6;         // 응답 최대 토큰 수 제어
  optional string request_type = 7;      // "quiz", "review", "interview" (로깅/모니터링용)
}
```

---

## 3. 벤더별 모델 추천

| 기능 | 추천 모델 | 이유 |
|------|-----------|------|
| 이력서 리뷰 | `gemini-2.5-flash` 또는 `gpt-4o` | 긴 문서 분석 능력, 구조화된 출력, 비용 효율 |
| 직무별 예상 질문 | `gemini-2.5-flash` (현재 퀴즈와 동일) | 이미 검증된 모델, JSON 구조화 출력 우수 |
| 고품질 리뷰 (프리미엄) | `claude-sonnet-4-6` 또는 `gpt-4o` | 더 정교한 분석이 필요한 경우 |

> **현재 퀴즈 생성은 `gemini-3-flash-preview` 사용 중**

---

## 4. 요약

| 구분 | Spring-AI 작업 | 상태 |
|------|----------------|------|
| gRPC 인터페이스 | 변경 불필요 (기존 Chat RPC 재사용) | ✅ 기존 유지 |
| AI 벤더 클라이언트 | 변경 불필요 (범용 게이트웨이) | ✅ 기존 유지 |
| Quota 관리 | 변경 불필요 | ✅ 기존 유지 |
| Structured Output | 변경 불필요 (responseSchema로 전달) | ✅ 기존 유지 |
| maxTokens | proto 확장 또는 벤더 옵션으로 조정 검토 | 🔍 검토 |
| 프롬프트 정의 | Backend 측에서 정의 (Spring-AI는 전달만) | ℹ️ Backend 담당 |
| 캐싱 | 동일 요청 캐싱 검토 | 🔍 선택 |

**결론: Spring-AI 모듈은 범용 게이트웨이로서 현재 구조를 유지하며, 주요 개발 작업은 Backend 모듈에서 진행됩니다. Spring-AI에서는 필요 시 maxTokens 제어와 요청 타입별 모니터링 정도만 추가하면 됩니다.**
