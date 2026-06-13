package com.example.community.user.repository;

import com.example.community.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmailAndDeletedAtIsNull(String email);


    // ️ 이메일 조건문 처리: 이메일이 일치하고 && 아직 탈퇴하지 않은 유저가 있는지 확인
    boolean existsByEmailAndDeletedAtIsNull(String email);

    // ️ 닉네임 조건문 처리: 닉네임이 일치하고 && 아직 탈퇴하지 않은 유저가 있는지 확인
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
