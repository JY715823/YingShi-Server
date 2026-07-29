package com.yingshi.server.common.cursor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R3-D-3: cursor 编解码测试.
 *
 * 验证 [CursorCodec] 的以下契约:
 * 1. 正向: encode -> decode 往返一致
 * 2. 正向: 不同 secret 生成不同 signature (HMAC 实例隔离)
 * 3. 边界: 篡改 cursor payload 触发签名校验失败
 * 4. 边界: 非法 Base64 / 缺少分隔符 / 非法 HMAC 抛 IllegalArgumentException
 * 5. 边界: payload 中包含冒号字符仍能正确解码 (使用 lastIndexOf 而非 split)
 *
 * 不依赖 Spring 上下文: 通过 ReflectionTestUtils 注入 secret.
 */
class CursorCodecTest {

    private CursorCodec codec;

    @BeforeEach
    void setUp() {
        codec = new CursorCodec();
        ReflectionTestUtils.setField(codec, "secret", "test-secret-for-cursor-codec");
    }

    @Test
    void encodeThenDecodeRoundTrips() {
        String payload = "media_12345:1700000000000";
        String cursor = codec.encode(payload);

        assertThat(cursor).isNotNull();
        assertThat(cursor).isNotBlank();

        String decoded = codec.decode(cursor);
        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void encodeProducesBase64UrlWithoutPadding() {
        String payload = "simple-payload";
        String cursor = codec.encode(payload);

        // Base64 URL-safe 不含 '+' '/' '=', 不含换行
        assertThat(cursor).doesNotContain("+", "/", "=", "\n", "\r");
        // 应可被 Base64.getUrlDecoder() 解码
        byte[] bytes = Base64.getUrlDecoder().decode(cursor);
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void differentPayloadsProduceDifferentCursors() {
        String cursor1 = codec.encode("payload-A");
        String cursor2 = codec.encode("payload-B");

        assertThat(cursor1).isNotEqualTo(cursor2);
    }

    @Test
    void samePayloadEncodesToSameCursorDeterministically() {
        // 同 secret + 同 payload -> 同 cursor (HMAC 是确定性的)
        String payload = "deterministic-payload";
        String cursor1 = codec.encode(payload);
        String cursor2 = codec.encode(payload);
        assertThat(cursor1).isEqualTo(cursor2);
    }

    @Test
    void differentSecretsProduceDifferentSignatures() {
        String payload = "shared-payload";
        String cursor1 = codec.encode(payload);

        // 换一个 secret
        CursorCodec codec2 = new CursorCodec();
        ReflectionTestUtils.setField(codec2, "secret", "different-secret-xyz");
        String cursor2 = codec2.encode(payload);

        assertThat(cursor1).isNotEqualTo(cursor2);

        // codec2 生成的 cursor 用 codec1 decode 应失败 (签名不匹配)
        assertThatThrownBy(() -> codec.decode(cursor2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor");
    }

    @Test
    void decodeRejectsTamperedPayload() {
        // 构造合法 cursor
        String payload = "legitimate-payload";
        String cursor = codec.encode(payload);

        // 篡改: 替换 payload 部分但保留原 signature
        String decoded = new String(Base64.getUrlDecoder().decode(cursor));
        int lastColon = decoded.lastIndexOf(":");
        assertThat(lastColon).isGreaterThan(0);
        String tampered = "tampered" + decoded.substring(lastColon);
        String tamperedCursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes());

        assertThatThrownBy(() -> codec.decode(tamperedCursor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor");
    }

    @Test
    void decodeRejectsTamperedSignature() {
        String payload = "legitimate-payload";
        String cursor = codec.encode(payload);

        // 篡改: 修改 signature 末尾字符
        char lastChar = cursor.charAt(cursor.length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tamperedCursor = cursor.substring(0, cursor.length() - 1) + replacement;

        assertThatThrownBy(() -> codec.decode(tamperedCursor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidBase64() {
        assertThatThrownBy(() -> codec.decode("!!!not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor");
    }

    @Test
    void decodeRejectsNullInput() {
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsEmptyInput() {
        assertThatThrownBy(() -> codec.decode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsMissingColonSeparator() {
        // 构造一个 Base64 URL-safe 但内部不含 ":" 的字符串
        // CursorCodec.decode 内部抛 "Invalid cursor format", 但外层 catch (Exception) 会重新包装为 "Invalid cursor"
        // 因此断言匹配外层包装后的消息 "Invalid cursor" (与其它 decode 失败场景一致)
        // 同时验证 cause 也是 IllegalArgumentException (内部 lastColon < 0 分支)
        String noColon = "noColonHere";
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(noColon.getBytes());

        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        // 验证 cause 的消息为 "Invalid cursor format" (内部具体错误信息)
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codec.decode(cursor))
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor format");
    }

    @Test
    void payloadWithColonsDecodesCorrectly() {
        // payload 中允许包含 ":" (例如 "media_1:media_2:1700000000")
        // 实现使用 lastIndexOf 取出 signature, 剩余全部为 payload
        String payload = "media_1:media_2:1700000000";
        String cursor = codec.encode(payload);
        String decoded = codec.decode(cursor);

        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void emptyPayloadCanBeEncoded() {
        // 空 payload 也应能正确往返 (虽然业务上不会用, 但不应崩溃)
        String payload = "";
        String cursor = codec.encode(payload);
        String decoded = codec.decode(cursor);

        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void unicodePayloadRoundTrips() {
        // 中文 payload 也应能正确往返 (UTF-8 编码)
        String payload = "用户:user-测试-2026";
        String cursor = codec.encode(payload);
        String decoded = codec.decode(cursor);

        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void longPayloadRoundTrips() {
        // 长 payload (>1KB) 也应能正确往返
        String payload = "x".repeat(2048);
        String cursor = codec.encode(payload);
        String decoded = codec.decode(cursor);

        assertThat(decoded).isEqualTo(payload);
    }
}
