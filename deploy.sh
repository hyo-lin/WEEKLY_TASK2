#!/bin/bash
set -e

# ===== 사용법 =====
# ./deploy.sh <ECR_URI> <IMAGE_TAG> <TG_ARN> <INSTANCE_ID>
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

STATE_FILE=".active_color"

echo "===== 0. ECR 로그인 ====="
AWS_REGION=$(echo "$ECR_URI" | cut -d. -f4)
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin "$ECR_URI"

echo "===== 1. 현재 활성 상태 확인 ====="
# 상태 파일이 없으면 최초 배포로 간주하고 blue를 활성 상태로 초기화
if [ ! -f "$STATE_FILE" ]; then
  echo "blue" > "$STATE_FILE"
fi
ACTIVE=$(cat "$STATE_FILE")

if [ "$ACTIVE" == "blue" ]; then
  IDLE="green"
  ACTIVE_PORT=8080
  IDLE_PORT=8081
else
  IDLE="blue"
  ACTIVE_PORT=8081
  IDLE_PORT=8080
fi
echo "현재 활성: $ACTIVE ($ACTIVE_PORT) / 이번 배포 대상: $IDLE ($IDLE_PORT)"

echo "===== 2. $IDLE 이미지 pull & 기동 ($IDLE_PORT) ====="
docker-compose pull "be-$IDLE"
docker-compose up -d "be-$IDLE"

echo "===== 3. 내부 헬스체크 ($IDLE 부팅 대기 및 검증) ====="
echo "스프링 부트 구동 대기 중 (30초)..."
sleep 30

MAX_RETRIES=5
SUCCESS_COUNT=0
for i in $(seq 1 $MAX_RETRIES); do
  if curl -f -s "http://localhost:$IDLE_PORT/health/check" > /dev/null; then
    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    echo "내부 헬스체크 통과 ($SUCCESS_COUNT/3)"
    if [ "$SUCCESS_COUNT" -eq 3 ]; then
      break
    fi
  else
    echo "애플리케이션이 아직 준비되지 않음... (재시도 $i/$MAX_RETRIES)"
    SUCCESS_COUNT=0
  fi
  sleep 5
done

if [ "$SUCCESS_COUNT" -lt 3 ]; then
  echo "내부 헬스체크 최종 실패. $IDLE 종료 후 배포 중단. (기존 $ACTIVE 는 그대로 서비스 중)"
  docker stop "be-$IDLE" || true
  docker rm "be-$IDLE" || true
  exit 1
fi

echo "===== 4. $IDLE 를 타겟그룹에 등록 (포트 $IDLE_PORT) ====="
aws elbv2 register-targets \
  --target-group-arn "$TG_ARN" \
  --targets Id="$INSTANCE_ID",Port=$IDLE_PORT

echo "===== 5. ALB 자체 헬스체크 통과 대기 ====="
for i in $(seq 1 10); do
  STATE=$(aws elbv2 describe-target-health \
    --target-group-arn "$TG_ARN" \
    --targets Id="$INSTANCE_ID",Port=$IDLE_PORT \
    --query 'TargetHealthDescriptions[0].TargetHealth.State' \
    --output text)
  echo "ALB 헬스 상태: $STATE ($i/10)"
  if [ "$STATE" == "healthy" ]; then
    break
  fi
  if [ "$i" -eq 10 ]; then
    echo "ALB 헬스체크 통과 실패. 타겟그룹 등록 취소 및 $IDLE 종료. (기존 $ACTIVE 는 그대로 서비스 중)"
    aws elbv2 deregister-targets --target-group-arn "$TG_ARN" --targets Id="$INSTANCE_ID",Port=$IDLE_PORT
    docker stop "be-$IDLE" && docker rm "be-$IDLE"
    exit 1
  fi
  sleep 10
done

echo "===== 6. 기존 $ACTIVE 를 타겟그룹에서 제외 ====="
aws elbv2 deregister-targets \
  --target-group-arn "$TG_ARN" \
  --targets Id="$INSTANCE_ID",Port=$ACTIVE_PORT

echo "===== 7. 기존 $ACTIVE 컨테이너 종료 ====="
# 완전히 삭제하지 않고 정지만 함 - 다음 배포 때 이 자리가 새 검증 대상이 되므로
docker stop "be-$ACTIVE" || true

echo "===== 8. 활성 상태 갱신: $ACTIVE -> $IDLE ====="
echo "$IDLE" > "$STATE_FILE"

echo "===== 배포 완료 (현재 활성: $IDLE, 포트 $IDLE_PORT) ====="
docker ps