package com.example.samdi.domain.user.repository;

import com.example.samdi.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserName(String userName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.userName = :userName")
    Optional<User> findByUserNameForUpdate(@Param("userName") String userName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Modifying
    @Query(value = "DELETE FROM score WHERE user_id = :userId", nativeQuery = true)
    void deleteScores(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM user_achievements WHERE user_id = :userId", nativeQuery = true)
    void deleteAchievements(@Param("userId") Long userId);

}
