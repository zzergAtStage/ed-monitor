package com.zergatstage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void componentScanTargetsServerPackage() {
        ComponentScan scan = App.class.getAnnotation(ComponentScan.class);
        assertThat(scan).isNotNull();
        var configuredPackages = Arrays.asList(scan.basePackages().length > 0 ? scan.basePackages() : scan.value());
        assertThat(configuredPackages).contains("com.zergatstage.server");
    }

    @Test
    void entityScanIncludesDomainAndServerPackages() {
        EntityScan entityScan = App.class.getAnnotation(EntityScan.class);
        assertThat(entityScan).isNotNull();
        assertThat(Arrays.asList(entityScan.basePackages()))
                .contains("com.zergatstage.domain")
                .contains("com.zergatstage.server");
    }

    @Test
    void jpaRepositoriesCoverServerAndDomainPackages() {
        EnableJpaRepositories repos = App.class.getAnnotation(EnableJpaRepositories.class);
        assertThat(repos).isNotNull();
        assertThat(Arrays.asList(repos.basePackages()))
                .contains("com.zergatstage.server.repository")
                .contains("com.zergatstage.domain");
    }
}
