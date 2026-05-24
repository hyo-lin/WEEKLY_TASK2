package com.example.community.global.exception;

import com.example.community.global.response.StatusCode;

public class GeneralException extends RuntimeException {

    private final StatusCode statusCode;

    public GeneralException(StatusCode statusCode) {
        super(statusCode.getMessage());
        this.statusCode = statusCode;
    }
    public StatusCode getStatusCode() {
        return statusCode;
    }
}
