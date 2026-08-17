# 🎙️openSquare

## Back-end 소개

- 러닝 커뮤니티를 주제로 `서로 소통하는 커뮤니티` 프로젝트입니다.
- `Spring Boot`로 서버를 구현하고, `MySQL`로 db를 사용했습니다.
- 개발은 초기 프로젝트 설정부터, ERD 설계 및 DB 연결, JWT 인증, REST API 설계, 프론트엔드 연동까지 구현했습니다.
- 계층형 아키텍처(Controller-Service-Repository) 기반으로 구현했습니다.

### 개발 인원 및 기간

- 개발기간 :  2026-05-31 ~ 2026-08-09
- 개발 인원 : 프론트엔드/백엔드 1명 (본인)

### 사용 기술 및 tools
**Backend**
- Spring Boot 4.0.6
- Spring Data JPA
- Spring Security (JWT 기반 인증)
- Gradle

**Database**
- MySQL 8.0
- Redis

**Infra / DevOps**
- AWS (EC2, S3, VPC, Route53 등)
- Docker
- Kubernetes (kubeadm)
- Helm
- ArgoCD (GitOps)
- GitHub Actions (CI/CD)

**Monitoring / Logging**
- Prometheus / Grafana
- Loki / Promtail



### Front-end
- <a href="https://github.com/100-hours-a-week/4-mond-community-FE">Front-end Github</a>

### 서비스 시연 영상
- https://youtu.be/ElOrnk6t738?si=hC_jWh-UM60Bkh4Y

### 📁 폴더 구조

<details>
  <summary><b>폴더 구조 보기/숨기기 (클릭)</b></summary>
  <div markdown="1">

```text
├── .github/
│   ├── ISSUE_TEMPLATE/
│   └── workflows/
│       └── deploy.yaml
├── .gradle/
├── .idea/
├── build/
├── gradle/
├── helm/
│   ├── argocd/
│   │   ├── kube-prometheus-stack-application.yaml
│   │   ├── loki-application.yaml
│   │   ├── mondcommunity-app.yaml
│   │   └── promtail-application.yaml
│   └── mondcomunity_chart/
│       ├── templates/
│       │   ├── config.yaml
│       │   ├── deployment.yaml
│       │   ├── ingress.yaml
│       │   ├── rbac.yaml
│       │   ├── redis.yaml
│       │   ├── sealed-secret.yaml
│       │   ├── service.yaml
│       │   └── servicemonitor.yaml
│       ├── Chart.yaml
│       └── values.yaml
├── root-app.yaml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── community/
│   │   │               ├── auth/
│   │   │               ├── comment/
│   │   │               ├── global/
│   │   │               ├── image/
│   │   │               ├── post/
│   │   │               ├── postlike/
│   │   │               ├── postview/
│   │   │               ├── refreshtoken/
│   │   │               ├── user/
│   │   │               └── KtbWeekly2Application.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-test.yaml
│   └── test/
├── uploads/
├── .env
├── .gitattributes
├── .gitignore
├── build.gradle
├── deploy.sh
├── docker-compose.yml
├── Dockerfile
├── gradlew
├── gradlew.bat
└── KTB_WEEKLY2.iml
```
  </div>
</details> 

<br/>

## 서버 설계
### 서버 구조

| 도메인 | Controller | Service | Repository |
| --- | --- | --- | --- |
| **인증 (Auth)** | `AuthController` | `AuthService` | `UserRepository` |
| **유저 (User)** | `UserController` | `UserService` | `UserRepository` |
| **게시글 (Post)** | `PostController` | `PostService` | `PostRepository` |
| **댓글 (Comment)** | `CommentController` | `CommentService` | `CommentRepository` |
| **좋아요 (Like)** | `PostLikeController` | `PostLikeService` | `PostLikeRepository` |
| **게시글 이미지** | `PostImageController` | `S3Service` | `PostImageRepository` |
| **프로필 이미지** | `ProfileImageController` | `S3Service` | `ProfileImageRepository` |

### 구현 기능

#### Users
```
- 회원가입, 로그인, 비밀번호 변경 기능 구현
- 비밀번호는 BCrypt로 암호화하여 저장
- JWT 기반 인증 처리 (Access Token + Refresh Token 발급 및 로테이션)
- Refresh Token을 이용한 Access Token 재발급 엔드포인트 구현
- 커스텀 인증 필터를 통해 유효한 토큰을 가진 요청만 처리
- 로그아웃 시 클라이언트 토큰 무효화 처리
- 프로필 이미지는 S3 Presigned URL 방식으로 업로드, DB에는 이미지 URL만 저장
- 확장자 화이트리스트 검증으로 업로드 파일 형식 제한
```

#### Posts
```
- 게시글 CRUD 기능 구현
- 커서 기반 페이지네이션으로 목록 조회 성능 확보
- 조회수는 Redis로 구현
- 게시글 이미지는 S3 Presigned URL 방식으로 업로드, nullable post_id로 임시 저장 후 게시글 생성 시 연결
- 인증 필터를 통해 유효한 토큰을 가진 유저 요청만 처리
```

