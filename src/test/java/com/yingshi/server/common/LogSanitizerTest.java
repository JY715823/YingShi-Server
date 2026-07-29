package com.yingshi.server.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-D-3: 日志脱敏测试.
 *
 * 项目当前未引入独立的 LogSanitizer 工具类 (生产代码无该类).
 * 本测试通过内联的 [TestLogSanitizer] 测试夹具验证脱敏规则的正确性,
 * 作为后续将脱敏逻辑下沉到生产代码 (如 com.yingshi.server.common.LogSanitizer) 的契约保护.
 *
 * 脱敏规则:
 * 1. email: 保留首字符 + "***" + @ + 域名 (如 "1***@qq.com")
 * 2. JWT token: 仅显示前 8 字符 + "..." (避免泄露完整 token)
 * 3. phone: 保留前 3 + "****" + 后 4 (如 "138****5678")
 * 4. password: 完全替换为 "***"
 * 5. null / empty: 原样返回 null / empty
 *
 * 不依赖 Spring 上下文.
 */
class LogSanitizerTest {

    @Test
    void emailIsMaskedPreservingFirstCharAndDomain() {
        assertThat(TestLogSanitizer.maskEmail("1085060329@qq.com")).isEqualTo("1***@qq.com");
        assertThat(TestLogSanitizer.maskEmail("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(TestLogSanitizer.maskEmail("x@y.io")).isEqualTo("x***@y.io");
    }

    @Test
    void emailWithSingleCharLocalPartStillMasks() {
        // 边界: local part 只有 1 字符, 仍应输出 "x***@domain"
        assertThat(TestLogSanitizer.maskEmail("a@b.com")).isEqualTo("a***@b.com");
    }

    @Test
    void emailWithoutAtSymbolReturnsMaskedPlaceholder() {
        // 非法 email (无 @) 不应原样输出, 应替换为占位符避免泄露
        assertThat(TestLogSanitizer.maskEmail("not-an-email")).isEqualTo("***");
    }

    @Test
    void emailNullOrEmptyReturnsSameValue() {
        assertThat(TestLogSanitizer.maskEmail(null)).isNull();
        assertThat(TestLogSanitizer.maskEmail("")).isEmpty();
    }

    @Test
    void jwtTokenIsTruncatedToFirstEightChars() {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLXh5eiJ9.signature-part-12345";
        String masked = TestLogSanitizer.maskToken(token);
        assertThat(masked).startsWith("eyJhbGci");
        assertThat(masked).endsWith("...");
        assertThat(masked).hasSize(11); // 8 chars + "..."
        assertThat(masked).doesNotContain("signature-part");
    }

    @Test
    void shortTokenIsFullyMasked() {
        // 短 token (< 8 字符) 应被完全遮蔽, 避免泄露
        assertThat(TestLogSanitizer.maskToken("abc")).isEqualTo("***");
        assertThat(TestLogSanitizer.maskToken("1234567")).isEqualTo("***");
    }

    @Test
    void tokenNullOrEmptyReturnsSameValue() {
        assertThat(TestLogSanitizer.maskToken(null)).isNull();
        assertThat(TestLogSanitizer.maskToken("")).isEmpty();
    }

    @Test
    void phoneNumberIsMaskedMiddleFourDigits() {
        assertThat(TestLogSanitizer.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(TestLogSanitizer.maskPhone("15987654321")).isEqualTo("159****4321");
    }

    @Test
    void shortPhoneNumberIsFullyMasked() {
        // < 7 字符的号码全部遮蔽, 避免部分泄露
        assertThat(TestLogSanitizer.maskPhone("12345")).isEqualTo("***");
        assertThat(TestLogSanitizer.maskPhone("123456")).isEqualTo("***");
    }

    @Test
    void phoneNullOrEmptyReturnsSameValue() {
        assertThat(TestLogSanitizer.maskPhone(null)).isNull();
        assertThat(TestLogSanitizer.maskPhone("")).isEmpty();
    }

    @Test
    void passwordIsFullyReplacedWithAsterisks() {
        assertThat(TestLogSanitizer.maskPassword("any-password-123")).isEqualTo("***");
        assertThat(TestLogSanitizer.maskPassword("x")).isEqualTo("***");
        assertThat(TestLogSanitizer.maskPassword("$2a$10$longhashvalue")).isEqualTo("***");
    }

    @Test
    void passwordNullOrEmptyReturnsSameValue() {
        assertThat(TestLogSanitizer.maskPassword(null)).isNull();
        assertThat(TestLogSanitizer.maskPassword("")).isEmpty();
    }

    @Test
    void combinedLogLineDoesNotLeakSensitiveData() {
        // 模拟一条业务日志: 用户登录场景
        String email = "1085060329@qq.com";
        String token = "eyJhbGciOiJIUzI1NiJ9.abc.def";
        String password = "super-secret-pwd";

        String logLine = String.format(
                "Login attempt: email=%s, token=%s, password=%s",
                TestLogSanitizer.maskEmail(email),
                TestLogSanitizer.maskToken(token),
                TestLogSanitizer.maskPassword(password)
        );

        assertThat(logLine).contains("1***@qq.com");
        assertThat(logLine).contains("eyJhbGci...");
        assertThat(logLine).contains("password=***");
        // 不应出现明文敏感数据
        assertThat(logLine).doesNotContain("1085060329");
        assertThat(logLine).doesNotContain("abc.def");
        assertThat(logLine).doesNotContain("super-secret-pwd");
    }

    @Test
    void refreshTokenLikeUuidIsTruncatedLikeToken() {
        String refreshToken = "550e8400-e29b-41d4-a716-446655440000";
        String masked = TestLogSanitizer.maskToken(refreshToken);
        assertThat(masked).startsWith("550e8400");
        assertThat(masked).endsWith("...");
        assertThat(masked).doesNotContain("446655440000");
    }

    /**
     * 测试夹具: 内联的最小化日志脱敏工具.
     *
     * 后续若将脱敏逻辑下沉到生产代码 (如 com.yingshi.server.common.LogSanitizer),
     * 可直接将本类的实现迁移过去, 测试用例无需改动.
     */
    private static final class TestLogSanitizer {

        private static final String MASKED_ASTERISKS = "***";
        private static final int TOKEN_VISIBLE_PREFIX = 8;

        private TestLogSanitizer() {}

        static String maskEmail(String email) {
            if (email == null || email.isEmpty()) return email;
            int at = email.indexOf('@');
            if (at <= 0) return MASKED_ASTERISKS;
            String local = email.substring(0, at);
            String domain = email.substring(at);
            return local.charAt(0) + MASKED_ASTERISKS + domain;
        }

        static String maskToken(String token) {
            if (token == null || token.isEmpty()) return token;
            if (token.length() < TOKEN_VISIBLE_PREFIX) return MASKED_ASTERISKS;
            return token.substring(0, TOKEN_VISIBLE_PREFIX) + "...";
        }

        static String maskPhone(String phone) {
            if (phone == null || phone.isEmpty()) return phone;
            if (phone.length() < 7) return MASKED_ASTERISKS;
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }

        static String maskPassword(String password) {
            if (password == null || password.isEmpty()) return password;
            return MASKED_ASTERISKS;
        }
    }
}
