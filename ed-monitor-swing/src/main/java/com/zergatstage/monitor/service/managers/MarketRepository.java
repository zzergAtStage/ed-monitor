package com.zergatstage.monitor.service.managers;

import com.zergatstage.domain.makret.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * The MarketRepository class is responsible for persisting market data into an H2 database.
 * <p>
 * This refactored version uses the Reflection API to dynamically generate SQL statements for the Market
 * and Commodity entities, thus reducing boilerplate code. For entities with composite keys such as MarketItem,
 * explicit SQL statements are used.
 * </p>
 */

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
}