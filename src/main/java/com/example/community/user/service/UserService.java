package com.example.community.user.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.image.service.ProfileImageService;
import com.example.community.user.dto.request.SignUpRequest;
import com.example.community.user.dto.request.UpdatePasswordRequest;
import com.example.community.user.dto.request.UpdateUserRequest;
import com.example.community.user.dto.response.UserResponse;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileImageService profileImageService;
    private final BCryptPasswordEncoder passwordEncoder;


    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm()))
            throw new GeneralException(StatusCode.PASSWORD_CONFIRM_MISMATCH);
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
            throw new GeneralException(StatusCode.DUPLICATE_EMAIL);
        if (userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
            throw new GeneralException(StatusCode.DUPLICATE_NICKNAME);

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = userRepository.save(request.toEntity(encodedPassword));

        if (request.getProfileImageUrl() != null) {
            profileImageService.assignUser(user, request.getProfileImageUrl());
        }

        return UserResponse.from(user, request.getProfileImageUrl());
    }

    @Transactional(readOnly = true)
    public boolean checkEmailDuplication(String email) {
        return userRepository.existsByEmailAndDeletedAtIsNull(email); // 존재하면 true, 없으면 false
    }

    @Transactional(readOnly = true)
    public boolean checkNicknameDuplication(String nickname) {
        return userRepository.existsByNicknameAndDeletedAtIsNull(nickname); // 존재하면 true, 없으면 false
    }

    @Transactional
    public UserResponse getUser(Long userId) {
        User user = findUserOrThrow(userId);
        String profileImageUrl = profileImageService.getImageUrl(userId);
        return UserResponse.from(user, profileImageUrl);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        if (!user.getNickname().equals(request.getNickname())
                && userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
            throw new GeneralException(StatusCode.DUPLICATE_NICKNAME);

        user.updateNickname(request.getNickname());

        if (request.getProfileImageUrl() != null) {
            // 기존 이미지 소프트딜리트 후 새 이미지 연결
            profileImageService.replaceImage(user, request.getProfileImageUrl());
        } else {
            // null이면 이미지 삭제 (removeProfileButton 클릭한 경우)
            profileImageService.deleteIfExists(userId);
        }

        String profileImageUrl = profileImageService.getImageUrl(userId);
        return UserResponse.from(user, profileImageUrl);
    }

    @Transactional
    public void updatePassword(Long userId, UpdatePasswordRequest request){
        User user = findUserOrThrow(userId);
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new GeneralException(StatusCode.PASSWORD_CONFIRM_MISMATCH);
        }
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }
    // 프로필 이미지 삭제 후 유저 소프트딜리트
    @Transactional
    public void deleteUser(Long userId) {
        User user = findUserOrThrow(userId);
        if (!user.isActive()) {
            throw new GeneralException(StatusCode.USER_NOT_FOUND); // 혹은 이미 탈퇴한 회원 에러코드 사용
        }
        profileImageService.deleteIfExists(userId);
        user.delete();  // deleteById 대신 소프트딜리트
    }
    // 유저 조회 공통 메서드
    private User findUserOrThrow(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));

    }
}
