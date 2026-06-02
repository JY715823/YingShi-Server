package com.yingshi.server.service.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.yingshi.server.config.FcmProperties;
import com.yingshi.server.domain.PushDeviceTokenEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.push.fcm", name = "enabled", havingValue = "true")
public class FirebasePushMessageSender implements PushMessageSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushMessageSender.class);
    private static final String FIREBASE_APP_NAME = "yingshi-fcm";

    private final FcmProperties fcmProperties;
    private final FirebaseMessaging firebaseMessaging;

    public FirebasePushMessageSender(FcmProperties fcmProperties) {
        this.fcmProperties = fcmProperties;
        this.firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp(fcmProperties));
    }

    @Override
    public PushDeliveryResult sendDataMessage(
            List<PushDeviceTokenEntity> targetTokens,
            Map<String, String> data
    ) {
        int successful = 0;
        List<String> invalidTokens = new ArrayList<>();

        for (PushDeviceTokenEntity targetToken : targetTokens) {
            Message message = Message.builder()
                    .setToken(targetToken.getToken())
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();
            try {
                firebaseMessaging.send(message, fcmProperties.dryRun());
                successful++;
            } catch (FirebaseMessagingException exception) {
                if (isInvalidToken(exception)) {
                    invalidTokens.add(targetToken.getToken());
                }
                log.warn(
                        "FCM send failed for tokenId={}, errorCode={}, messagingErrorCode={}",
                        targetToken.getId(),
                        exception.getErrorCode(),
                        exception.getMessagingErrorCode(),
                        exception
                );
            }
        }

        return new PushDeliveryResult(targetTokens.size(), successful, invalidTokens);
    }

    private boolean isInvalidToken(FirebaseMessagingException exception) {
        MessagingErrorCode messagingErrorCode = exception.getMessagingErrorCode();
        return messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private FirebaseApp firebaseApp(FcmProperties properties) {
        return FirebaseApp.getApps().stream()
                .filter(app -> FIREBASE_APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(firebaseOptions(properties), FIREBASE_APP_NAME));
    }

    private FirebaseOptions firebaseOptions(FcmProperties properties) {
        if (!properties.hasCredentials()) {
            throw new IllegalStateException("FCM is enabled but no service account credentials were provided.");
        }
        try (InputStream inputStream = serviceAccountInputStream(properties)) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream));
            if (properties.projectId() != null) {
                builder.setProjectId(properties.projectId());
            }
            return builder.build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize Firebase Admin SDK.", exception);
        }
    }

    private InputStream serviceAccountInputStream(FcmProperties properties) throws IOException {
        if (properties.serviceAccountJsonBase64() != null) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(properties.serviceAccountJsonBase64()));
        }
        return Files.newInputStream(Path.of(properties.serviceAccountPath()));
    }
}
