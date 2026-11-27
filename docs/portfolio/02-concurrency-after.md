# 02. 동시성 개선 — 방식 비교와 최종 적용 (After)

> 전제: `01-concurrency-before.md` 의 수정 전 수치. 같은 조건(MySQL 8.0 / Redis 7 Testcontainers, 동시 100건, 5회 반복, Hikari 풀 기본 10)에서 측정했다.
> 표의 값은 **최소 / 평균 / 최대**.

## 1. 데드락 원인 확인 (01 문서에도 캡처 첨부)

`SHOW ENGINE INNODB STATUS` 의 `LATEST DETECTED DEADLOCK` 에서 **가설이 그대로 확인됐다.**

- 두 트랜잭션 모두 `post` 의 PRIMARY 레코드에 `lock mode S locks rec but not gap` 을 **보유**(HOLDS)
- 두 트랜잭션 모두 같은 레코드에 `lock_mode X locks rec but not gap` 을 **대기**(WAITING)
- 대기 중인 SQL 은 둘 다 `update post set comment_count=0,content=...,like_count=9,... where post_id=1` — Hibernate 가 **전체 컬럼**을 쓰는 UPDATE
- S 락의 출처는 `post_like` INSERT 의 FK 검사. 즉 INSERT(post 공유 락) → UPDATE post(배타 락 승격) 순서에서, 여러 트랜잭션이 S 를 쥔 채 서로의 X 를 기다리는 **락 승격 데드락**이다.

## 2. 핵심 발견: 두 문제는 원인이 다르다

| 증상 | 원인 | 확인 |
|---|---|---|
| 갱신 손실 (커밋된 행 > 카운터) | 엔티티를 읽고 `++` 후 flush 하는 read-modify-write | 기준선(원자적 UPDATE)에서 **불일치 0** 으로 사라짐 |
| 데드락 (100건 중 82~87건 실패) | INSERT(FK 공유 락) → UPDATE(배타 락) **락 순서** | 기준선에서도 **그대로 남음** (84~88건 실패) |

원자적 UPDATE 만으로는 반쪽짜리 해결이고, 락 순서(A) 또는 락 대상(B)을 바꿔야 데드락이 사라진다.

## 3. 동시 100건 비교

### 좋아요 (서로 다른 사용자 100명 → 같은 글)

| 방식 | 성공 | 데드락 | 최종 카운터 | 실제 행 수 | 카운터 불일치 | 처리 시간(ms) |
|---|---|---|---|---|---|---|
| (참고) 1단계: 현재 코드 그대로 (엔티티 read-modify-write) | 17 / 17.6 / 18 | 82 / 82.4 / 83 | 8 / 8.4 / 9 | 17 / 17.6 / 18 | 8 / 9.2 / 10 | 측정 안 함 |
| 기준선 (원자적 UPDATE만, INSERT→UPDATE 순서 유지) | 14 / 14.8 / 16 | 84 / 85.2 / 86 | 14 / 14.8 / 16 | 14 / 14.8 / 16 | 0 / 0.0 / 0 | 35 / 39.4 / 44 |
| **A. 락 순서 변경** (UPDATE → INSERT) | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 100 / 100.0 / 100 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 106 / 122.0 / 131 |
| B. post_stats 분리 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 100 / 100.0 / 100 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 87 / 99.0 / 115 |
| C. Redis INCR + 주기 반영 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 100 / 100.0 / 100 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 29 / 33.4 / 40 |

### 댓글 (서로 다른 사용자 100명 → 같은 글)

| 방식 | 성공 | 데드락 | 최종 카운터 | 실제 행 수 | 카운터 불일치 | 처리 시간(ms) |
|---|---|---|---|---|---|---|
| (참고) 1단계: 현재 코드 그대로 (엔티티 read-modify-write) | 13 / 13.8 / 15 | 85 / 86.2 / 87 | 7 / 7.4 / 8 | 13 / 13.8 / 15 | 6 / 6.4 / 7 | 측정 안 함 |
| 기준선 (원자적 UPDATE만, INSERT→UPDATE 순서 유지) | 12 / 12.4 / 13 | 87 / 87.6 / 88 | 12 / 12.4 / 13 | 12 / 12.4 / 13 | 0 / 0.0 / 0 | 28 / 36.2 / 55 |
| **A. 락 순서 변경** (UPDATE → INSERT) | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 100 / 100.0 / 100 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 108 / 112.4 / 117 |
| B. post_stats 분리 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 100 / 100.0 / 100 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 95 / 109.2 / 134 |
| C. Redis INCR + 주기 반영 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 100 / 100.0 / 100 | 100 / 100.0 / 100 | 0 / 0.0 / 0 | 28 / 29.6 / 31 |

