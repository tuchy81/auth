package com.hd.authz.repo;

import com.hd.authz.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuRepo extends JpaRepository<Menu, Long> {
    List<Menu> findBySystemCdOrderBySortOrderAscMenuIdAsc(String systemCd);
    List<Menu> findByParentMenuId(Long parentMenuId);

    @Query("SELECT m FROM Menu m WHERE m.systemCd = :s AND m.menuType = 'M'")
    List<Menu> findLeafMenusBySystem(@Param("s") String systemCd);
}
