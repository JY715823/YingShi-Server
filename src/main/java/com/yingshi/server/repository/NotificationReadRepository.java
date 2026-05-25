package com.yingshi.server.repository;

import com.yingshi.server.domain.NotificationReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationReadRepository extends JpaRepository<NotificationReadEntity, String> {

    List<NotificationReadEntity> findByUserIdAndNotificationIdIn(String userId, Collection<String> notificationIds);

    Optional<NotificationReadEntity> findByUserIdAndNotificationId(String userId, String notificationId);
}
