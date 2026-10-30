package com.zergatstage.server.repository;

import com.zergatstage.domain.carrier.FleetCarrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FleetCarrierRepository extends JpaRepository<FleetCarrier, Long> {
}
