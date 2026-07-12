#!/bin/bash
set -e

ECR_URI=$1
IMAGE_TAG=$2
TG_ARN=$3
INSTANCE_ID=$4

if [ -z "$ECR_URI" ] || [ -z "$IMAGE_TAG" ] || [ -z "$TG_ARN" ] || [ -z "$INSTANCE_ID" ]; then
  echo "사용법: ./deploy.sh <ECR_URI> <IMAGE_TAG> <TG_ARN> <INSTANCE_ID>"
  exit 1
fi

export ECR_URI
export IMAGE_TAG

echo "===== 1. green 이미지 pull & 기동 (8081) ====="
docker-compose --profile deploying pull be-green
docker-compose --profile deploying up -d be-green

echo "===== 2. 내부 헬스체크 (green, 연속 3회 통과 필요) ====="
for i in 1 2 3; do
  if curl -f -s http://localhost:8081/health/check > /dev/null; then
    echo "내부 헬스체크 $i/3 통과"
    sleep 5
  else
    echo "내부 헬스체크 실패. green 종료 후 배포 중단."
    docker stop be-green || true
    docker rm be-green || true
    exit 1
  fi
done

echo "===== 3. green을 타겟그룹에 등록 (포트 8081) ====="
aws elbv2 register-targets \
  --target-group-arn "$TG_ARN" \
  --targets Id="$INSTANCE_ID",Port=8081

echo "===== 4. ALB 자체 헬스체크 통과 대기 ====="
for i in $(seq 1 10); do
  STATE=$(aws elbv2 describe-target-health \
    --target-group-arn "$TG_ARN" \
    --targets Id="$INSTANCE_ID",Port=8081 \
    --query 'TargetHealthDescriptions[0].TargetHealth.State' \
    --output text)
  echo "ALB 헬스 상태: $STATE ($i/10)"
  if [ "$STATE" == "healthy" ]; then
    break
  fi
  if [ "$i" -eq 10 ]; then
    echo "ALB 헬스체크 통과 실패. 타겟그룹 등록 취소 및 green 종료."
    aws elbv2 deregister-targets --target-group-arn "$TG_ARN" --targets Id="$INSTANCE_ID",Port=8081
    docker stop be-green && docker rm be-green
    exit 1
  fi
  sleep 10
done

echo "===== 5. blue(8080)를 타겟그룹에서 제외 ====="
aws elbv2 deregister-targets \
  --target-group-arn "$TG_ARN" \
  --targets Id="$INSTANCE_ID",Port=8080

echo "===== 6. 기존 blue 컨테이너 종료 ====="
docker stop be-blue || true
docker rm be-blue || true

echo "===== 7. green 이미지로 blue(8080) 재기동 후 타겟그룹 등록 ====="
docker-compose --profile deploying up -d be-blue
aws elbv2 register-targets \
  --target-group-arn "$TG_ARN" \
  --targets Id="$INSTANCE_ID",Port=8080

echo "===== 8. green(8081) 정리 ====="
docker stop be-green || true
docker rm be-green || true
aws elbv2 deregister-targets \
  --target-group-arn "$TG_ARN" \
  --targets Id="$INSTANCE_ID",Port=8081 || true

echo "===== 배포 완료 ====="
docker ps