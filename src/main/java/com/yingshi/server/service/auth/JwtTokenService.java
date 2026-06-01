package com.yingshi.server.service.auth;

import com.yingshi.server.common.IdGenerator;
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
    private static final String LIBRARY_ID_CLAIM = "libraryId";
    private static final String SESSION_ID_CLAIM = "sessionId";
    private static final String TOKEN_ID_CLAIM = "tokenId";

    private final AuthProperties authProperties;
    private final SecretKey secretKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.secretKey = Keys.hmacShaKeyFor(authProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public JwtTokenBundle issueTokensForNewSession(AuthenticatedUser authenticatedUser) {
        return issueTokens(authenticatedUser, IdGenerator.newId("session"));
    }

    public JwtTokenBundle issueTokens(AuthenticatedUser authenticatedUser, String sessionId) {
        Instant now = Instant.now();
        Instant accessExpireAt = now.plus(authProperties.getAccessTokenTtl());
        Instant refreshExpireAt = now.plus(authProperties.getRefreshTokenTtl());
        String accessTokenId = IdGenerator.newId("token");
        String refreshTokenId = IdGenerator.newId("token");

        String accessToken = buildToken(
                authenticatedUser,
                JwtTokenType.ACCESS,
                sessionId,
                accessTokenId,
                now,
                accessExpireAt
        );
        String refreshToken = buildToken(
                authenticatedUser,
                JwtTokenType.REFRESH,
                sessionId,
                refreshTokenId,
                now,
                refreshExpireAt
        );

        return new JwtTokenBundle(
                sessionId,
                refreshTokenId,
                accessToken,
                refreshToken,
                accessExpireAt,
                refreshExpireAt,
                accessExpireAt.toEpochMilli(),
                refreshExpireAt.toEpochMilli()
        );
    }

    public ParsedJwtToken parseAccessToken(String token) {
        return parseToken(token, JwtTokenType.ACCESS, "Access token is required.");
    }

    public ParsedJwtToken parseRefreshToken(String token) {
        return parseToken(token, JwtTokenType.REFRESH, "Refresh token is required.");
    }

    private ParsedJwtToken parseToken(String token, JwtTokenType expectedType, String invalidMessage) {
        Claims claims = parseClaims(token);
        JwtTokenType tokenType = JwtTokenType.valueOf(claims.get(TOKEN_TYPE_CLAIM, String.class));
        if (tokenType != expectedType) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_SESSION_INVALID,
                    invalidMessage
            );
        }
        return new ParsedJwtToken(
                claims.getSubject(),
                claims.get(ACCOUNT_CLAIM, String.class),
                claims.get(DISPLAY_NAME_CLAIM, String.class),
                claims.get(LIBRARY_ID_CLAIM, String.class),
                claims.get(SESSION_ID_CLAIM, String.class),
                claims.get(TOKEN_ID_CLAIM, String.class),
                claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant(),
                tokenType
        );
    }

    private String buildToken(
            AuthenticatedUser authenticatedUser,
            JwtTokenType tokenType,
            String sessionId,
            String tokenId,
            Instant issuedAt,
            Instant expireAt
    ) {
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(authenticatedUser.userId())
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .claim(ACCOUNT_CLAIM, authenticatedUser.account())
                .claim(DISPLAY_NAME_CLAIM, authenticatedUser.displayName())
                .claim(LIBRARY_ID_CLAIM, authenticatedUser.libraryId())
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(TOKEN_ID_CLAIM, tokenId)
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
