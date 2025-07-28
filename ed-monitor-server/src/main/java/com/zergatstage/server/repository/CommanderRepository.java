package com.zergatstage.server.repository;

import com.zergatstage.domain.commander.Commander;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommanderRepository extends JpaRepository<Commander, Long> {

    Optional<Commander> findByFID(String fid);

    boolean existsByFID(String fid);
}
