package com.hd.authz.repo;

import com.hd.authz.domain.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemRepo extends JpaRepository<SystemEntity, String> {}