#### Comments
```
- 댓글 CRUD 기능 구현
- 인증 필터를 통해 유효한 토큰을 가진 유저 요청만 처리
```
#### Likes
```
- 게시글 좋아요 기능 구현
- post_like를 별도 테이블로 분리하여 중복 좋아요 방지
```

<br/>

## 데이터베이스 설계
### 요구사항 분석
`유저 관리`
- 사용자는 이메일, 프로필 이미지, 비밀번호, 닉네임 정보를 포함하는 유저 관리
- 각 유저는 고유한 식별자를 가지고 있으며, 이메일과 닉네임은 유니크하게 설정하여 중복 방지

`게시글 관리`
- 사용자가 제목, 내용, 이미지, 작성일시, 수정일시 등의 정보를 포함하는 게시글 관리
- 게시글은 작성자를 참조하여 관계를 설정

`댓글 관리`
- 사용자가 내용, 작성자, 작성일시 등의 정보를 포함하는 댓글 관리
- 댓글은 어떤 게시글에 속해 있는지 나타내는 참조 포함

`좋아요 관리`
- 사용자와 게시글 간의 좋아요 관계를 별도 테이블로 관리하여 중복 좋아요 방지

`인증 관리`
- JWT Access Token / Refresh Token 기반으로 사용자의 로그인 상태 관리
- Refresh Token 만료 시간, 로테이션 정보를 저장하여 토큰 재발급 추적

### 모델링
`E-R Diagram`  
요구사항을 기반으로 모델링한 E-R Diagram입니다.  
<br/>
<img src="img.png" width="60%" alt="E-R Diagram" />
<br/>


### 아키텍처
<img width="1348" height="1531" alt="Image" src="https://github.com/user-attachments/assets/65d15f1a-841d-457e-91a7-0eca7bf27eff" />

#### 플로우 설명

1. 사용자 → Route53 → CloudFront → S3 (정적 프론트엔드)
2. 사용자 → Route53 → ALB → NodePort(nginx-ingress) → Backend Pod
3. Backend Pod → MySQL EC2 (3306)
4. 브라우저가 Presigned URL로 S3에 직접 업로드 (Gateway Endpoint 경유)
5. 워커/마스터 → NAT Instance → IGW → ECR 
6. GitHub Actions(CI) → ECR 이미지 Push
7. ArgoCD가 클러스터 내부에서 GitHub 레포를 Pull하여 동기화 


## 트러블 슈팅

### 조회수 동시성 처리 이슈

- 문제: 동시에 여러 요청이 몰릴 때 조회수 카운트가 부정확하게 집계
- 원인: 단순 +1 업데이트 쿼리가 동시성 상황에서 레이스 컨디션 발생
- 고민:
    - 1차로 로컬 메모리(`ConcurrentHashMap`)로 버퍼링해 스레드 세이프한 원자적 연산은 확보했지만, 서버 재시작/장애 시 아직 flush 안 된 데이터가 유실되는 문제가 남아있었음
    - 여기에 Backend Pod를 replica 2개로 운영하기 시작하면서 문제가 하나 더 생김: `ConcurrentHashMap`은 JVM(파드)별로 독립된 메모리라, 같은 게시글 조회 요청이 파드1과 파드2에 번갈아 분산되면 조회수가 파드마다 따로 쌓여 집계가 쪼개지는 문제가 발생 (예: postId=33 조회수가 파드1엔 30, 파드2엔 20으로 분리되어 DB flush 시 정합성이 깨짐)
    - 즉 Redis 도입은 "동시성 자체"보다는 **① 영속성 확보, ② 멀티 파드 환경에서 여러 인스턴스가 공유하는 단일 카운트 저장소 필요**, 이 두 가지가 실질적인 목적
    - Redis 자료구조 선택: postId마다 별도 키(`view:33`, `view:34`...)를 만드는 방식도 가능했지만, 게시글 수만큼 키가 늘어나는 키 스프롤(key sprawl) 문제와 TTL/메모리 관리가 번거로워짐. 대신 하나의 Hash 키(`view_buffer`) 안에 postId를 필드로 묶어 관리하는 방식을 선택. `HINCRBY`는 하나의 Hash 안에서도 필드 단위로 원자적 증가가 보장되고, 주기적으로 `HGETALL` 한 번으로 전체 버퍼를 배치 조회해 DB에 flush하기도 용이해서 채택
