package com.social.backend.components.user.repository;

import com.social.backend.components.user.enums.Role;

import com.social.backend.components.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByVerificationToken(String verificationToken);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByPasswordResetToken(String passwordResetToken);

    // Case-insensitive login
    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchByUsername(@Param("query") String query);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY u.username ASC")
    List<User> searchByUsernameOrEmail(@Param("query") String query);

    long countByBanned(boolean banned);

    long countByRole(Role role);

    List<User> findByRole(Role role);
}