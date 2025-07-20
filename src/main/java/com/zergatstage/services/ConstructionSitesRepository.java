package com.zergatstage.services;


import com.zergatstage.domain.ConstructionSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstructionSitesRepository extends JpaRepository<ConstructionSite, Long> {
}
