package com.example.community.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class CommonResponse<T> {
    private String message;
    private T data;

    public CommonResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    public static <T> CommonResponse<T> success(StatusCode statusCode, T data) {
        return new CommonResponse<>(statusCode.getMessage(), data);
    }
}