- C 의 "최종 카운터"는 Redis → DB 반영(flush) **후** 값이다. 반영 **전**에는 DB 값이 0 이고 Redis 버퍼가 100 이었다 (아래 4절).
- 기준선의 처리 시간(약 36~39ms)은 대부분의 요청이 데드락으로 **즉시 실패**한 결과라 빠른 것이 아니다. 성공 100건 기준 비교는 A/B/C 끼리만 의미가 있다.
- 비교 하네스는 JdbcTemplate 으로 SQL 을 통일한 테스트 전용 구현(`CounterVariants`)이라 JPA 오버헤드가 없다. JPA 로 구현한 실제 코드는 A 방식이 좋아요 평균 148ms·댓글 평균 131ms, 최종 선택한 B 방식이 좋아요 평균 101ms·댓글 평균 92ms 였다 (5절).

### 같은 사용자 동시 중복 좋아요 (10건)

| 방식 | 성공 | ALREADY(정상 거절) | 데드락(=500) | 기타 예외 | post_like 행 수 | 카운터 |
|---|---|---|---|---|---|---|
| 기준선 (원자적 UPDATE만, INSERT→UPDATE 순서 유지) | 1 / 1.0 / 1 | 0 / 0.2 / 1 | 8 / 8.8 / 9 | 0 / 0.0 / 0 | 1 / 1.0 / 1 | 1 / 1.0 / 1 |
| **A. 락 순서 변경** (UPDATE → INSERT) | 1 / 1.0 / 1 | 9 / 9.0 / 9 | 0 / 0.0 / 0 | 0 / 0.0 / 0 | 1 / 1.0 / 1 | 1 / 1.0 / 1 |
| B. post_stats 분리 | 1 / 1.0 / 1 | 9 / 9.0 / 9 | 0 / 0.0 / 0 | 0 / 0.0 / 0 | 1 / 1.0 / 1 | 1 / 1.0 / 1 |
| C. Redis INCR + 주기 반영 | 1 / 1.0 / 1 | 9 / 9.0 / 9 | 0 / 0.0 / 0 | 0 / 0.0 / 0 | 1 / 1.0 / 1 | 1 / 1.0 / 1 |

- 기준선: 1건만 성공하고 나머지 약 9건은 `ALREADY_LIKED` 가 아니라 **데드락(=500)** 이다.
- A/B/C: 1건 성공 + 9건 정상 거절. 카운터는 1.

## 4. C (Redis INCR) 의 DB 반영 전후와 유실 범위

| 시점 | 좋아요 | 댓글 |
|---|---|---|
| DB 반영 전, DB 컬럼 값 | 0 | 0 |
| DB 반영 전, 조회용 합산값(DB + Redis 버퍼) | 100 | 100 |
| DB 반영 후 | 100 | 100 |

처리 시간 ≈ 30ms 로 가장 빠르다(INSERT 만 DB 에서 수행). 그러나 유실 가능 범위가 있다.

- **반영 주기 내 유실**: Redis 에만 있는 증가분은 flush 전에 Redis 가 죽고 RDB/AOF 가 없으면 사라진다. 최대 유실 = 스케줄러 주기 동안의 모든 좋아요·댓글 증가분. 행(`post_like`, `comment`)은 남으므로 **카운터와 실제 행 수가 영구히 어긋난다.**
- **flush 중 장애**: 기존 조회수 버퍼와 같은 `rename → DB 반영 → delete` 패턴이라, DB 반영 실패 시 processing 키를 되살리는 코드가 없어 증가분이 사라지고, DB 커밋 후 `delete` 전에 죽으면 재처리 시 **이중 반영**될 수 있다.
- 감소(unlike/댓글 삭제)는 `0 미만 방지`와 `버퍼 음수` 처리가 추가로 필요하고, 모든 조회에 DB + Redis 합산이 들어간다.

## 5. 최종 선택: B (카운터 테이블 분리, `post_stats`)

