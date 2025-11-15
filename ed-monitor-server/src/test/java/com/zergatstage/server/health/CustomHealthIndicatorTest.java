package com.zergatstage.server.health;

import com.zergatstage.server.repository.MarketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomHealthIndicatorTest {

    @Test
    void reportsUpWhenDatabaseAndMarketAvailable() {
        CustomHealthIndicator indicator = new CustomHealthIndicator(
                workingDataSource(),
                marketRepositoryStub(3L)
        );

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("status", "UP");
    }

    @Test
    void reportsDegradedWhenMarketDataMissing() {
        CustomHealthIndicator indicator = new CustomHealthIndicator(
                workingDataSource(),
                marketRepositoryStub(0L)
        );

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails()).containsEntry("status", "DEGRADED");
    }

    @Test
    void reportsDownWhenDatabaseUnavailable() {
        CustomHealthIndicator indicator = new CustomHealthIndicator(
                failingDataSource(),
                marketRepositoryStub(3L)
        );

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("status", "DOWN");
    }

    private static DataSource workingDataSource() {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> null
        );
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getConnection".equals(name)) {
                        return connection;
                    }
                    if ("isWrapperFor".equals(name)) {
                        return false;
                    }
                    if ("unwrap".equals(name)) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static DataSource failingDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        throw new SQLException("Connection refused");
                    }
                    if ("isWrapperFor".equals(method.getName())) {
                        return false;
                    }
                    if ("unwrap".equals(method.getName())) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static MarketRepository marketRepositoryStub(long count) {
        return (MarketRepository) Proxy.newProxyInstance(
                MarketRepository.class.getClassLoader(),
                new Class[]{MarketRepository.class},
                (proxy, method, args) -> {
                    if ("count".equals(method.getName())) {
                        return count;
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "MarketRepositoryStub";
                    }
                    throw new UnsupportedOperationException("Method not supported in stub: " + method.getName());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        if (returnType == char.class) return '\0';
        return null;
    }
}
