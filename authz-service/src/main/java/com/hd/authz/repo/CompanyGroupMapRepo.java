package com.hd.authz.repo;

import com.hd.authz.domain.CompanyGroupMap;
import com.hd.authz.domain.CompanyGroupMapId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyGroupMapRepo extends JpaRepository<CompanyGroupMap, CompanyGroupMapId> {
    List<CompanyGroupMap> findByCompanyGroupId(Long companyGroupId);
    void deleteByCompanyGroupId(Long companyGroupId);
}
