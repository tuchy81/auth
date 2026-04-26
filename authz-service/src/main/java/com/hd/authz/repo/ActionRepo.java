package com.hd.authz.repo;

import com.hd.authz.domain.Action;
import com.hd.authz.domain.ActionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActionRepo extends JpaRepository<Action, ActionId> {
    List<Action> findBySystemCdOrderBySortOrder(String systemCd);
}
