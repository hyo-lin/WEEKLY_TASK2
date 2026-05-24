package com.example.community.global.response;
import org.springframework.http.HttpStatus;

public enum StatusCode {

    CREATE_POST_SUCCESS(HttpStatus.OK, "create_post_success"),
    GET_POSTS_SUCCESS(HttpStatus.OK, "get_posts_success"),
    UPDATE_POST_SUCCESS(HttpStatus.OK, "update_post_success"),
    DELETE_POST_SUCCESS(HttpStatus.OK, "delete_post_success"),

    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post_not_found"),
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
