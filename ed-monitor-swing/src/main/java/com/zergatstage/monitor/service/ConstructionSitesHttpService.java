package com.zergatstage.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ConstructionSitesHttpService {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpUrl baseUrl;

    public ConstructionSitesHttpService(String baseUrl) {
        this.baseUrl = HttpUrl.parse(baseUrl);
        if (this.baseUrl == null) throw new IllegalArgumentException("Invalid baseUrl: " + baseUrl);
    }

    public void postSites(List<ConstructionSiteDto> sites) throws IOException {
        String json = mapper.writeValueAsString(sites);
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegments("api/construction-sites").build())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("POST /api/construction-sites failed: " + res.code());
        }
    }

    public List<ConstructionSiteDto> getSites(boolean includeCompleted) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("api/construction-sites")
                .addQueryParameter("includeCompleted", String.valueOf(includeCompleted))
                .build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("GET /api/construction-sites failed: " + res.code());
            ConstructionSiteDto[] arr = mapper.readValue(res.body().byteStream(), ConstructionSiteDto[].class);
            return Arrays.asList(arr);
        }
    }
}

