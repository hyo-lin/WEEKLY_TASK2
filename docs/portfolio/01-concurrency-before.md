# 01. 동시성 문제 — 수정 전(Before) 측정

> 대상: 문제 1 (좋아요·댓글 수 갱신 손실, 조회수 flush 덮어쓰기). 프로덕션 코드는 수정하지 않은 상태(브랜치 `portfolio-improve`)에서 측정했다.

## 측정 환경

- 테스트: `src/test/java/com/example/community/concurrency/ConcurrencyBeforeTest.java` (기반 클래스 `ConcurrencyTestSupport`)
- Testcontainers로 실제 `mysql:8.0`(기본 격리수준 REPEATABLE READ), `redis:7` 구동. Spring `test` 프로파일, `ddl-auto: create-drop`
- 서비스 메서드(`PostLikeService.like`, `CommentService.createComment`, `ViewCountSyncScheduler.sync`)를 `ExecutorService` + `CountDownLatch`로 동시에 직접 호출. HTTP/JWT 필터는 거치지 않음
- 커넥션 풀: Hikari 기본값(설정 파일에 `hikari` 항목 없음 → 최대 10개). 요청 스레드는 100개
- 백그라운드 스케줄러는 주기를 하루로 늘려 꺼 두고, `sync()`는 테스트가 직접 호출 (테스트 전용 프로퍼티, 프로덕션 설정 변경 없음)
- 각 케이스 5회 반복. 표의 값은 **최소 / 평균 / 최대**
- 500 건수: `GlobalExceptionHandler`에 `GeneralException`·검증 예외 외의 처리기가 없으므로, `GeneralException`이 아닌 예외는 HTTP 500이 될 것으로 보고 센 값이다 (HTTP로 직접 확인한 것은 아님)

## 결과 요약

1. **예상했던 "조용한 갱신 손실"보다 먼저 MySQL 데드락이 지배적으로 나타났다.** 100건 동시 요청 중 약 82~87건이 `CannotAcquireLockException`(MySQL 1213 Deadlock)으로 실패한다.
2. 데드락을 피해 **커밋에 성공한 요청들 사이에서도 카운터가 어긋난다** (좋아요 17~18건 커밋 → `like_count`는 8~9). 갱신 손실은 5회 모두 재현됐다.
3. 같은 사용자의 중복 좋아요도 `ALREADY_LIKED`(409류)나 unique 위반이 아니라 데드락으로 실패한다. 동시 2건만 보내도 1건은 실패.
4. 조회수 flush 덮어쓰기(`view_count` 되돌림)는 **재현 안 됨** (아래 참고).

### 1. 좋아요 갱신 손실 (서로 다른 사용자 100명 → 같은 글)

| 항목 | 값 (최소 / 평균 / 최대) |
|---|---|
| 동시 요청 수 | 100 / 100.0 / 100 |
| 기대값 (like_count) | 100 / 100.0 / 100 |
| 성공(커밋)한 요청 = post_like 행 수 | 17 / 17.6 / 18 |
| 실제 like_count | 8 / 8.4 / 9 |
| 손실 건수 (커밋된 행 − like_count) | 8 / 9.2 / 10 |
| 실패 요청 수 (평균, 5회 합계 412건) | 82.4 |
| 실패 원인 | CannotAcquireLockException<-MySQLTransactionRollbackException=412 |

### 2. 댓글 수 갱신 손실 (서로 다른 사용자 100명 → 같은 글)

| 항목 | 값 (최소 / 평균 / 최대) |
|---|---|
| 동시 요청 수 | 100 / 100.0 / 100 |
| 기대값 (comment_count) | 100 / 100.0 / 100 |
| 성공(커밋)한 요청 = comment 행 수 | 13 / 13.8 / 15 |
| 실제 comment_count | 7 / 7.4 / 8 |
| 손실 건수 (커밋된 행 − comment_count) | 6 / 6.4 / 7 |
| 실패 요청 수 (평균, 5회 합계 431건) | 86.2 |
| 실패 원인 | CannotAcquireLockException<-MySQLTransactionRollbackException=431 |

> 손실 건수는 "실제로 커밋된 행 수 − 카운터"로 계산했다. 데드락으로 롤백된 요청은 행도 카운터도 늘지 않으므로 손실에 포함하지 않았다. 100건 대비 최종 카운터가 8 안팎인 것은 데드락 실패 + 갱신 손실이 합쳐진 결과다.

### 3. 같은 사용자의 동시 중복 좋아요

| 동시 요청 수 | 성공 (최소/평균/최대) | post_like 행 수 | like_count | 500 건수 (최소/평균/최대) | 실패 원인 |
|---|---|---|---|---|---|
| 2 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | CannotAcquireLockException<-MySQLTransactionRollbackException=5 |
| 5 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | 4 / 4.0 / 4 | CannotAcquireLockException<-MySQLTransactionRollbackException=20 |
| 10 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | 1 / 1.0 / 1 | 9 / 9.0 / 9 | CannotAcquireLockException<-MySQLTransactionRollbackException=45 |

- 데이터는 중복 없이 1행으로 보존되지만(unique 제약 + 데드락 롤백), 나머지 요청은 `ALREADY_LIKED`가 아니라 데드락 예외로 끝난다. 사용자에게는 의미 없는 500이다.
- 이번 측정에서는 unique 위반(`DataIntegrityViolationException`) 형태는 관측되지 않았다. 중복 검사와 insert 경합이 있어도 InnoDB가 먼저 데드락을 일으켜 그 경로까지 가지 못한 것으로 추정한다.

### 4. 조회수 flush(`sync()`) × 좋아요 동시 실행

