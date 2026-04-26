package com.hd.authz.repo;

import com.hd.authz.domain.MenuActionApi;
import com.hd.authz.domain.MenuActionApiId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuActionApiRepo extends JpaRepository<MenuActionApi, MenuActionApiId> {
    List<MenuActionApi> findByMenuIdAndActionCd(Long menuId, String actionCd);
    List<MenuActionApi> findByMenuId(Long menuId);
}
