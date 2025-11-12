package com.zergatstage.server.market;

import com.zergatstage.server.market.dto.CommodityDto;
import com.zergatstage.server.market.dto.MarketDto;
import com.zergatstage.server.market.dto.MarketItemDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketControllerIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path){
        return "http://localhost:" + port + path;
    }

    @Test
    void postGetAndUpdateMarket() {
        // Build payload
        CommodityDto commodity = new CommodityDto(128049154L, "Gold", "Gold", "Metals", "Metals");
        MarketItemDto item = new MarketItemDto(commodity, 48000, 47000, 100, 0);
        MarketDto market = new MarketDto(3516841984L, "Baxter Base", "CraterPort", "Some System", List.of(item));

        // POST list
        ResponseEntity<MarketDto[]> postRes = rest.postForEntity(url("/api/v1/markets"), List.of(market), MarketDto[].class);
        assertEquals(HttpStatus.CREATED, postRes.getStatusCode());
        assertNotNull(postRes.getBody());
        assertTrue(postRes.getBody().length >= 1);

        // GET list
        ResponseEntity<MarketDto[]> getRes = rest.getForEntity(url("/api/v1/markets"), MarketDto[].class);
        assertEquals(HttpStatus.OK, getRes.getStatusCode());
        assertNotNull(getRes.getBody());
        assertTrue(getRes.getBody().length >= 1);

        // PUT update
        MarketDto toUpdate = getRes.getBody()[0];
        toUpdate.setStationName("Updated Station");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MarketDto> req = new HttpEntity<>(toUpdate, headers);
        ResponseEntity<MarketDto> putRes = rest.exchange(url("/api/v1/markets/" + toUpdate.getMarketId()), HttpMethod.PUT, req, MarketDto.class);
        assertEquals(HttpStatus.OK, putRes.getStatusCode());
        assertEquals("Updated Station", putRes.getBody().getStationName());
    }
}