> 이전 판단(A)은 "변경 범위가 작다"가 주된 근거였고, B 의 **조회 비용**과 **게시글 수정과의 쓰기 경합**을 측정하지 않은 상태였다. 아래 추가 측정으로 다시 골랐다.

### 5-1. 추가 측정 조건

- 게시글 10,000건 / 사용자 300명 / 게시글당 좋아요 0~20개(총 약 10만) / 댓글 0~5개 / 프로필 이미지 보유 / FULLTEXT(ngram) 인덱스 포함
- 서비스 메서드(`PostService.getPosts/getPost/searchPosts`)를 단일 스레드로 호출. 케이스당 워밍업 30회 후 100회 측정, 이를 3라운드 반복
- A: 카운터가 `post` 행에 있는 코드(직전 상태), B: `post_stats` 로 분리한 코드. 같은 테스트 코드를 두 상태에서 각각 실행
- 데이터는 동일한 시드(`Random(42)`)로 생성

### 5-2. 조회 응답 시간 (p50 / p95, ms)

| 조회 | A p50 (r1 / r2 / r3) | B p50 (r1 / r2 / r3) | r2~r3 평균 A → B (p50) | A p95 (r1 / r2 / r3) | B p95 (r1 / r2 / r3) |
|---|---|---|---|---|---|
| 목록 size=10 (최신 페이지) | 5.52 / 3.63 / 3.65 | 5.31 / 4.34 / 3.55 | 3.64 → 3.94 ms (+8.4%) | 7.13 / 4.09 / 3.86 | 8.43 / 12.97 / 3.81 |
| 목록 size=50 | 17.1 / 15.29 / 15.59 | 17.54 / 15.85 / 14.89 | 15.44 → 15.37 ms (-0.5%) | 23.17 / 17.99 / 21.57 | 24.85 / 18.28 / 16.13 |
| 목록 size=10 (커서 위치 5,000번째, 깊은 페이지) | 3.62 / 3.48 / 3.33 | 3.61 / 3.58 / 3.39 | 3.41 → 3.49 ms (+2.3%) | 4.09 / 4.02 / 3.69 | 4.79 / 4.66 / 3.57 |
| 상세 조회 | 1.7 / 1.25 / 1.19 | 1.46 / 1.36 / 1.23 | 1.22 → 1.29 ms (+6.1%) | 2.08 / 1.33 / 1.42 | 1.79 / 1.95 / 1.32 |
| 검색 size=10 (FULLTEXT) | 10.39 / 9.49 / 9.95 | 10.3 / 9.74 / 9.37 | 9.72 → 9.55 ms (-1.7%) | 13.51 / 9.93 / 11.17 | 14.17 / 13.21 / 10.73 |

- 1라운드(r1)에는 JIT 워밍업 잔여 효과가 남아 이후 라운드보다 느리다. 요약 비교는 r2~r3 평균 p50 로 했다.
- B 는 최종 코드(`LEFT JOIN FETCH` / `LEFT JOIN`) 기준으로 **재측정**한 값이다. (같은 조건에서 inner JOIN 으로 먼저 측정했을 때도 결과는 비슷했다: 목록 10건 p50 3.77/3.65ms, 상세 1.25/1.31ms.)
- r2~r3 평균 p50 차이는 목록 10건 +0.30ms(+8%), 상세 +0.08ms(+6%), 깊은 페이지 +0.08ms(+2%), 목록 50건 −0.07ms, 검색 −0.17ms 로 **방향이 일정하지 않고 절대값은 0.3ms 이하**다.
- 목록 10건의 +8% 는 B 의 r2 한 라운드(4.34ms, p95 12.97ms)가 튄 영향이 크다. 같은 B 의 r3 은 3.55ms 로 A(3.63~3.65ms)보다 낮고, inner JOIN 으로 측정한 B 도 3.77/3.65ms 였다. **B 라운드 간 흔들림(3.55~4.34ms)이 A-B 차이보다 크다**는 점에서 이번 측정으로는 "조회 비용이 늘었다"고 단정할 수 없고, 반대로 "차이가 전혀 없다"고도 말할 수 없다. 결론은 **"측정 가능한 증가는 있어도 오차 범위 수준(≤0.3ms)"** 이다.
- p95 는 100회 표본에서 꼬리값 1~2개에 좌우되어(예: B 목록 r2 p95 12.97ms 단발 튐) 판단 근거로 쓰기엔 불안정하다.
- 조회 시간은 게시글마다 나가는 N+1(작성자·프로필 이미지·Redis 조회수)이 대부분을 차지해서, JOIN 하나가 늘어나는 비용은 그 안에 묻힌다. 이 N+1 은 이번 개선 범위 밖이며 그대로다.

