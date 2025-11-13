package com.zergatstage.server.health;

import com.zergatstage.domain.makret.Market;
import com.zergatstage.server.repository.MarketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomHealthIndicatorIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MarketRepository marketRepository;

    @BeforeEach
    void seedMarket() {
        if (marketRepository.count() == 0) {
            Market market = Market.builder()
                    .marketId(1L)
                    .stationName("Test Station")
                    .systemName("Test System")
                    .stationType("Orbis Starport")
                    .build();
            marketRepository.save(market);
        }
    }

    @Test
    void actuatorHealthEndpointRespondsWithUpStatus() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/actuator/health",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody()).containsKey("components");
    }
}
