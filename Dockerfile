# 멀티 스테이지 빌드를 사용한 Spring Boot 애플리케이션 Dockerfile

# Stage 1: 빌드 스테이지
FROM gradle:8.10-jdk21 AS build

WORKDIR /app

# Gradle 캐시를 활용하기 위해 의존성 파일 먼저 복사
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# 의존성 다운로드 (캐시 활용)
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드
RUN gradle build -x test --no-daemon

# Stage 2: 실행 스테이지
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 애플리케이션 실행을 위한 사용자 생성 (보안)
RUN groupadd -r spring && useradd -r -g spring spring
RUN chown -R spring:spring /app
USER spring:spring

# 헬스체크 추가 (간단한 TCP 포트 확인)
# nc (netcat)가 없을 수 있으므로, 헬스체크는 선택사항입니다
# 필요시 다음 명령으로 헬스체크를 활성화하세요:
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD sh -c 'exec 3<>/dev/tcp/localhost/8080' || exit 1

# 포트 노출 (기본값 8080, application.yml에서 변경 가능)
EXPOSE 8080

# JVM 옵션 환경 변수 (컨테이너 메모리 제한 자동 인식)
# 컨테이너 메모리의 75%를 힙에 할당 (나머지는 메타스페이스, 스택 등에 사용)
ENV JAVA_MAX_RAM_PERCENTAGE=75.0
ENV JAVA_INITIAL_RAM_PERCENTAGE=50.0
ENV JAVA_MIN_RAM_PERCENTAGE=25.0

# 추가 JVM 옵션 (기본값, 환경 변수로 오버라이드 가능)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# 애플리케이션 실행
# 컨테이너 메모리 제한을 자동으로 인식하여 힙 크기 조정
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=${JAVA_MAX_RAM_PERCENTAGE} -XX:InitialRAMPercentage=${JAVA_INITIAL_RAM_PERCENTAGE} -XX:MinRAMPercentage=${JAVA_MIN_RAM_PERCENTAGE} $JAVA_OPTS -jar app.jar"]
