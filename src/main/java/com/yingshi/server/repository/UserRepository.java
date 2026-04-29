package com.yingshi.server.repository;

import com.yingshi.server.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByAccount(String account);

    List<UserEntity> findByIdIn(Collection<String> ids);
}
