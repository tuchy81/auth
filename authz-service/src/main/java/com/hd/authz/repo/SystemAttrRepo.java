package com.hd.authz.repo;

import com.hd.authz.domain.SystemAttr;
import com.hd.authz.domain.SystemAttrId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SystemAttrRepo extends JpaRepository<SystemAttr, SystemAttrId> {
    List<SystemAttr> findBySystemCd(String systemCd);
    void deleteBySystemCd(String systemCd);
}
