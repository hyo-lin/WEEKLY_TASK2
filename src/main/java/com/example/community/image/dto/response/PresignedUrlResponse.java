package com.example.community.image.dto.response;

public record PresignedUrlResponse(
        String presignedUrl,
        String s3Url
) {}
