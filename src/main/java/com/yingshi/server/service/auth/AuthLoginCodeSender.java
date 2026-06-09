package com.yingshi.server.service.auth;

import java.time.Instant;

public interface AuthLoginCodeSender {

    void sendLoginCode(String email, String displayName, String code, Instant expireAt);
}
