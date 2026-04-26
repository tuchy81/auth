package com.hd.authz.repo;

import com.hd.authz.domain.PermChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PermChangeLogRepo extends JpaRepository<PermChangeLog, Long> {
    @Query("SELECT p FROM PermChangeLog p WHERE p.processedYn = 'N' ORDER BY p.seq ASC")
    List<PermChangeLog> findUnprocessed(org.springframework.data.domain.Pageable pageable);
}
