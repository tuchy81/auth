package com.hd.authz.repo;

import com.hd.authz.domain.SystemShardConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShardConfigRepo extends JpaRepository<SystemShardConfig, String> {}
