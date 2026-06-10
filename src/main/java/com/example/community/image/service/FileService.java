package com.example.community.image.service;


import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${file.upload.path}")
    private String uploadPath;

    private final ImageProcessor imageValidator;

    // 파일 저장
    // 이미지 검증 후 날짜별 디렉토리에 UUID 파일명으로 저장
    // 반환값: /images/yyyy/MM/dd/uuid.ext 형태의 URL
    public String store(MultipartFile file) {
        imageValidator.validate(file);

        String ext = imageValidator.extractExtension(file.getOriginalFilename());
        String savedFilename = UUID.randomUUID() + "." + ext;
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path dirPath = Paths.get(uploadPath, datePath);

        log.info("uploadPath: {}", uploadPath);
        log.info("dirPath: {}", dirPath);

        try {
            Files.createDirectories(dirPath);
            file.transferTo(dirPath.resolve(savedFilename).toFile());
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage());
            throw new GeneralException(StatusCode.IMAGE_STORE_FAILED);
        }

        return "/images/" + datePath + "/" + savedFilename;
    }
    // 파일 삭제
    // imageUrl에서 실제 파일 경로를 추출하여 디스크에서 삭제
    public void delete(String imageUrl) {
        Path filePath = Paths.get(uploadPath, imageUrl.replace("/images/", ""));
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new GeneralException(StatusCode.IMAGE_DELETE_FAILED);
        }
    }
}