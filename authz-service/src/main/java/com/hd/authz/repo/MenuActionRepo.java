package com.hd.authz.repo;

import com.hd.authz.domain.MenuAction;
import com.hd.authz.domain.MenuActionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuActionRepo extends JpaRepository<MenuAction, MenuActionId> {
    List<MenuAction> findByMenuId(Long menuId);
}
