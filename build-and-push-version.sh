#!/bin/bash

# 버전 기반 Docker 이미지 빌드 및 푸시 스크립트
# build.gradle.kts의 version을 읽어서 자동으로 태그를 생성합니다.

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# build.gradle.kts에서 버전 읽기
VERSION=$(grep -E "^version\s*=" build.gradle.kts | sed -E "s/.*version\s*=\s*[\"']([^\"']+)[\"'].*/\1/" | head -1)

if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: build.gradle.kts에서 version을 찾을 수 없습니다.${NC}"
    exit 1
fi

# 버전에서 SNAPSHOT 제거 및 태그 생성
TAG=$(echo $VERSION | sed 's/-SNAPSHOT//')
IMAGE_NAME=${1:-"ai-demo"}
DOCKER_USER=${2:-${DOCKERHUB_USERNAME:-""}}

if [ -z "$DOCKER_USER" ]; then
    echo -e "${RED}Error: Docker Hub 사용자명이 필요합니다.${NC}"
    echo "사용법: $0 [이미지명] [Docker Hub 사용자명]"
    echo "또는 DOCKERHUB_USERNAME 환경 변수를 설정하세요."
    exit 1
fi

FULL_IMAGE_NAME="${DOCKER_USER}/${IMAGE_NAME}:${TAG}"
LATEST_IMAGE_NAME="${DOCKER_USER}/${IMAGE_NAME}:latest"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}버전 기반 Docker 이미지 빌드 및 푸시${NC}"
echo -e "${GREEN}========================================${NC}"
echo "버전: ${VERSION}"
echo "태그: ${TAG}"
echo "이미지: ${FULL_IMAGE_NAME}"
echo ""

# build-and-push.sh 호출
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${SCRIPT_DIR}/build-and-push.sh" "${IMAGE_NAME}" "${TAG}" "${DOCKER_USER}"
