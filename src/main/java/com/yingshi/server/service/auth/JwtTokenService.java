package com.yingshi.server.service.auth;

import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCOUNT_CLAIM = "account";
    private static final String DISPLAY_NAME_CLAIM = "displayName";
    private static final String SPACE_ID_CLAIM = "spaceId";

    private final AuthProperties authProperties;
    private final SecretKey secretKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public JwtTokenBundle issueTokens(AuthenticatedUser authenticatedUser) {
        Instant now = Instant.now();
        Instant accessExpireAt = now.plus(authProperties.getAccessTokenTtl());
        Instant refreshExpireAt = now.plus(authProperties.getRefreshTokenTtl());

        String accessToken = buildToken(authenticatedUser, JwtTokenType.ACCESS, now, accessExpireAt);
        String refreshToken = buildToken(authenticatedUser, JwtTokenType.REFRESH, now, refreshExpireAt);

        return new JwtTokenBundle(
                accessToken,
                refreshToken,
                accessExpireAt.toEpochMilli(),
                refreshExpireAt.toEpochMilli()
        );
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        JwtTokenType tokenType = JwtTokenType.valueOf(claims.get(TOKEN_TYPE_CLAIM, String.class));
        if (tokenType != JwtTokenType.ACCESS) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_SESSION_INVALID,
                    "Access token is required."
            );
        }
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get(ACCOUNT_CLAIM, String.class),
                claims.get(DISPLAY_NAME_CLAIM, String.class),
                claims.get(SPACE_ID_CLAIM, String.class)
        );
    }

    private String buildToken(
            AuthenticatedUser authenticatedUser,
            JwtTokenType tokenType,
            Instant issuedAt,
            Instant expireAt
    ) {
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(authenticatedUser.userId())
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .claim(ACCOUNT_CLAIM, authenticatedUser.account())
                .claim(DISPLAY_NAME_CLAIM, authenticatedUser.displayName())
                .claim(SPACE_ID_CLAIM, authenticatedUser.spaceId())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(authProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_TOKEN_EXPIRED, "Token has expired.");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "Invalid bearer token.");
        }
    }
}
