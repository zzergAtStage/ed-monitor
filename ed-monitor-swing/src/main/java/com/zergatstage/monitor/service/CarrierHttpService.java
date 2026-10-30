package com.zergatstage.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zergatstage.monitor.http.dto.FleetCarrierDto;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Thin HTTP client for the server-side Fleet Carrier endpoints, mirroring the
 * style of {@link ConstructionSitesHttpService}.
 */
public class CarrierHttpService {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpUrl baseUrl;

    public CarrierHttpService(String baseUrl) {
        this.baseUrl = HttpUrl.parse(baseUrl);
        if (this.baseUrl == null) throw new IllegalArgumentException("Invalid baseUrl: " + baseUrl);
    }

    /**
     * Fetches a single carrier by id.
     *
     * @param carrierId the carrier id
     * @return the carrier DTO, or empty if the server has no record (HTTP 404)
     * @throws IOException on any other transport or HTTP failure
     */
    public Optional<FleetCarrierDto> getCarrier(long carrierId) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("api/v1/carriers")
                .addPathSegment(String.valueOf(carrierId))
                .build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = client.newCall(req).execute()) {
            if (res.code() == 404) return Optional.empty();
            if (!res.isSuccessful()) throw new IOException("GET /api/v1/carriers/" + carrierId + " failed: " + res.code());
            return Optional.ofNullable(mapper.readValue(res.body().byteStream(), FleetCarrierDto.class));
        }
    }

    /**
     * Fetches all carriers known to the server.
     *
     * @return list of carrier DTOs; never null
     * @throws IOException on transport or HTTP failure
     */
    public List<FleetCarrierDto> getCarriers() throws IOException {
        HttpUrl url = baseUrl.newBuilder().addPathSegments("api/v1/carriers").build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("GET /api/v1/carriers failed: " + res.code());
            FleetCarrierDto[] arr = mapper.readValue(res.body().byteStream(), FleetCarrierDto[].class);
            return Arrays.asList(arr);
        }
    }

    /**
     * Pushes a carrier record to the server.
     *
     * @param dto the carrier state to persist
     * @return the server's persisted representation (with the updated version)
     * @throws IOException                on transport or HTTP failure
     * @throws VersionConflictException   if the server rejects the write with HTTP 409;
     *                                     carries the server's current DTO so the caller can retry
     */
    public FleetCarrierDto putCarrier(FleetCarrierDto dto) throws IOException, VersionConflictException {
        String json = mapper.writeValueAsString(dto);
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("api/v1/carriers")
                .addPathSegment(String.valueOf(dto.getCarrierId()))
                .build();
        Request req = new Request.Builder().url(url)
                .put(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (res.code() == 409) {
                FleetCarrierDto latest = getCarrier(dto.getCarrierId()).orElse(null);
                throw new VersionConflictException(latest);
            }
            if (!res.isSuccessful()) throw new IOException("PUT /api/v1/carriers/" + dto.getCarrierId() + " failed: " + res.code());
            return mapper.readValue(res.body().byteStream(), FleetCarrierDto.class);
        }
    }

    /**
     * Thrown when a PUT is rejected because the server's optimistic-lock version has
     * advanced past the version the client believed it was updating.
     */
    public static class VersionConflictException extends IOException {
        private final FleetCarrierDto latest;

        public VersionConflictException(FleetCarrierDto latest) {
            super("Version conflict");
            this.latest = latest;
        }

        public FleetCarrierDto getLatest() {
            return latest;
        }
    }
}
