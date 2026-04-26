package com.hd.authz.repo;

import com.hd.authz.domain.ApiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiRepo extends JpaRepository<ApiEntity, Long> {
    Optional<ApiEntity> findBySystemCdAndHttpMethodAndUrlPattern(String systemCd, String httpMethod, String urlPattern);
    List<ApiEntity> findBySystemCd(String systemCd);
}
