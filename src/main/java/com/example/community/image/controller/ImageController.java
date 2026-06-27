package com.example.community.image.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import com.example.community.image.dto.request.PresignedUrlRequest;
import com.example.community.image.dto.response.PresignedUrlResponse;
import com.example.community.image.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final S3Service s3Service;

    @PostMapping("/presigned-url")
    public ResponseEntity<CommonResponse<PresignedUrlResponse>> getPresignedUrl(
            @RequestBody PresignedUrlRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        S3Service.PresignedUrlResponse response = s3Service.generatePresignedUrl(
                request.extension(),
                request.imageType().toFolder(),
                userId
        );
        return ResponseEntity.ok(CommonResponse.success(
                StatusCode.IMAGE_UPLOAD_SUCCESS,
                new PresignedUrlResponse(response.presignedUrl(), response.s3Url())
        ));
    }
}