package com.hd.authz.repo;

import com.hd.authz.domain.MenuActionApi;
import com.hd.authz.domain.MenuActionApiId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Set;

public interface MenuActionApiRepo extends JpaRepository<MenuActionApi, MenuActionApiId> {
    List<MenuActionApi> findByMenuIdAndActionCd(Long menuId, String actionCd);
    List<MenuActionApi> findByMenuId(Long menuId);

    /** Step 4 — bulk lookup for matrix views. */
    @Query("SELECT m FROM MenuActionApi m WHERE m.apiId = :apiId")
    List<MenuActionApi> findByApiId(@Param("apiId") Long apiId);

    /** Step 4 — system-scoped distinct mapped api_ids in one query. */
    @Query("SELECT DISTINCT m.apiId FROM MenuActionApi m " +
           "WHERE m.apiId IN (SELECT a.apiId FROM ApiEntity a WHERE a.systemCd = :system)")
    Set<Long> findMappedApiIdsForSystem(@Param("system") String system);
}
