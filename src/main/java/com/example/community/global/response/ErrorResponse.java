package com.example.community.global.response;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private String message;
    private Object data = null;

    public ErrorResponse(String message) {
        this.message = message;
    }
}
