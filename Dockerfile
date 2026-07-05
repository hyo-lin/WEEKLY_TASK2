# 빌드 이미지 (JDK)
FROM eclipse-temurin:26-jdk-alpine AS builder

WORKDIR /app

# Gradle 파일 먼저 복사 (레이어 캐싱)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사 후 빌드
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# 실행 이미지 (JRE)
FROM eclipse-temurin:26-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]