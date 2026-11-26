package com.example.community;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * 통합 테스트 공통 기반. 로컬 MySQL·환경변수 없이 Testcontainers(MySQL 8.0, Redis 7)로 DB 를 띄운다.
 * 컨테이너는 테스트 JVM 안에서 한 번만 시작해 모든 통합 테스트가 공유하고, 같은 설정이면 Spring 컨텍스트도 캐시된다.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0")
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withEnv("MYSQL_DATABASE", "community")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*\\s", 1)
                    .withStartupTimeout(java.time.Duration.ofMinutes(3)));

    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true");
        r.add("spring.datasource.username", () -> "root");
        r.add("spring.datasource.password", () -> "test");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 백그라운드 스케줄러가 테스트에 끼어들지 않도록 사실상 비활성화 (sync() 는 테스트가 직접 호출)
        r.add("scheduler.sync.fixed-delay", () -> "86400000");
    }
}