### 5-3. 실행 계획 (EXPLAIN)

목록 쿼리 (`post_id < 5000` 커서, `LIMIT 11`):

| | 접근 방식 |
|---|---|
| A | `post` PRIMARY **역방향 인덱스 range scan** → LIMIT |
| B | 위와 동일 + `post_stats` **`eq_ref` PRIMARY (`ref=community.p.post_id`, rows=1)** 단건 조회 (Nested loop) |

- 최종 코드의 `LEFT JOIN` 도 실행 계획은 같고 조인 방식만 `Nested loop left join` 으로 바뀐다. B 의 JOIN 은 풀 스캔 없이 PK 로 행마다 1회 조회한다. 목록은 `LIMIT` 이 11건이라 JOIN 대상도 최대 11건이다.
- 검색(FULLTEXT) 쿼리도 `post_stats` 가 `eq_ref` PRIMARY 로 붙는다. 풀텍스트 인덱스 사용 방식은 A 와 같다.
- 원문: A, B(LEFT JOIN) 두 모드의 `EXPLAIN` 과 `EXPLAIN FORMAT=TREE` (`ReadCostBenchmarkTest` 가 `build/explain-{A,B}.txt` 로 저장)

### 5-4. 쓰기 경합: 같은 게시글에 좋아요 100건 + 게시글 수정 10건 동시

(커넥션 풀 대기가 락 대기를 가리지 않도록 이 테스트만 풀을 120으로 키움)

| 지표 (5회, 최소 / 평균 / 최대) | A (post 행 카운터) | B (post_stats 분리) |
|---|---|---|
| 수정 요청 p50 소요(ms) — 락 대기 포함 | 145.14 / 184.2 / 273.91 | 94.88 / 109.8 / 123.34 |
| 수정 요청 최대 소요(ms) | 148.68 / 187.5 / 278.33 | 97.31 / 112.1 / 125.91 |
| 수정 요청 실패 | 0 / 0.0 / 0 | 0 / 0.0 / 0 |
| 좋아요 p50(ms) | 80.65 / 107.2 / 176.41 | 53.22 / 60.7 / 69.5 |
| 좋아요 p95(ms) | 134.24 / 170.8 / 257.73 | 87.88 / 103.0 / 115.86 |
| 좋아요 실패 | 0 / 0.0 / 0 | 0 / 0.0 / 0 |
| 최종 like_count (기대 100) | 100 / 100.0 / 100 | 100 / 100.0 / 100 |
| 전체 처리 시간(ms) | 152.45 / 192.8 / 282.95 | 98.68 / 114.2 / 130.14 |
| (참고) 1회차 제외 4회 평균: 수정 p50 / 전체 | 161.8 / 170.2 | 106.4 / 110.2 |

- 수정 요청은 A, B 모두 **실패 0건**이고 좋아요도 전부 성공했다.
- B 가 수정 요청 대기 시간을 약 **34% 줄였다** (1회차 제외 평균 약 162ms → 약 106ms). 좋아요 쪽도 빨라졌다.
- 다만 **"완전히 분리됐다"는 아니다.** B 에서도 수정 요청 시간이 전체 처리 시간과 거의 같다. 좋아요 INSERT 의 FK 검사가 `post` 행에 공유 락을 잡고 있어서, 수정의 배타 락이 진행 중인 좋아요 트랜잭션이 끝나기를 기다리기 때문이다. B 는 "카운터 UPDATE 의 배타 락이 post 행에 걸리지 않는다"는 것까지만 달성했고, 그만큼 대기 구간이 짧아졌다.
- 이 수치에는 위 5-2 의 노이즈 수준(수십 ms 단위 변동)보다 큰 차이가 있지만, 1회차는 워밍업의 영향이 커 보여 1회차 제외 평균도 함께 적었다.

### 5-5. 판단

사전에 정한 기준 — "B 의 조회 비용 증가가 오차 범위 수준이면 B 채택" — 에 따라 **B 를 채택**한다.

