# 커뮤니티 REST API

## 기술 스택
- Java 26
- Spring Boot 4.0.6
- 메모리 저장소 (HashMap)

## 패키지 구조
```
com.example.community
├── global
│   ├── exception
│   │   ├── GeneralException
│   │   └── GlobalExceptionHandler
│   └── response
│       ├── CommonResponse
│       ├── ErrorResponse
│       └── StatusCode
└── post
    ├── controller
    │   └── PostController
    ├── service
    │   └── PostService
    ├── repository
    │   ├── PostRepository
    │   └── InMemoryPostRepository
    ├── model
    │   └── Post
    └── dto
        ├── request
        │   ├── PostCreateRequest
        │   └── PostUpdateRequest
        └── response
            └── PostResponse
```

## 설계 고려사항

### 다형성을 활용한 Repository 설계
PostRepository 인터페이스와 InMemoryPostRepository 구현체를 분리했다.
현재는 DB 없이 Map 기반 메모리 저장소를 사용하지만,
Service는 인터페이스만 바라보기 때문에 나중에 JPA를 도입할 때
구현체만 교체하면 된다.

### 공통 응답 형식
모든 응답을 CommonResponse<T>로 통일한 이유는
프론트엔드가 성공/실패 여부와 관계없이 항상 같은 형태로 응답을 처리할 수 있도록 하기 위해서다.

### 예외 처리 중앙화
GlobalExceptionHandler를 사용한 이유는 예외 처리 로직을 한 곳에서 관리하기 위해서다.
각 Controller나 Service에서 예외를 직접 처리하면 중복 코드가 생기고
응답 형식이 달라질 수 있다.

### 입력값 검증
@Valid를 Controller에서 처리한 이유는 Service에 잘못된 값이 들어오기 전에
미리 차단하기 위해서다. 제목 최대 26자 제한은 기획서 요구사항을 반영한 것이며,
필수값 누락(제목, 내용)과 형식 오류를 각각 다른 메시지로 반환하여
프론트엔드가 오류 원인을 파악할 수 있도록 설계했다.

## API 명세
| Method | URL | 설명 |
|--------|-----|------|
| POST | /posts | 게시글 추가 |
| GET | /posts | 게시글 목록 조회 |
| PATCH | /posts/{postId} | 게시글 수정 |
| DELETE | /posts/{postId} | 게시글 삭제 |