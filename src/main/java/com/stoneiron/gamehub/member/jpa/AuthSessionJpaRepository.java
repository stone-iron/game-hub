package com.stoneiron.gamehub.member.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, String> {

    @Modifying
    @Query("delete from AuthSessionEntity s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
