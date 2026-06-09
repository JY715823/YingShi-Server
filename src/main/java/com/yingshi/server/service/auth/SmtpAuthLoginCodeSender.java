package com.yingshi.server.service.auth;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.AuthMailProperties;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class SmtpAuthLoginCodeSender implements AuthLoginCodeSender {

    private static final DateTimeFormatter EXPIRE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final AuthMailProperties properties;
    private final JavaMailSenderImpl mailSender;

    public SmtpAuthLoginCodeSender(AuthMailProperties properties) {
        this.properties = properties;
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost(properties.host());
        this.mailSender.setPort(properties.port());
        this.mailSender.setUsername(properties.username());
        this.mailSender.setPassword(properties.password());
        this.mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties javaMailProperties = this.mailSender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", String.valueOf(properties.auth()));
        javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(properties.starttls()));
        javaMailProperties.put("mail.smtp.connectiontimeout", "5000");
        javaMailProperties.put("mail.smtp.timeout", "8000");
        javaMailProperties.put("mail.smtp.writetimeout", "8000");
    }

    @Override
    public void sendLoginCode(String email, String displayName, String code, Instant expireAt) {
        if (!properties.enabled()) {
            throw sendFailure("邮箱验证码发送未启用，请检查邮件配置。");
        }
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            throw sendFailure("邮箱验证码配置不完整，请补充 QQ 邮箱账号和授权码。");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(email);
            helper.setSubject("映世登录验证码");
            helper.setText(buildBody(displayName, code, expireAt), false);
            mailSender.send(message);
        } catch (Exception exception) {
            throw sendFailure("验证码发送失败，请稍后重试。");
        }
    }

    private String buildBody(String displayName, String code, Instant expireAt) {
        String resolvedDisplayName = displayName == null || displayName.isBlank() ? "你" : displayName.trim();
        return """
                %s，你好：

                你的映世登录验证码是：%s
                该验证码 5 分钟内有效，请勿泄露给他人。
                预计失效时间：%s（北京时间）

                如果这不是你本人操作，请忽略本邮件。
                """.formatted(resolvedDisplayName, code, EXPIRE_TIME_FORMATTER.format(expireAt));
    }

    private ApiException sendFailure(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AUTH_LOGIN_CODE_SEND_FAILED, message);
    }
}
