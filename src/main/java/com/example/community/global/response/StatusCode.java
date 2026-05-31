package com.example.community.global.response;
import org.springframework.http.HttpStatus;

public enum StatusCode {

    AUTH_CHECK_SUCCESS(HttpStatus.OK, "auth_check_success"),

    // --- 회원 관련 성공 ---
    SIGN_UP_SUCCESS(HttpStatus.OK, "sign_up_success"),
    GET_USER_SUCCESS(HttpStatus.OK, "get_user_success"),
    UPDATE_USER_SUCCESS(HttpStatus.OK, "update_user_success"),
    UPDATE_PASSWORD_SUCCESS(HttpStatus.OK, "update_password_success"),
    DELETE_USER_SUCCESS(HttpStatus.OK, "delete_user_success"),
    CHECK_EMAIL_SUCCESS(HttpStatus.OK, "이메일 중복 확인에 성공했습니다."),
    CHECK_NICKNAME_SUCCESS(HttpStatus.OK, "닉네임 중복 확인에 성공했습니다."),

    // --- 인증/인가 성공 관련 ---
    LOGIN_SUCCESS(HttpStatus.OK, "login_success"),
    LOGOUT_SUCCESS(HttpStatus.OK, "logout_success"),
    TOKEN_REFRESH_SUCCESS(HttpStatus.OK, "token_refresh_success"),

    // --- 게시글 관련 성공 ---
    CREATE_POST_SUCCESS(HttpStatus.OK, "create_post_success"),
    GET_POST_SUCCESS(HttpStatus.OK, "get_post_success"),
    GET_POSTS_SUCCESS(HttpStatus.OK, "get_posts_success"),
    UPDATE_POST_SUCCESS(HttpStatus.OK, "update_post_success"),
    DELETE_POST_SUCCESS(HttpStatus.OK, "delete_post_success"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post_not_found"),

    // --- 댓글 관련 성공 (여기를 추가해 주자!) ---
    CREATE_COMMENT_SUCCESS(HttpStatus.OK, "create_comment_success"),
    GET_COMMENTS_SUCCESS(HttpStatus.OK, "get_comments_success"),
    UPDATE_COMMENT_SUCCESS(HttpStatus.OK, "update_comment_success"),
    DELETE_COMMENT_SUCCESS(HttpStatus.OK, "delete_comment_success"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "comment_not_found"),

    LIKE_SUCCESS(HttpStatus.OK, "like_success"),
    UNLIKE_SUCCESS(HttpStatus.OK, "unlike_success"),
    ALREADY_LIKED(HttpStatus.CONFLICT, "already_liked"),
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "like_not_found"),

    // --- 인증 실패 관련 ---
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user_not_found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden"),

    // --- 회원가입 및 비번 수정 ---
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "duplicate_email"),                 // 이메일 중복 (409)
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "duplicate_nickname"),           // 닉네임 중복 (409)
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "invalid_password"),             // 현재 비밀번호 틀림 (400)
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "password_mismatch"),

    // --- 이미지 업로드 관련  ---
    UPLOAD_IMAGE_SUCCESS(HttpStatus.OK, "upload_image_success"),
    UPLOAD_FILE_SUCCESS(HttpStatus.OK, "upload_file_success"),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "upload_image_fail"),

    // --- 공통 에러 ---
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "invalid_parameter_format"),
    INVALID_TITLE_LENGTH(HttpStatus.BAD_REQUEST, "invalid_title_length"),
    MISSING_REQUIRED_FIELDS(HttpStatus.BAD_REQUEST, "missing_required_fields");

    private final HttpStatus status;
    private final String message;

    StatusCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