| 기준 | A | B |
|---|---|---|
| 정합성 | 카운터 = 행 수 | 동일 |
| 데드락 | 락 **순서 규칙**(UPDATE → INSERT)을 지켜야 방지됨. 새 코드가 순서를 어기면 재발 | **구조적으로 없음**: INSERT 는 post 공유 락, 카운터 UPDATE 는 다른 행의 배타 락이라 S→X 승격이 생기지 않음 |
| 조회 비용 | 기준 | p50 차이 ≤0.3ms (오차 범위 수준, 5-2 참고), PK `eq_ref` JOIN |
| 수정과의 경합 | 모든 좋아요가 post 행 배타 락 → 수정이 줄 서서 대기 | 수정 대기 약 34% 감소(완전 분리는 아님) |
| 좋아요 100건 처리 | 평균 약 148ms (JPA 최종 코드 기준) | 평균 약 101ms |
| 구조 복잡도 | 낮음 | 높음: 테이블·엔티티·LEFT JOIN FETCH·마이그레이션. 카운터 행이 누락되면 읽기는 0 으로 보이지만 좋아요/댓글 쓰기는 예외 |

- B 는 복잡도를 감수하는 대신 **데드락을 코드 규칙이 아니라 스키마로 막는다**는 점이 결정적이다. A 는 모든 카운터 갱신 코드가 락 순서를 기억해야 한다.
- 조회 비용이 의미 있게 늘었다면 A 를 유지하고 락 순서를 한 메서드에 모아 동시성 테스트로 회귀를 막는 방향이었으나, 측정은 그 조건에 해당하지 않았다.
- B 의 대가: 인기 글 한 건에 좋아요가 몰리면 `post_stats` 한 행에 UPDATE 가 직렬화된다는 점은 A 와 같다(카운터 행 락). B 는 이 경합이 `post` 행 다른 쓰기와 섞이지 않을 뿐이다.

**프로덕션 코드에 적용한 것 (B)**

| 변경 | 위치 |
|---|---|
| `post_stats` 엔티티 신규 (PK = post_id, like_count, comment_count) | `PostStats` |
| 원자적 증감 쿼리와 카운터 행 생성 | `PostStatsRepository` |
| `Post` 에서 카운터 컬럼·`increase/decrease*Count` 제거, `stats` 를 PK 공유 읽기 전용 지연 연관으로 매핑(`post → post_stats` 방향 FK 는 생성하지 않음) | `Post` |
| 목록/상세/수정 조회는 `LEFT JOIN FETCH p.stats`, 검색 native 쿼리는 `LEFT JOIN post_stats` + `COALESCE` (카운터 행이 없어도 게시글이 사라지지 않음) | `PostRepository` |
| 게시글 생성 시 카운터 행 `(post_id, 0, 0)` 생성 | `PostService.createPost` |
| `like`: 중복·사용자·게시글 확인 → `post_like` INSERT → `post_stats` UPDATE. unique 위반은 `ALREADY_LIKED`(409) | `PostLikeService.like` |
| `unlike`/`deleteComment`: DELETE 영향 행이 1일 때만 카운터 감소 | `PostLikeService.unlike`, `CommentService.deleteComment` |
| `createComment`: 게시글 확인 → INSERT → `post_stats` UPDATE | `CommentService.createComment` |
| `@DynamicUpdate` 유지: 게시글 수정이 `view_count` 를 오래된 값으로 덮어쓰지 않도록 | `Post` |

- 부수 효과: 삭제된 게시글에 좋아요·댓글을 달 수 없다(`POST_NOT_FOUND`).
- `@DynamicUpdate` 는 결정적 테스트 `titleUpdateDoesNotOverwriteViewCount` 로 효과를 확인했다. 조회수 스케줄러와의 실제 경합과 다중 파드의 `view_count` 경합은 여전히 측정하지 않았다.
- 카운터 행이 없는 게시글(마이그레이션 누락 등): 읽기는 `LEFT JOIN` 이라 **목록·상세·검색에서 사라지지 않고 카운터 0 으로 표시**된다. 반대로 쓰기(좋아요·댓글)는 카운터 UPDATE 가 0건이면 `IllegalStateException` 으로 **조용히 넘어가지 않고 실패하며 INSERT 도 롤백**된다. 두 동작 모두 `PostStatsIntegrityTest` 로 검증했다. 그래도 마이그레이션 검증 쿼리는 필요하다(카운터가 0 으로 잘못 보이는 것을 막기 위해).
- 게시글 생성 시 `post_stats` 행은 같은 트랜잭션에서 만들어진다. 바깥 트랜잭션을 롤백하면 `post` 와 `post_stats` 가 함께 사라지는 것을 `PostStatsIntegrityTest` 로 확인했다.

