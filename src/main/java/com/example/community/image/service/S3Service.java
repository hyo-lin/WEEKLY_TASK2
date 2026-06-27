package com.example.community.image.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.presigned-url-expiration}")
    private long presignedUrlExpiration;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public PresignedUrlResponse generatePresignedUrl(String extension, String folder, Long userId) {
        String key = folder + "/" + userId + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("image/" + extension)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(presignedUrlExpiration))
                        .putObjectRequest(objectRequest)
                        .build()
        );

        String presignedUrl = presignedRequest.url().toString();
        String s3Url = "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + key;

        return new PresignedUrlResponse(presignedUrl, s3Url);
    }

    public void deleteFile(String s3Url) {
        String key = s3Url.replace("https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/", "");
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    public record PresignedUrlResponse(String presignedUrl, String s3Url) {}
}