package com.example.community.image.dto.request;

import com.example.community.image.enums.ImageType;

public record PresignedUrlRequest(
        String extension,
        ImageType imageType
) {}