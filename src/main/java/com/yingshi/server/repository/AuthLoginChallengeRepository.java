package com.yingshi.server.repository;

import com.yingshi.server.domain.AuthLoginChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuthLoginChallengeRepository extends JpaRepository<AuthLoginChallengeEntity, String> {

    List<AuthLoginChallengeEntity> findByAccountAndConsumedAtIsNullAndInvalidatedAtIsNullAndExpireAtAfter(
            String account,
            Instant now
    );

    @Query("""
            select coalesce(sum(challenge.sendCount), 0)
            from AuthLoginChallengeEntity challenge
            where challenge.account = :account
              and challenge.lastSentAt >= :windowStart
            """)
    long sumSendCountByAccountSince(
            @Param("account") String account,
            @Param("windowStart") Instant windowStart
    );
}
