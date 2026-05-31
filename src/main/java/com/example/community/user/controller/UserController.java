package com.example.community.user.controller;

import com.example.community.global.response.CommonResponse;
import com.example.community.global.response.StatusCode;
import com.example.community.user.dto.request.SignUpRequest;
import com.example.community.user.dto.request.UpdatePasswordRequest;
import com.example.community.user.dto.request.UpdateUserRequest;
import com.example.community.user.dto.response.UserResponse;
import com.example.community.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 회원가입
    @PostMapping("/users")
    public ResponseEntity<CommonResponse<UserResponse>> signUp(
            @RequestBody
            @Valid SignUpRequest request
    ){
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CommonResponse.success(StatusCode.SIGN_UP_SUCCESS, userService.signUp(request)));
    }

    @GetMapping("/users/email/check")
    public ResponseEntity<CommonResponse<Boolean>> checkEmail(
            @RequestParam("email") String email
    ) {
        boolean isDuplicated = userService.checkEmailDuplication(email);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.CHECK_EMAIL_SUCCESS, isDuplicated));
    }

    @GetMapping("/users/nickname/check")
    public ResponseEntity<CommonResponse<Boolean>> checkNickname(
            @RequestParam("nickname") String nickname
    ) {
        boolean isDuplicated = userService.checkNicknameDuplication(nickname);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.CHECK_NICKNAME_SUCCESS, isDuplicated));
    }


    // 회원정보 조회
    @GetMapping("/users/me")
    public ResponseEntity<CommonResponse<UserResponse>> getUser(
            @RequestAttribute("userId") Long userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.GET_USER_SUCCESS, userService.getUser(userId)));
    }

    // 회원정보 수정
    @PatchMapping("/users/me")
    public ResponseEntity<CommonResponse<UserResponse>> updateUser(
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid UpdateUserRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(StatusCode.UPDATE_USER_SUCCESS, userService.updateUser(userId, request)));
    }

    // 비밀번호 수정
    @PatchMapping("/users/me/password")
    public ResponseEntity<CommonResponse<Void>> updatePassword(
            @RequestAttribute("userId") Long userId,
            @RequestBody @Valid UpdatePasswordRequest request
    ) {
        userService.updatePassword(userId, request);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.UPDATE_PASSWORD_SUCCESS, null));
    }

    // 회원탈퇴
    @DeleteMapping("/users/me")
    public ResponseEntity<CommonResponse<Void>> deleteUser(
            @RequestAttribute("userId") Long userId
    ) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(CommonResponse.success(StatusCode.DELETE_USER_SUCCESS, null));
    }
}
