package com.zergatstage.server.health;

import com.zergatstage.server.repository.MarketRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Consolidates basic readiness checks so Swing clients can reason about backend state.
 */
@Component
public class CustomHealthIndicator extends AbstractHealthIndicator {

    private final DataSource dataSource;
    private final MarketRepository marketRepository;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public CustomHealthIndicator(ObjectProvider<DataSource> dataSourceProvider,
                                 ObjectProvider<MarketRepository> marketRepositoryProvider) {
        this.dataSource = dataSourceProvider.getIfAvailable();
        this.marketRepository = marketRepositoryProvider.getIfAvailable();
    }

    CustomHealthIndicator(DataSource dataSource, MarketRepository marketRepository) {
        this.dataSource = dataSource;
        this.marketRepository = marketRepository;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.withDetail("timestamp", Instant.now().toString());
        boolean databaseUp = checkDatabase(builder);
        boolean readinessOk = checkReadiness(builder);

        if (!databaseUp) {
            builder.down().withDetail("status", "DOWN");
            return;
        }
        if (!readinessOk) {
            builder.status("DEGRADED").withDetail("status", "DEGRADED");
            return;
        }
        builder.up().withDetail("status", "UP");
    }

    private boolean checkDatabase(Health.Builder builder) {
        if (dataSource == null) {
            builder.withDetail("database", Map.of(
                    "available", true,
                    "message", "DataSource not configured; assuming available"));
            return true;
        }
        try (Connection ignored = dataSource.getConnection()) {
            builder.withDetail("database", Map.of(
                    "available", true,
                    "message", "Connected"));
            return true;
        } catch (SQLException ex) {
            builder.withDetail("database", Map.of(
                    "available", false,
                    "message", Optional.ofNullable(ex.getMessage()).orElse("SQL exception")));
            return false;
        }
    }

    private boolean checkReadiness(Health.Builder builder) {
        if (marketRepository == null) {
            builder.withDetail("readiness", Map.of(
                    "ready", true,
                    "message", "Market repository not configured; treating as ready"));
            return true;
        }
        long markets = marketRepository.count();
        boolean ready = markets > 0;
        builder.withDetail("readiness", Map.of(
                "ready", ready,
                "marketsIndexed", markets));
        return ready;
    }
}