**수정 후 검증 (`ConcurrencyAfterTest`, B 코드에서 5회 반복 포함 25개 테스트 전부 통과)**

| 시나리오 | 결과 |
|---|---|
| 좋아요 100건 동시 | 성공 100, 데드락 0, `like_count` = 100 = 행 수 (처리 시간 91 / 101.0 / 112 ms) |
| 댓글 100건 동시 | 성공 100, 데드락 0, `comment_count` = 100 = 행 수 (처리 시간 84 / 91.8 / 101 ms) |
| 같은 사용자 좋아요 10건 동시 | 성공 1, `ALREADY_LIKED` 9, 500 0건, 카운터 1 |
| 좋아요 100건 동시 취소 | 전부 성공, 카운터 0, 행 0 |
| 같은 사용자 취소 10건 동시 | 성공 1, `LIKE_NOT_FOUND` 9, 다른 사용자의 좋아요 카운터는 유지 |
| 댓글 100건 동시 삭제 | 전부 성공, 카운터 0 |
| 같은 댓글 10건 동시 삭제 | 성공 1, `COMMENT_NOT_FOUND` 9, 카운터 1만 감소 |
| 삭제된 글에 좋아요/댓글 | 거절 |
| 오래된 엔티티로 제목 수정 | 그 사이 올라간 `view_count` 보존 |
| 게시글 생성 (`PostStatsIntegrityTest`) | `post_stats` 행 `(0, 0)` 이 같은 트랜잭션에서 생성됨. 롤백 시 함께 제거 |
| 카운터 행 누락 게시글 읽기 | 목록·상세·검색 모두 카운터 0 으로 정상 조회 (inner join 으로 바꾸면 실패함을 확인) |
| 카운터 행 누락 게시글에 좋아요 | `IllegalStateException`, `post_like` INSERT 롤백 |

## 6. 구현하지 않고 제외한 방식

- **낙관적 락(`@Version`)**: 충돌 시 재시도가 필요하고, 데드락의 원인(INSERT→UPDATE 락 순서)은 그대로라 해결되지 않는다.
- **FK 제거**: 데드락은 사라지지만 참조 무결성을 DB 에서 포기하게 된다.
- **카운터 없이 COUNT 조회**: 쓰기 경합은 없어지나 목록 조회마다 게시글 수만큼 COUNT 가 필요해 읽기 비용이 커진다.
- **데드락 재시도**: 동시 100건에서 82% 가 실패하는 상황에서 재시도는 같은 충돌을 반복할 뿐이라 증상 완화에 그치고 원인은 남는다.

## 7. 측정의 한계

- **모든 측정은 로컬 Testcontainers(Docker 위 단일 MySQL 8.0, 단일 JVM) 기준**이다. 운영 환경(네트워크 지연, 버퍼 풀 크기, 동시 접속자 수, 디스크)과는 절대값이 다르다. 상대 비교와 데드락 유무 같은 구조적 결과만 근거로 삼는다.
- 조회 측정은 단일 스레드 순차 호출이라 읽기 동시 부하에서의 차이는 보지 못했다. 데이터 10,000건은 InnoDB 버퍼 풀에 모두 올라가는 크기라 디스크 I/O 가 섞인 상황은 측정하지 않았다.
- A 와 B 는 서로 다른 JVM 실행에서 측정했다(같은 JVM 에서 번갈아 측정하지 않음). 라운드 간 변동이 있어 r1 은 제외 요약했다.
- 쓰기 경합은 5회 표본이다.
- 서비스 메서드 직접 호출(HTTP·JWT 필터 제외), 5회 반복이라 처리 시간 차이는 오차 범위일 수 있다.
- 비교 하네스(JdbcTemplate)와 최종 적용 코드(JPA)의 시간은 직접 비교할 수 없다.
- 데드락 건수는 예외(`CannotAcquireLockException`) 기준이다.

## 부록. 마이그레이션 (B 적용용)

`ddl-auto` 에 맡기지 않고 SQL 로 한다. 앱을 먼저 배포하면 `ddl-auto: update` 설정에서는 Hibernate 가 FK 없는 빈 `post_stats` 를 만들 수 있으므로 **SQL 을 먼저** 실행한다. (운영 프로파일 설정은 열어 보지 않아 확인하지 못했다.)

