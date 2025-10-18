package com.zergatstage.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.http.dto.MarketItemDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataHttpServiceTest {

    private MockWebServer server;
    private MarketDataHttpService http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        http = new MarketDataHttpService(server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void postAndGetMarkets() throws Exception {
        // Enqueue responses
        server.enqueue(new MockResponse().setResponseCode(201));
        MarketDto dto = new MarketDto(1L, "Station", "Type", "System",
                List.of(new MarketItemDto(new CommodityDto(1L, "Gold", "Gold", "Metals", "Metals"), 1, 2, 3, 4)));
        String body = mapper.writeValueAsString(new MarketDto[]{dto});
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body).addHeader("Content-Type", "application/json"));

        // POST
        http.postMarkets(List.of(dto));
        // GET
        List<MarketDto> res = http.getMarkets();
        assertEquals(1, res.size());
        assertEquals("Station", res.get(0).getStationName());
    }
}

