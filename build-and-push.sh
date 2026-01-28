#!/bin/bash

# Docker 이미지 빌드 및 푸시 스크립트
# 사용법: ./build-and-push.sh [이미지명] [태그] [Docker Hub 사용자명]

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 기본값 설정
DEFAULT_IMAGE_NAME="ai-demo"
DEFAULT_TAG="latest"
DEFAULT_DOCKER_USER=""

# 파라미터 처리
IMAGE_NAME=${1:-$DEFAULT_IMAGE_NAME}
TAG=${2:-$DEFAULT_TAG}
DOCKER_USER=${3:-$DEFAULT_DOCKER_USER}

# Docker Hub 사용자명이 없으면 환경 변수에서 가져오기
if [ -z "$DOCKER_USER" ]; then
    DOCKER_USER=${DOCKERHUB_USERNAME:-""}
fi

# Docker Hub 사용자명이 여전히 없으면 에러
if [ -z "$DOCKER_USER" ]; then
    echo -e "${RED}Error: Docker Hub 사용자명이 필요합니다.${NC}"
    echo "사용법: $0 [이미지명] [태그] [Docker Hub 사용자명]"
    echo "또는 DOCKERHUB_USERNAME 환경 변수를 설정하세요."
    exit 1
fi

# 전체 이미지 이름 구성
FULL_IMAGE_NAME="${DOCKER_USER}/${IMAGE_NAME}:${TAG}"
LATEST_IMAGE_NAME="${DOCKER_USER}/${IMAGE_NAME}:latest"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Docker 이미지 빌드 및 푸시${NC}"
echo -e "${GREEN}========================================${NC}"
echo "이미지명: ${FULL_IMAGE_NAME}"
echo "태그: ${TAG}"
echo ""

# 1. Gradle 빌드
echo -e "${YELLOW}[1/4] Gradle 빌드 중...${NC}"
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo -e "${RED}Gradle 빌드 실패${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle 빌드 완료${NC}"
echo ""

# 2. Docker Hub 로그인 확인
echo -e "${YELLOW}[2/5] Docker Hub 로그인 확인 중...${NC}"
if ! docker info 2>/dev/null | grep -q "Username"; then
    echo -e "${YELLOW}Docker Hub에 로그인이 필요합니다.${NC}"
    docker login
    if [ $? -ne 0 ]; then
        echo -e "${RED}Docker Hub 로그인 실패${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✓ Docker Hub 로그인 확인 완료${NC}"
echo ""

# 3. buildx 빌더 확인 및 설정 (멀티 플랫폼 빌드용)
echo -e "${YELLOW}[3/5] buildx 빌더 확인 중...${NC}"
BUILDER_NAME="multiarch-builder"
if ! docker buildx inspect ${BUILDER_NAME} >/dev/null 2>&1; then
    echo -e "${YELLOW}멀티 플랫폼 빌더 생성 중...${NC}"
    docker buildx create --name ${BUILDER_NAME} --use --bootstrap
    if [ $? -ne 0 ]; then
        echo -e "${YELLOW}빌더 생성 실패, 기본 빌더 사용${NC}"
        BUILDER_NAME="default"
    fi
else
    docker buildx use ${BUILDER_NAME}
fi
echo -e "${GREEN}✓ buildx 빌더 준비 완료${NC}"
echo ""

# 4. Docker 이미지 빌드 및 푸시 (멀티 플랫폼: linux/amd64, linux/arm64)
echo -e "${YELLOW}[4/5] Docker 이미지 빌드 및 푸시 중 (linux/amd64, linux/arm64)...${NC}"

# 빌드할 태그 목록
TAGS=("${FULL_IMAGE_NAME}")
if [ "$TAG" != "latest" ]; then
    TAGS+=("${LATEST_IMAGE_NAME}")
fi

# buildx를 사용하여 멀티 플랫폼 빌드 및 푸시
docker buildx build \
    --platform linux/amd64,linux/arm64 \
    --tag ${FULL_IMAGE_NAME} \
    $(if [ "$TAG" != "latest" ]; then echo "--tag ${LATEST_IMAGE_NAME}"; fi) \
    --push \
    .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker 이미지 빌드 및 푸시 실패${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 이미지 빌드 및 푸시 완료${NC}"
echo ""

# 5. 완료
echo -e "${YELLOW}[5/5] 완료${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ 모든 작업 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo "이미지: ${FULL_IMAGE_NAME}"
if [ "$TAG" != "latest" ]; then
    echo "이미지 (latest): ${LATEST_IMAGE_NAME}"
fi
echo ""
echo "플랫폼: linux/amd64, linux/arm64"
echo ""
echo "다음 명령으로 실행할 수 있습니다:"
echo "  docker run -p 8080:8080 ${FULL_IMAGE_NAME}"
echo ""
echo "또는 특정 플랫폼으로 실행:"
echo "  docker run --platform linux/amd64 -p 8080:8080 ${FULL_IMAGE_NAME}"