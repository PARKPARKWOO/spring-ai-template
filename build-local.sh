#!/bin/bash

# 로컬에서만 빌드하는 스크립트 (푸시 없음)
# Mac에서 Linux용으로 빌드하려면 이 스크립트 사용

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 기본값 설정
DEFAULT_IMAGE_NAME="ai-demo"
DEFAULT_TAG="latest"
DEFAULT_PLATFORM="linux/amd64"

# 파라미터 처리
IMAGE_NAME=${1:-$DEFAULT_IMAGE_NAME}
TAG=${2:-$DEFAULT_TAG}
PLATFORM=${3:-$DEFAULT_PLATFORM}

FULL_IMAGE_NAME="${IMAGE_NAME}:${TAG}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}로컬 Docker 이미지 빌드${NC}"
echo -e "${GREEN}========================================${NC}"
echo "이미지명: ${FULL_IMAGE_NAME}"
echo "플랫폼: ${PLATFORM}"
echo ""

# 1. Gradle 빌드
echo -e "${YELLOW}[1/3] Gradle 빌드 중...${NC}"
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo -e "${RED}Gradle 빌드 실패${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle 빌드 완료${NC}"
echo ""

# 2. Docker 이미지 빌드 (특정 플랫폼)
echo -e "${YELLOW}[2/3] Docker 이미지 빌드 중 (${PLATFORM})...${NC}"
docker buildx build \
    --platform ${PLATFORM} \
    --tag ${FULL_IMAGE_NAME} \
    --load \
    .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker 이미지 빌드 실패${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 이미지 빌드 완료: ${FULL_IMAGE_NAME}${NC}"
echo ""

# 3. 완료
echo -e "${YELLOW}[3/3] 완료${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ 빌드 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo "이미지: ${FULL_IMAGE_NAME}"
echo "플랫폼: ${PLATFORM}"
echo ""
echo "다음 명령으로 실행할 수 있습니다:"
echo "  docker run -p 8080:8080 ${FULL_IMAGE_NAME}"
