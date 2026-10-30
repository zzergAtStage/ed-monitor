package com.zergatstage.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zergatstage.monitor.http.dto.MarketDto;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MarketDataHttpService {
    private final OkHttpClient client = new OkHttpClient();
    // MarketDto carries an Instant (lastUpdated); without JSR-310 registered Jackson refuses to
    // (de)serialize it and every market fetch fails at parse time.
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpUrl baseUrl;

    public MarketDataHttpService(String baseUrl) {
        this.baseUrl = HttpUrl.parse(baseUrl);
        if (this.baseUrl == null) throw new IllegalArgumentException("Invalid baseUrl: " + baseUrl);
    }

    public void postMarkets(List<MarketDto> markets) throws IOException {
        String json = mapper.writeValueAsString(markets);
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/v1/markets").build())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("POST /api/v1/markets failed: " + res.code());
        }
    }

    public List<MarketDto> getMarkets() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/v1/markets").build())
                .get().build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("GET /api/v1/markets failed: " + res.code());
            MarketDto[] arr = mapper.readValue(res.body().byteStream(), MarketDto[].class);
            return Arrays.asList(arr);
        }
    }

    public MarketDto getMarket(Long id) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/v1/markets").addPathSegment(String.valueOf(id)).build())
                .get().build();
        try (Response res = client.newCall(req).execute()) {
            if (res.code() == 404) return null;
            if (!res.isSuccessful()) throw new IOException("GET /api/v1/markets/{id} failed: " + res.code());
            return mapper.readValue(res.body().byteStream(), MarketDto.class);
        }
    }

    /**
     * Flags (or unflags) a market as a bulk supply hub, a player-set preference the route
     * optimizer uses to favor that market when scores are otherwise close.
     *
     * @param marketId the market to update
     * @param value    {@code true} to mark as a supply hub, {@code false} to clear the flag
     * @throws IOException on transport or HTTP failure
     */
    public void putSupplyHub(long marketId, boolean value) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("api/v1/markets")
                .addPathSegment(String.valueOf(marketId))
                .addPathSegment("supply-hub")
                .addQueryParameter("value", String.valueOf(value))
                .build();
        Request req = new Request.Builder()
                .url(url)
                .put(RequestBody.create(new byte[0], MediaType.parse("application/json")))
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                throw new IOException("PUT /api/v1/markets/" + marketId + "/supply-hub failed: " + res.code());
            }
        }
    }
}

