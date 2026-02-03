# Docker 빌드 및 실행 가이드

## 자동 빌드 및 푸시 스크립트

### 빠른 시작
```bash
# Docker Hub 사용자명을 환경 변수로 설정
export DOCKERHUB_USERNAME=your-username

# 빌드 및 푸시 (기본: latest 태그)
./build-and-push.sh

# 특정 태그 지정
./build-and-push.sh ai-demo v1.0.0 your-username

# 버전 기반 빌드 (build.gradle.kts의 version 사용)
./build-and-push-version.sh ai-demo your-username
```

### 스크립트 옵션

#### `build-and-push.sh`
```bash
./build-and-push.sh [이미지명] [태그] [Docker Hub 사용자명]
```

**예시:**
```bash
# 기본값 사용 (latest 태그)
./build-and-push.sh

# 커스텀 이미지명과 태그
./build-and-push.sh my-ai-app v1.0.0 myusername

# 환경 변수 사용
export DOCKERHUB_USERNAME=myusername
./build-and-push.sh ai-demo v1.0.0
```

#### `build-and-push-version.sh`
`build.gradle.kts`의 `version`을 자동으로 읽어서 태그로 사용합니다.

```bash
./build-and-push-version.sh [이미지명] [Docker Hub 사용자명]
```

**예시:**
```bash
# build.gradle.kts의 version이 "0.0.1-SNAPSHOT"이면
# 태그는 "0.0.1"로 생성됩니다
./build-and-push-version.sh ai-demo myusername
```

### 스크립트 동작 과정
1. ✅ Gradle 빌드 (`./gradlew clean build -x test`)
2. ✅ Docker 이미지 빌드
3. ✅ latest 태그 추가 (태그가 latest가 아닌 경우)
4. ✅ Docker Hub 로그인 확인
5. ✅ Docker Hub에 푸시

## 수동 빌드

### 단일 이미지 빌드
```bash
docker build -t ai-demo:latest .
```

### 태그 지정 빌드
```bash
docker build -t ai-demo:1.0.0 .
```

## 실행

### Docker Compose 사용 (권장)
```bash
# .env 파일 생성 (선택사항)
cat > .env << EOF
MYSQL_ROOT_PASSWORD=my-secret-pw
MYSQL_USER=ai_user
MYSQL_PASSWORD=ai_password
MYSQL_PORT=3306
APP_PORT=8080
OPENAI_API_KEY=your-openai-key
ANTHROPIC_API_KEY=your-anthropic-key
GEMINI_API_KEY=your-gemini-key
GROK_API_KEY=your-grok-key
JWT_SECRET=your-jwt-secret-key-at-least-32-characters-long
SPRING_PROFILES_ACTIVE=prod
EOF

# 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f app

# 중지
docker-compose down

# 볼륨까지 삭제
docker-compose down -v
```

### 단일 컨테이너 실행
```bash
# MySQL 먼저 실행
docker run -d \
  --name ai-mysql \
  -e MYSQL_ROOT_PASSWORD=my-secret-pw \
  -e MYSQL_DATABASE=ai \
  -p 3306:3306 \
  mysql:8.0

# 애플리케이션 실행
docker run -d \
  --name ai-app \
  -p 8080:8080 \
  -m 1g \
  --memory-swap=1g \
  --link ai-mysql:mysql \
  -e MYSQL_HOST=mysql \
  -e MYSQL_PORT=3317 \
  -e MYSQL_USERNAME=root \
  -e MYSQL_PASSWORD=my-secret-pw \
  -e OPENAI_API_KEY=your-key \
  -e ANTHROPIC_API_KEY=your-key \
  -e GEMINI_API_KEY=your-key \
  -e GROK_API_KEY=your-key \
  -e JWT_SECRET=your-secret-key \
  -e JAVA_MAX_RAM_PERCENTAGE=75.0 \
  ai-demo:latest
```

## 환경 변수

### 필수 환경 변수
- `MYSQL_HOST`: MySQL 호스트 (기본값: localhost)
- `MYSQL_PORT`: MySQL 포트 (기본값: 3306)
- `MYSQL_USERNAME`: MySQL 사용자명
- `MYSQL_PASSWORD`: MySQL 비밀번호

### 선택적 환경 변수

#### 애플리케이션 설정
- `OPENAI_API_KEY`: OpenAI API 키
- `ANTHROPIC_API_KEY`: Anthropic API 키
- `GEMINI_API_KEY`: Google Gemini API 키
- `GROK_API_KEY`: xAI Grok API 키
- `JWT_SECRET`: JWT 시크릿 키 (최소 32자)
- `SPRING_PROFILES_ACTIVE`: Spring 프로파일 (기본값: prod)

#### JVM 메모리 설정 (컨테이너 친화적)
컨테이너 메모리 제한을 자동으로 인식하여 힙 크기를 조정합니다.

- `JAVA_MAX_RAM_PERCENTAGE`: 컨테이너 메모리의 최대 힙 비율 (기본값: 75.0)
  - 예: 컨테이너가 1GB면 힙은 약 750MB
- `JAVA_INITIAL_RAM_PERCENTAGE`: 초기 힙 크기 비율 (기본값: 50.0)
- `JAVA_MIN_RAM_PERCENTAGE`: 최소 힙 크기 비율 (기본값: 25.0)
- `JAVA_OPTS`: 추가 JVM 옵션 (기본값: 컨테이너 지원 + G1GC)
  - 기본값: `-XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError`

#### 컨테이너 리소스 제한
- `CONTAINER_MEMORY_LIMIT`: 컨테이너 메모리 제한 (기본값: 1G)
- `CONTAINER_MEMORY_RESERVATION`: 컨테이너 메모리 예약 (기본값: 512M)

## 포트

- `8080`: Spring Boot 애플리케이션
- `3306`: MySQL 데이터베이스

## 헬스체크

컨테이너는 자동으로 헬스체크를 수행합니다. Spring Boot Actuator의 `/actuator/health` 엔드포인트를 사용합니다.

```bash
# 헬스체크 확인
docker inspect --format='{{.State.Health.Status}}' ai-app
```

## 문제 해결

### 빌드 실패
- Java 23이 설치되어 있는지 확인
- Gradle 래퍼 권한 확인: `chmod +x gradlew`

### 연결 오류
- MySQL 컨테이너가 실행 중인지 확인
- 네트워크 설정 확인
- 환경 변수 확인

### 메모리 부족
컨테이너 메모리 제한을 늘리거나 JVM 메모리 비율을 조정하세요:

```bash
# 방법 1: 컨테이너 메모리 제한 증가 (docker-compose.yml)
deploy:
  resources:
    limits:
      memory: 2G

# 방법 2: JVM 힙 비율 조정
-e JAVA_MAX_RAM_PERCENTAGE=80.0

# 방법 3: docker run 시 메모리 제한 설정
docker run -m 2g --memory-swap=2g ...
```

**참고**: `-Xmx`, `-Xms` 대신 `JAVA_MAX_RAM_PERCENTAGE`를 사용하면 컨테이너 메모리 제한에 자동으로 맞춰집니다.