**1) 사전 작업 (무중단)**

```sql
CREATE TABLE post_stats (
  post_id       BIGINT NOT NULL PRIMARY KEY,
  like_count    INT    NOT NULL DEFAULT 0,
  comment_count INT    NOT NULL DEFAULT 0,
  CONSTRAINT fk_post_stats_post FOREIGN KEY (post_id) REFERENCES post (post_id)
);

-- 1차 백필
INSERT INTO post_stats (post_id, like_count, comment_count)
SELECT post_id, like_count, comment_count FROM post;
```

**2) 짧은 점검 시간: 쓰기 중단 → 증분 동기화 → 검증 → 신버전 배포**

```sql
-- (구 버전 앱이 1차 백필 이후에도 post 의 카운터를 바꿨으므로) 쓰기를 멈춘 뒤 다시 맞춘다
INSERT INTO post_stats (post_id, like_count, comment_count)
SELECT p.post_id, p.like_count, p.comment_count FROM post p
WHERE NOT EXISTS (SELECT 1 FROM post_stats s WHERE s.post_id = p.post_id);

UPDATE post_stats s JOIN post p ON p.post_id = s.post_id
SET s.like_count = p.like_count, s.comment_count = p.comment_count;

-- 신버전은 post 의 카운터 컬럼을 채우지 않으므로, INSERT 가 실패하지 않게 기본값을 준다
ALTER TABLE post
  MODIFY like_count    INT NOT NULL DEFAULT 0,
  MODIFY comment_count INT NOT NULL DEFAULT 0;
```

**3) 검증 (모두 0 이어야 신버전 배포)**

```sql
-- 카운터 행이 없는 게시글: 읽기에서는 카운터 0 으로 보이고, 좋아요/댓글 쓰기는 예외로 실패한다
SELECT COUNT(*) FROM post p LEFT JOIN post_stats s ON s.post_id = p.post_id WHERE s.post_id IS NULL;

-- 이관 값 불일치
SELECT COUNT(*) FROM post p JOIN post_stats s ON s.post_id = p.post_id
 WHERE p.like_count <> s.like_count OR p.comment_count <> s.comment_count;

-- 실제 행 수와 카운터 불일치 (갱신 손실이 남긴 과거 오차를 이 기회에 바로잡을 수도 있다)
SELECT COUNT(*) FROM post_stats s
 WHERE s.like_count    <> (SELECT COUNT(*) FROM post_like l WHERE l.post_id = s.post_id)
    OR s.comment_count <> (SELECT COUNT(*) FROM comment  c WHERE c.post_id = s.post_id);
```

- 세 번째 쿼리가 0 이 아니면, 과거 갱신 손실로 카운터가 이미 어긋나 있던 것이다. 필요하면 아래로 재계산한다.

```sql
UPDATE post_stats s SET
  like_count    = (SELECT COUNT(*) FROM post_like l WHERE l.post_id = s.post_id),
  comment_count = (SELECT COUNT(*) FROM comment  c WHERE c.post_id = s.post_id);
```

**4) 롤백 (구 버전으로 되돌릴 때)**: `post` 의 카운터 컬럼은 일정 기간 남겨 두었으므로, 되돌리기 전에 역방향으로 값을 맞춘다.

```sql
UPDATE post p JOIN post_stats s ON s.post_id = p.post_id
SET p.like_count = s.like_count, p.comment_count = s.comment_count;
```

**5) 안정화 후 정리**

```sql
ALTER TABLE post DROP COLUMN like_count, DROP COLUMN comment_count;
```

- 순서 요약: 테이블 생성·1차 백필 → (점검) 쓰기 중단·증분 동기화·기본값 부여 → 검증 → 신버전 배포 → 안정화 후 컬럼 제거.
- 무중단이 필요하면 이중 쓰기 기간을 두는 방법이 있으나, 이 서비스 규모에서는 짧은 점검이 더 단순하고 안전하다고 판단했다.
- **SQL 정리만 했고, 실행 검증은 하지 않았다.** 이번 작업은 배포하지 않으며, 테스트는 스키마를 Hibernate 가 생성하고 FK 만 SQL 로 추가한 환경이라 위 SQL 은 문법과 순서를 정리한 것이다. 운영 적용 전에 스테이징 복제본에서 실행·검증이 필요하다.
