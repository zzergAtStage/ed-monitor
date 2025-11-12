package com.zergatstage.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ConstructionSitesHttpService {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
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

    public ConstructionSiteDto getSite(long id) throws IOException {
        HttpUrl url = baseUrl.newBuilder().addPathSegments("api/construction-sites").addPathSegment(String.valueOf(id)).build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = client.newCall(req).execute()) {
            if (res.code() == 404) return null;
            if (!res.isSuccessful()) throw new IOException("GET /api/construction-sites/" + id + " failed: " + res.code());
            return mapper.readValue(res.body().byteStream(), ConstructionSiteDto.class);
        }
    }

    public ConstructionSiteDto putSite(ConstructionSiteDto site) throws IOException, VersionConflictException {
        String json = mapper.writeValueAsString(site);
        HttpUrl url = baseUrl.newBuilder().addPathSegments("api/construction-sites").addPathSegment(String.valueOf(site.getMarketId())).build();
        Request req = new Request.Builder().url(url)
                .put(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (res.code() == 409) {
                // fetch latest and throw conflict
                ConstructionSiteDto latest = getSite(site.getMarketId());
                throw new VersionConflictException(latest);
            }
            if (!res.isSuccessful()) throw new IOException("PUT /api/construction-sites/" + site.getMarketId() + " failed: " + res.code());
            return mapper.readValue(res.body().byteStream(), ConstructionSiteDto.class);
        }
    }

    public static class VersionConflictException extends IOException {
        private final ConstructionSiteDto latest;
        public VersionConflictException(ConstructionSiteDto latest) {
            super("Version conflict");
            this.latest = latest;
        }
        public ConstructionSiteDto getLatest() { return latest; }
    }
}
