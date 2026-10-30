package com.zergatstage.monitor.routes.dto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable point-in-time snapshot of a personal Fleet Carrier used as input to the route
 * optimizer for {@link HaulingMode#LOAD_CARRIER} and {@link HaulingMode#UNLOAD_TO_SITE}.
 */
public final class CarrierSnapshot {

    private final long carrierId;
    private final String systemName;
    private final int freeSpace;
    private final Map<Long, Integer> hold;

    /**
     * Creates a new snapshot, defensively copying the supplied hold map.
     *
     * @param carrierId  the carrier's market/identifier
     * @param systemName the system the carrier is currently parked in, may be {@code null}
     * @param freeSpace  free cargo capacity remaining on the carrier, in tons
     * @param hold       commodity id -> quantity in tons currently held by the carrier;
     *                   {@code null} is treated as empty
     */
    public CarrierSnapshot(long carrierId, String systemName, int freeSpace, Map<Long, Integer> hold) {
        this.carrierId = carrierId;
        this.systemName = systemName;
        this.freeSpace = freeSpace;
        this.hold = hold != null ? new HashMap<>(hold) : new HashMap<>();
    }

    /**
     * @return the carrier's identifier
     */
    public long getCarrierId() {
        return carrierId;
    }

    /**
     * @return the system the carrier is currently parked in, or {@code null} if unknown
     */
    public String getSystemName() {
        return systemName;
    }

    /**
     * @return free cargo capacity remaining on the carrier, in tons
     */
    public int getFreeSpace() {
        return freeSpace;
    }

    /**
     * @return unmodifiable view of the carrier's hold (commodity id -> tons held); never {@code null}
     */
    public Map<Long, Integer> getHold() {
        return Collections.unmodifiableMap(hold);
    }

    /**
     * Looks up the quantity of a single commodity currently held by the carrier.
     *
     * @param commodityId the commodity id to look up, may be {@code null}
     * @return quantity in tons, or {@code 0} if the commodity is not held or id is {@code null}
     */
    public int getHeldQuantity(Long commodityId) {
        if (commodityId == null) {
            return 0;
        }
        Integer value = hold.get(commodityId);
        return value != null ? value : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CarrierSnapshot that = (CarrierSnapshot) o;
        return carrierId == that.carrierId
            && freeSpace == that.freeSpace
            && Objects.equals(systemName, that.systemName)
            && Objects.equals(hold, that.hold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carrierId, systemName, freeSpace, hold);
    }

    @Override
    public String toString() {
        return "CarrierSnapshot{"
            + "carrierId=" + carrierId
            + ", systemName='" + systemName + '\''
            + ", freeSpace=" + freeSpace
            + ", hold=" + hold
            + '}';
    }
}
