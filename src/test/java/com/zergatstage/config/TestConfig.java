package com.zergatstage.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@EntityScan(basePackages = "com.zergatstage.domain")
@EnableJpaRepositories(basePackages = "com.zergatstage.domain.makret")
public class TestConfig {
    // No additional config needed
}