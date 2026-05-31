# 커뮤니티 REST API

## 기술 스택
- Java 26
- Spring Boot 4.0.6
- MySQL
- Spring Data JPA
  
## 패키지 구조
```
com.example.community
├── global
│   ├── config
│   │   ├── CorsConfig
│   │   └── WebConfig
│   ├── exception
│   │   ├── GeneralException
│   │   └── GlobalExceptionHandler
│   └── response
│       ├── CommonResponse
│       ├── ErrorResponse
│       └── StatusCode
├── auth
│   ├── controller
│   │   └── AuthController
│   ├── service
│   │   └── AuthService
│   ├── dto
│   │   ├── request
│   │   │   └── LoginRequest
│   │   └── response
│   │       ├── LoginResponse
│   │       └── LoginResult
│   └── jwt
│       ├── JwtProperties
│       ├── JwtProvider
│       └── JwtAuthenticationFilter
├── user
│   ├── controller
│   │   └── UserController
│   ├── service
│   │   └── UserService
│   ├── repository
│   │   └── UserRepository
│   ├── model
│   │   └── User
│   └── dto
│       ├── request
│       │   ├── SignUpRequest
│       │   ├── UpdateUserRequest
│       │   └── UpdatePasswordRequest
│       └── response
│           └── UserResponse
├── post
│   ├── controller
│   │   └── PostController
│   ├── service
│   │   └── PostService
│   ├── repository
│   │   └── PostRepository
│   ├── model
│   │   └── Post
│   └── dto
│       ├── request
│       │   ├── PostCreateRequest
│       │   └── PostUpdateRequest
│       └── response
│           └── PostResponse
├── comment
│   ├── controller
│   │   └── CommentController
│   ├── service
│   │   └── CommentService
│   ├── repository
│   │   └── CommentRepository
│   ├── model
│   │   └── Comment
│   └── dto
│       ├── request
│       │   ├── CommentCreateRequest
│       │   └── CommentUpdateRequest
│       └── response
│           └── CommentResponse
├── postlike
│   ├── controller
│   │   └── PostLikeController
│   ├── service
│   │   └── PostLikeService
│   ├── repository
│   │   └── PostLikeRepository
│   └── model
│       └── PostLike
├── refreshtoken
│   ├── repository
│   │   └── RefreshTokenRepository
│   └── model
│       └── RefreshToken
└── image
    ├── controller
    │   └── ImageController
    └── service
        └── ImageServicee
```

## 설계 고려사항

### 다형성을 활용한 Repository 설계
초기에는 PostRepository 인터페이스와 InMemoryPostRepository 구현체를 분리해서
Service는 인터페이스만 바라보도록 설계했다.
JPA 도입 시 PostRepository가 JpaRepository를 상속하는 것으로 교체하면서
Service 코드는 전혀 수정하지 않았다. 

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

### 인증/인가 구현
JwtAuthenticationFilter에서 모든 요청의 토큰을 검증한다.
회원가입, 로그인 등 인증이 필요 없는 경로는 화이트리스트로 관리해서 필터를 건너뛰도록 했다.
토큰에서 추출한 userId는 request.setAttribute()로 저장하고
Controller에서 @RequestAttribute로 꺼내 사용도록 했다.

### JWT 설계
같은 시크릿키로 서명하기 때문에 서명만으로는 두 토큰을 구분할 수 없어서
typ 클레임에 access 또는 refresh 값을 넣어 구분했다.
필터에서 RefreshToken으로 API 접근 시 401을 반환하도록 했다.
리프레시 토큰 탈취를 방지하고자 RTR(Refresh Token Rotation)을 적용해서 
토큰 재발급 시마다 RefreshToken도 교체하도록 했다.


## API 명세
###  인증 (Auth)
| Method | URL | 설명 | 인증 필요 |
| :--- | :--- | :--- | :---: |
| `POST` | `/auth/token` | 로그인 | X |
| `DELETE` | `/auth/token` | 로그아웃 | **O** |
| `POST` | `/auth/token/refresh` | 액세스 토큰 재발급 | X |
| `GET` | `/auth/check` | 로그인 상태 확인 | **O** |

---

###  회원 (User)
| Method | URL | 설명 | 인증 필요 |
| :--- | :--- | :--- | :---: |
| `POST` | `/users` | 회원가입 | X |
| `GET` | `/users/me` | 회원정보 조회 | **O** |
| `PATCH` | `/users/me` | 회원정보 수정 | **O** |
| `PATCH` | `/users/me/password` | 비밀번호 수정 | **O** |
| `DELETE` | `/users/me` | 회원탈퇴 | **O** |
| `GET` | `/users/email/check` | 이메일 중복 확인 | X |
| `GET` | `/users/nickname/check` | 닉네임 중복 확인 | X |

---

###  게시글 (Post)
| Method | URL | 설명 | 인증 필요 |
| :--- | :--- | :--- | :---: |
| `POST` | `/posts` | 게시글 등록 | **O** |
| `GET` | `/posts` | 게시글 목록 조회 (커서 기반) | **O** |
| `GET` | `/posts/{postId}` | 게시글 상세 조회 | **O** |
| `PATCH` | `/posts/{postId}` | 게시글 수정 | **O** |
| `DELETE` | `/posts/{postId}` | 게시글 삭제 | **O** |

---

###  댓글 (Comment)
| Method | URL | 설명 | 인증 필요 |
| :--- | :--- | :--- | :---: |
| `POST` | `/posts/{postId}/comments` | 댓글 등록 | **O** |
| `GET` | `/posts/{postId}/comments` | 댓글 목록 조회 | **O** |
| `PATCH` | `/posts/{postId}/comments/{commentId}` | 댓글 수정 | **O** |
| `DELETE` | `/posts/{postId}/comments/{commentId}` | 댓글 삭제 | **O** |

---

###  좋아요 (Like)
| Method | URL | 설명 | 인증 필요 |
| :--- | :--- | :--- | :---: |
| `POST` | `/posts/{postId}/likes` | 좋아요 | **O** |
| `DELETE` | `/posts/{postId}/likes` | 좋아요 취소 | **O** |

---

###  이미지 (Image)
| Method | URL | 설명 | 인증 필요 |
| :--- | :--- | :--- | :---: |
| `POST` | `/v1/users/upload/profile-image` | 프로필 이미지 업로드 | **O** |
| `POST` | `/v1/posts/upload/attach-file` | 게시글 이미지 업로드 | **O** |
