package com.zergatstage.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zergatstage.monitor.http.dto.MarketDto;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MarketDataHttpService {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpUrl baseUrl;

    public MarketDataHttpService(String baseUrl) {
        this.baseUrl = HttpUrl.parse(baseUrl);
        if (this.baseUrl == null) throw new IllegalArgumentException("Invalid baseUrl: " + baseUrl);
    }

    public void postMarkets(List<MarketDto> markets) throws IOException {
        String json = mapper.writeValueAsString(markets);
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/markets").build())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("POST /api/markets failed: " + res.code());
        }
    }

    public List<MarketDto> getMarkets() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/markets").build())
                .get().build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("GET /api/markets failed: " + res.code());
            MarketDto[] arr = mapper.readValue(res.body().byteStream(), MarketDto[].class);
            return Arrays.asList(arr);
        }
    }

    public MarketDto getMarket(Long id) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/markets").addPathSegment(String.valueOf(id)).build())
                .get().build();
        try (Response res = client.newCall(req).execute()) {
            if (res.code() == 404) return null;
            if (!res.isSuccessful()) throw new IOException("GET /api/markets/{id} failed: " + res.code());
            return mapper.readValue(res.body().byteStream(), MarketDto.class);
        }
    }
}