- 해결: Redis Hash(`HINCRBY`) 기반 원자적 연산으로 카운트 처리, `post_view` 테이블로 유저별 중복 조회 방지
- 테스트: JMeter 5.6.3로 `GET /posts/{postId}` 대상 50 threads × 100 loops(총 5,000건)를 before(ConcurrentHashMap) / after(Redis) 각 3회 반복 측정

  | 항목 | before (ConcurrentHashMap) | after (Redis HINCRBY) | 차이 |
    | --- | --- | --- | --- |
  | Throughput (평균) | 978.5/s | 978.2/s | 거의 동일 (-0.3/s) |
  | Average 응답시간 | 1.7ms | 1.3ms | 거의 동일 |
  | Min 응답시간 | 0ms | 1ms | 무시 가능한 차이 |
  | Error율 | 0% | 0% | 동일 |

    - **결론: 응답시간·처리량 자체에는 유의미한 성능 저하가 없었음.** Redis 도입의 실익은 성능이 아니라, 서버 재시작/장애 시 조회수 유실 방지와 replica 2개 이상 멀티 파드 환경에서의 카운트 정합성 확보에 있음

### 검색 기능 성능 이슈

- 문제: 게시글 제목/본문 검색 시 응답 속도 저하, 데이터 증가할수록 심화. 부가적으로 검색 결과 변환 과정에서 N+1 쿼리 발생
- 원인:
    - `LIKE '%keyword%'`처럼 키워드 앞에 `%`가 붙는 패턴은 B-Tree 인덱스를 타지 못해 Full Table Scan 발생
    - 공백 처리에도 취약해서 "러닝 크루"로 검색 시 "러닝크루 모집"(공백 없음)이 결과에서 누락됨
    - 검색 결과 각 게시글마다 작성자(`User`) Lazy Loading, 프로필 이미지 URL 조회, 조회수 버퍼 조회가 반복되며 N+1 쿼리 발생
- 해결:
    - MySQL FULLTEXT 인덱스(ngram parser) 적용으로 인덱스 기반 검색 전환
    - `JOIN FETCH p.user`로 작성자 정보를 한 번의 쿼리로 함께 조회해 N+1 제거
- 테스트: JMeter로 LIKE 검색(before) vs FULLTEXT 검색(after)을 키워드별 400건씩 3회 반복 비교

  | 키워드 | 매치율 | before (LIKE) | after (ngram FULLTEXT) | 결과 |
    | --- | --- | --- | --- | --- |
  | "러닝" | 33% (66,558건) | ~8ms | ~704ms | **LIKE가 88배 빠름** |
  | "안녕" | 0.006% (12건) | ~112ms | ~6.7ms | **FULLTEXT가 17배 빠름** |

    - **결론: FULLTEXT가 무조건 빠른 게 아니라, 매치율(결과 건수)에 따라 유불리가 갈림.** 매치율이 낮은(결과가 희소한) 키워드에서는 FULLTEXT가 압도적으로 유리하지만, 매치율이 높은 키워드는 오히려 인덱스 탐색 오버헤드 + 결과 정렬/스코어링 비용 때문에 LIKE보다 느려짐. 실제 서비스에서는 매치율이 낮은 검색(구체적인 키워드)이 대다수라고 판단해 FULLTEXT를 채택했지만, 이 트레이드오프는 인지하고 있어야 함

### VPC Endpoint Interface를 NAT Instance로 변경

- 문제: ECR 접근에 사용하던 VPC Endpoint Interface(ecr.api, ecr.dkr) 비용이 VPC 관련 비용에서 상당 부분을 차지
- 원인: Interface Endpoint는 AZ당 시간 과금 + 데이터 처리 비용이 별도로 붙는 구조라, 트래픽량 대비 고정비 비중이 컸음. 반면 ECR pull 트래픽은 대량이 아니라 NAT를 경유해도 충분히 감당 가능한 수준이었음
- 고민: NAT Instance를 ECR 전용으로 새로 띄우는 방법도 있었지만, 이미 private subnet 아웃바운드용으로 운영 중이던 NAT Instance가 있어서 굳이 리소스를 중복으로 둘 필요는 없다고 판단. 다만 기존 트래픽에 이미지 pull 트래픽까지 더해지면 대역폭 병목이 생길 수 있어 인스턴스 스펙(t3.micro)으로 감당 가능한지도 함께 고려
- 해결: 기존 NAT Instance를 재활용. 보안 그룹에 HTTPS(443) 인바운드 규칙 추가(source: 워커 노드 SG)만으로 라우팅 자체는 바로 적용, 이후 VPC Endpoint 삭제
- 테스트:
    - `curl -v https://<ecr-uri>` 로 응답 IP가 사설 IP(엔드포인트 경유)가 아닌 NAT의 퍼블릭 IP로 바뀌는지 확인
    - `kubectl run` 으로 실제 ECR 이미지 pull 테스트 파드 생성 → `Running` 진입까지 확인해 pull 경로 정상 동작 검증
    - 라우트 테이블의 `0.0.0.0/0` 대상이 NAT Instance ENI로 정상 연결되어 있는지 점검
<br/>


## 프로젝트 후기
프로젝트를 구현하면서 어떻게 설계해야할지를 고민할 수 있었던 프로젝트였다.



<br/>
<br/>
<br/>

<p align="center">
  <img src="https://github.com/100-hours-a-week/5-erica-react-fe/assets/81230764/d611b233-b596-4d1d-bbb9-dc2e4e41eb47" style="width:200px; margin: 0 auto"/>
</p>
