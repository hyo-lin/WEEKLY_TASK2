package com.example.community.user.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import com.example.community.user.dto.request.SignUpRequest;
import com.example.community.user.dto.request.UpdatePasswordRequest;
import com.example.community.user.dto.request.UpdateUserRequest;
import com.example.community.user.dto.response.UserResponse;
import com.example.community.user.model.User;
import com.example.community.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse signUp(SignUpRequest request){
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new GeneralException(StatusCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if(userRepository.existsByEmail(request.getEmail())){
            throw new GeneralException(StatusCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new GeneralException(StatusCode.DUPLICATE_NICKNAME);
        }
        User user = request.toEntity();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public boolean checkEmailDuplication(String email) {
        return userRepository.existsByEmail(email); // 존재하면 true, 없으면 false
    }

    @Transactional(readOnly = true)
    public boolean checkNicknameDuplication(String nickname) {
        return userRepository.existsByNickname(nickname); // 존재하면 true, 없으면 false
    }

    @Transactional
    public UserResponse getUser(Long userId){
        return UserResponse.from(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request){
        User user=findUserOrThrow(userId);
        if(!user.getNickname().equals(request.getNickname())
                && userRepository.existsByNickname(request.getNickname())){
            throw new GeneralException(StatusCode.DUPLICATE_NICKNAME);
        }
        user.updateNickname(request.getNickname());
        user.updateProfileImage(request.getProfileImage());
        return UserResponse.from(user);
    }

    @Transactional
    public void updatePassword(Long userId, UpdatePasswordRequest request){
        User user = findUserOrThrow(userId);
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new GeneralException(StatusCode.PASSWORD_CONFIRM_MISMATCH);
        }
        user.updatePassword(request.getNewPassword());
    }

    @Transactional
    public void deleteUser(Long userId){
        findUserOrThrow(userId);
        userRepository.deleteById(userId);
    }

    private User findUserOrThrow(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(StatusCode.USER_NOT_FOUND));
    }
}
