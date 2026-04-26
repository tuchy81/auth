package com.hd.authz.repo;

import com.hd.authz.domain.CompanyGroupMap;
import com.hd.authz.domain.CompanyGroupMapId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyGroupMapRepo extends JpaRepository<CompanyGroupMap, CompanyGroupMapId> {
    List<CompanyGroupMap> findByCompanyGroupId(Long companyGroupId);
    void deleteByCompanyGroupId(Long companyGroupId);

    /** spec §2.7 — CG inheritance: companies in any CG → CG IDs */
    @Query("SELECT m.companyGroupId FROM CompanyGroupMap m WHERE m.companyCd = :companyCd")
    List<Long> findGroupIdsByCompanyCd(@Param("companyCd") String companyCd);
}