조건: 좋아요 100건 + Redis 조회 500건 유입 + `sync()` 반복 호출을 동시에 실행한 뒤, 마지막에 `sync()`를 한 번 더 호출.

| 항목 | 값 (최소 / 평균 / 최대) |
|---|---|
| 기대 view_count | 500 / 500.0 / 500 |
| 실제 view_count | 500 / 500.0 / 500 |
| view_count 손실 | 0 / 0.0 / 0 |
| 커밋된 좋아요 수 | 18 / 18.6 / 20 |
| 실제 like_count | 8 / 8.6 / 9 |
| like_count 손실 | 9 / 10.0 / 11 |

- **`view_count` 되돌림: 재현 안 됨** (5회 모두 500 = 500, 손실 0).
- **`like_count`가 sync에 의해 되돌려졌는지는 구분 불가.** 손실이 10건 안팎으로 케이스 1(sync 없음)의 손실 8~10건과 같은 수준이라, sync 영향이 따로 드러나지 않았다.
- 재현되지 않은 이유(추정):
  1. 좋아요 트랜잭션 대부분이 데드락으로 먼저 실패해, 오래된 `view_count`를 쓰고 커밋하는 트랜잭션 자체가 적다.
  2. `sync()`는 `findById` 직후 같은 트랜잭션에서 flush하므로 오래된 값을 쥐고 있는 구간이 짧아, 이번 타이밍에서는 겹치지 않았다.
  3. 이 문제는 Hibernate가 전체 컬럼 UPDATE를 쓰는 구조상 이론적으로 가능하지만, 이번 측정은 그것을 보이지 못했다. 코드가 위험하다는 주장은 하되 "실측으로 확인됨"이라고 쓰지 않는다.
- 확인하려면 `like` 트랜잭션이 Post를 읽은 뒤 커밋 직전에 멈추도록 하는 결정적 인터리빙 테스트(훅이나 `CountDownLatch` 삽입)가 필요하다. 이번 단계에서는 만들지 않았다.

## 해석과 다음 단계 제안

- 원인(2단계에서 확인됨): `post_like`/`comment` insert가 FK 검사로 `post` 행에 공유 락(S)을 잡고, 이어서 `UPDATE post`가 배타 락(X)을 요구한다. 여러 트랜잭션이 S를 쥔 채 서로의 X를 기다리다 데드락이 된다. 아래 캡처 참고.
- 따라서 개선 전후 지표는 "손실 건수" 하나가 아니라 **성공률(데드락 포함 실패율)과 최종 카운터 정합성** 두 가지로 잡는 것이 맞다. 목표: 100건 → 성공 100, `like_count` = 100, 500 = 0.
- 재현 방법: `./gradlew test --tests '*ConcurrencyBeforeTest*'` (Docker 필요). 원시 결과는 `build/concurrency-results.jsonl`.

## 데드락 캡처 (`SHOW ENGINE INNODB STATUS` → LATEST DETECTED DEADLOCK)

좋아요 100건 동시 실행 직후(`DeadlockDiagnosisTest`) 캡처. 레코드 hex 덤프는 생략했다.

- (1)(2) 두 트랜잭션 모두 `post` PRIMARY 레코드에 **S 락 보유**(`lock mode S locks rec but not gap`)
- (1)(2) 두 트랜잭션 모두 같은 레코드의 **X 락 대기**(`lock_mode X locks rec but not gap waiting`)
- → FK 검사의 공유 락(INSERT) 이후 `UPDATE post`가 배타 락으로 승격하려다 서로 대기하는 **락 승격 데드락**. 가설과 일치.
- 대기 SQL이 `like_count`뿐 아니라 `comment_count`, `title`, `view_count` 등 **전체 컬럼**을 쓰는 UPDATE인 점은 Hibernate가 `@DynamicUpdate` 없이 전체 컬럼을 갱신하기 때문이다.

```text
LATEST DETECTED DEADLOCK
------------------------
2026-11-26 12:46:27 281473060552448
*** (1) TRANSACTION:
TRANSACTION 2373, ACTIVE 0 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 7 lock struct(s), heap size 1128, 3 row lock(s), undo log entries 1
MySQL thread id 12, OS thread handle 281473369550592, query id 1340 192.168.65.1 root updating
/* update for com.example.community.post.model.Post */update post set comment_count=0,content='content',deleted_at=null,like_count=9,title='title',user_id=1,view_count=0 where post_id=1

*** (1) HOLDS THE LOCK(S):
RECORD LOCKS space id 11 page no 4 n bits 72 index PRIMARY of table `community`.`post` trx id 2373 lock mode S locks rec but not gap
 7: SQL NULL;


*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 11 page no 4 n bits 72 index PRIMARY of table `community`.`post` trx id 2373 lock_mode X locks rec but not gap waiting
 7: SQL NULL;


*** (2) TRANSACTION:
TRANSACTION 2372, ACTIVE 0 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 7 lock struct(s), heap size 1128, 3 row lock(s), undo log entries 1
MySQL thread id 8, OS thread handle 281473374023424, query id 1342 192.168.65.1 root updating
/* update for com.example.community.post.model.Post */update post set comment_count=0,content='content',deleted_at=null,like_count=9,title='title',user_id=1,view_count=0 where post_id=1

*** (2) HOLDS THE LOCK(S):
RECORD LOCKS space id 11 page no 4 n bits 72 index PRIMARY of table `community`.`post` trx id 2372 lock mode S locks rec but not gap
 7: SQL NULL;


*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 11 page no 4 n bits 72 index PRIMARY of table `community`.`post` trx id 2372 lock_mode X locks rec but not gap waiting
 7: SQL NULL;

*** WE ROLL BACK TRANSACTION (2)
```
