package com.zergatstage.monitor.service.managers;

import com.zergatstage.domain.carrier.CarrierHoldItem;
import com.zergatstage.domain.carrier.FleetCarrier;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.monitor.http.dto.CarrierHoldItemDto;
import com.zergatstage.monitor.http.dto.CommodityDto;
import com.zergatstage.monitor.http.dto.FleetCarrierDto;
import com.zergatstage.monitor.service.BaseManager;
import com.zergatstage.monitor.service.CarrierHttpService;
import com.zergatstage.monitor.service.CommodityRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks the in-memory state of the commander's Fleet Carrier: identity/stats, current
 * system, and the cargo hold.
 *
 * <p>Shaped after {@link com.zergatstage.monitor.service.ConstructionSiteManager}'s dirty-flag
 * and server-sync pattern, but simplified for a single tracked carrier rather than a collection
 * of sites.</p>
 *
 * <p>Journal event handlers call the mutator methods here from a background parsing thread
 * (see {@link com.zergatstage.monitor.service.JournalLogMonitor}); every mutator is synchronized
 * on this instance to guard the shared {@link FleetCarrier} state. {@link BaseManager#notifyListeners()}
 * already marshals to the EDT, so callers must not wrap calls into this manager with their own
 * {@code SwingUtilities.invokeLater}.</p>
 */
@Slf4j
public class CarrierManager extends BaseManager {

    private static volatile CarrierManager instance;

    private final CommodityRegistry commodityRegistry;

    /** The single carrier tracked by this client. Never null; starts out unpopulated. */
    private final FleetCarrier carrier = FleetCarrier.builder()
            .hold(new ArrayList<>())
            .build();

    /** True once a CarrierStats event or an adopted server record has populated {@link #carrier}. */
    private boolean known = false;

    /** True when local state has changed since the last successful push to the server. */
    private boolean dirty = false;
    /**
     * Bumped by every local mutation. The sync loop records this value before it starts an
     * HTTP round trip and only clears {@link #dirty} if it is unchanged afterwards, so a
     * mutation that lands mid-flush is not silently marked as already synced.
     */
    private long revision = 0L;

    private CarrierHttpService httpService;
    private ScheduledExecutorService scheduler;

    private CarrierManager() {
        // Resolve directly from the CommodityRegistry singleton (not via DefaultManagerFactory):
        // this manager is constructed eagerly from inside DefaultManagerFactory's own
        // constructor, so routing through DefaultManagerFactory.getInstance() here would
        // recurse into a partially-constructed factory.
        this.commodityRegistry = CommodityRegistry.getInstance();
    }

    /**
     * Returns the process-wide {@code CarrierManager} singleton, creating it on first use.
     *
     * @return the singleton instance
     */
    public static CarrierManager getInstance() {
        if (instance == null) {
            synchronized (CarrierManager.class) {
                if (instance == null) {
                    instance = new CarrierManager();
                }
            }
        }
        return instance;
    }

    /**
     * Updates carrier identity and cargo capacity/usage stats, typically from a journal
     * {@code CarrierStats} event. Marks the carrier as known.
     *
     * @param carrierId     the carrier id (equals its MarketID)
     * @param callsign      the carrier's callsign, e.g. {@code XXX-000}
     * @param name          the carrier's player-assigned name
     * @param totalCapacity total cargo capacity in tons
     * @param cargoUsed     tons of cargo currently used
     * @param freeSpace     free cargo space in tons
     */
    public synchronized void updateStats(long carrierId, String callsign, String name,
                                          int totalCapacity, int cargoUsed, int freeSpace) {
        carrier.setCarrierId(carrierId);
        carrier.setCallsign(callsign);
        carrier.setName(name);
        carrier.setTotalCapacity(totalCapacity);
        carrier.setCargoUsed(cargoUsed);
        carrier.setFreeSpace(freeSpace);
        known = true;
        markDirty();
        notifyListeners();
    }

    /**
     * Updates the carrier's current system from a journal {@code CarrierJump}/{@code CarrierLocation}
     * event. This is a no-op if the system has been set manually ({@link #setSystemManually}),
     * since a manual override reflects information the player trusts more than journal-derived
     * state (e.g. correcting a stale or ambiguous reading) and must not be silently clobbered by
     * subsequent journal events.
     *
     * @param systemName the system name reported by the journal
     */
    public synchronized void updateSystem(String systemName) {
        if (carrier.isSystemManuallySet()) {
            return;
        }
        carrier.setCurrentSystem(systemName);
        markDirty();
        notifyListeners();
    }

    /**
     * Manually sets the carrier's current system and marks it as manually overridden so that
     * subsequent {@link #updateSystem(String)} calls from journal events no longer overwrite it.
     *
     * @param systemName the system name to set
     */
    public synchronized void setSystemManually(String systemName) {
        carrier.setCurrentSystem(systemName);
        carrier.setSystemManuallySet(true);
        markDirty();
        notifyListeners();
    }

    /**
     * Replaces the entire cargo hold with an absolute snapshot, typically parsed from the
     * carrier's own {@code Market.json} (a full re-read, not a delta). Ignored if
     * {@code snapshotAt} is not after the currently stored {@link #getHoldSnapshotAt()} snapshot
     * time, so an out-of-order or duplicate read cannot regress the hold state.
     *
     * @param quantitiesByCommodityId map of commodity id to tons currently held
     * @param snapshotAt              timestamp the snapshot was taken
     */
    public synchronized void applyHoldSnapshot(Map<Long, Integer> quantitiesByCommodityId, Instant snapshotAt) {
        if (carrier.getHoldSnapshotAt() != null && snapshotAt != null
                && !snapshotAt.isAfter(carrier.getHoldSnapshotAt())) {
            log.debug("Ignoring stale carrier hold snapshot at {} (current snapshot at {})",
                    snapshotAt, carrier.getHoldSnapshotAt());
            return;
        }
        carrier.getHold().clear();
        if (quantitiesByCommodityId != null) {
            for (Map.Entry<Long, Integer> entry : quantitiesByCommodityId.entrySet()) {
                Commodity commodity = commodityRegistry.getCommodityById(entry.getKey());
                if (commodity == null) {
                    log.warn("Unknown commodity id {} in carrier hold snapshot. Skipping item.", entry.getKey());
                    continue;
                }
                carrier.setHeldQuantity(commodity, entry.getValue());
            }
        }
        carrier.setHoldSnapshotAt(snapshotAt);
        known = true;
        markDirty();
        notifyListeners();
    }

    /**
     * Applies an additive change to a single commodity in the hold, typically from a journal
     * {@code CargoTransfer} event.
     *
     * <p>Because the entire journal is replayed from byte offset 0 on every application startup
     * (see {@code AppendFileReadStrategy}), the same transfer event can be re-processed after a
     * restart. The event is safely idempotent here: it is ignored whenever {@code eventTimestamp}
     * is not strictly after {@link #getHoldSnapshotAt()}, because any transfer at or before the
     * stored snapshot time is already baked into that snapshot's totals. Only transfers that
     * happened after the last snapshot are new information.</p>
     *
     * @param commodityId    the canonical commodity id
     * @param delta          the change in tons; positive to add, negative to remove
     * @param eventTimestamp the journal timestamp of the transfer event
     */
    public synchronized void applyTransferDelta(long commodityId, int delta, Instant eventTimestamp) {
        // No baseline yet: a delta is meaningless without a total to add it to. This is the
        // normal state during the startup journal replay, before the server pull (+5s) or a
        // Market.json read has established a baseline. Applying deltas here would reconstruct
        // the hold from the latest journal file alone, mark the record dirty, and let the sync
        // loop flush that garbage over the good server-side state.
        if (carrier.getHoldSnapshotAt() == null) {
            log.debug("Ignoring carrier transfer delta at {}: no hold baseline established yet.",
                    eventTimestamp);
            return;
        }
        if (eventTimestamp != null && !eventTimestamp.isAfter(carrier.getHoldSnapshotAt())) {
            log.debug("Ignoring carrier transfer delta at {}, already covered by snapshot at {}",
                    eventTimestamp, carrier.getHoldSnapshotAt());
            return;
        }
        Commodity commodity = commodityRegistry.getCommodityById(commodityId);
        if (commodity == null) {
            log.warn("Unknown commodity id {} in carrier transfer delta. Skipping item.", commodityId);
            return;
        }
        int current = carrier.getHeldQuantity(commodityId);
        int updated = current + delta;
        if (updated < 0) {
            // A hold cannot contain a negative quantity, so this clamps to zero. Reaching this
            // point means the baseline snapshot disagrees with reality — the carrier really did
            // hold more of this commodity than the last Market.json read recorded. Surface it:
            // the player's cure is to reopen the carrier's commodity screen for a fresh snapshot.
            log.warn("Carrier hold for commodity {} would go negative ({} {} {}); clamping to 0. "
                            + "The hold baseline from {} is stale — reopen the carrier commodity "
                            + "screen to refresh it.",
                    commodityId, current, delta < 0 ? "-" : "+", Math.abs(delta),
                    carrier.getHoldSnapshotAt());
        }
        carrier.setHeldQuantity(commodity, updated);
        markDirty();
        notifyListeners();
    }

    /**
     * Manually overrides the held quantity of a single commodity, bypassing snapshot/delta
     * reconciliation.
     *
     * @param commodityId the canonical commodity id
     * @param quantity    the quantity to set; a value {@code <= 0} removes the item
     */
    public synchronized void setHoldQuantityManually(long commodityId, int quantity) {
        Commodity commodity = commodityRegistry.getCommodityById(commodityId);
        if (commodity == null) {
            log.warn("Unknown commodity id {} for manual hold quantity update. Skipping.", commodityId);
            return;
        }
        carrier.setHeldQuantity(commodity, quantity);
        markDirty();
        notifyListeners();
    }

    /**
     * Returns an unmodifiable snapshot of the current hold as commodity id to tons.
     *
     * @return unmodifiable map of commodity id to quantity held
     */
    public synchronized Map<Long, Integer> getHoldSnapshot() {
        Map<Long, Integer> snapshot = new LinkedHashMap<>();
        for (CarrierHoldItem item : carrier.getHold()) {
            if (item.getCommodity() != null) {
                snapshot.put(item.getCommodity().getId(), item.getQuantity());
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * @return free cargo space in tons
     */
    public synchronized int getFreeSpace() {
        return carrier.getFreeSpace();
    }

    /**
     * @return total cargo capacity in tons
     */
    public synchronized int getTotalCapacity() {
        return carrier.getTotalCapacity();
    }

    /**
     * @return the carrier's current system name, or null if unknown
     */
    public synchronized String getCurrentSystem() {
        return carrier.getCurrentSystem();
    }

    /**
     * @return true when {@link #getCurrentSystem()} was set via {@link #setSystemManually(String)}
     *         and is therefore no longer overwritten by journal CarrierJump/CarrierLocation events
     */
    public synchronized boolean isSystemManuallySet() {
        return carrier.isSystemManuallySet();
    }

    /**
     * @return the carrier's callsign (e.g. {@code XXX-000}), or null if not yet known
     */
    public synchronized String getCallsign() {
        return carrier.getCallsign();
    }

    /**
     * @return the carrier's player-assigned name, or null if not yet known
     */
    public synchronized String getName() {
        return carrier.getName();
    }

    /**
     * @return the carrier id (equals its MarketID), or 0 if not yet known
     */
    public synchronized long getCarrierId() {
        return carrier.getCarrierId();
    }

    /**
     * @return the timestamp of the last absolute hold snapshot applied, or null if none yet
     */
    public synchronized Instant getHoldSnapshotAt() {
        return carrier.getHoldSnapshotAt();
    }

    /**
     * @return true once a CarrierStats event or an adopted server record has populated this
     *         manager's state
     */
    public synchronized boolean isKnown() {
        return known;
    }

    /**
     * Installs the HTTP client used to sync this carrier's state with the server and starts
     * (or restarts) the periodic sync loop. Safe to call again with a new client, e.g. when the
     * server base URL changes.
     *
     * @param httpService the HTTP client to use for sync
     */
    public synchronized void setHttpService(CarrierHttpService httpService) {
        this.httpService = httpService;
        startAutoSync();
    }

    private void startAutoSync() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "carrier-sync");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncWithServer();
            } catch (Exception e) {
                log.warn("Carrier sync cycle failed", e);
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    /**
     * Records a local mutation: marks the record for flushing and bumps {@link #revision} so an
     * in-flight sync can detect that it raced with this change.
     */
    private synchronized void markDirty() {
        dirty = true;
        revision++;
    }

    /**
     * One sync cycle.
     *
     * <p>Journal event handlers mutate this manager from the log-reading thread and have a
     * sub-100ms budget, so the object monitor must NOT be held across the HTTP round trip.
     * State is snapshotted under the lock, the network call happens unlocked, and the result is
     * adopted under the lock again.</p>
     */
    private void syncWithServer() {
        FleetCarrierDto pending;
        long sentRevision;
        synchronized (this) {
            if (httpService == null || !known) {
                return;
            }
            sentRevision = revision;
            pending = dirty ? toDto() : null;
        }
        try {
            if (pending != null) {
                flushToServer(pending, sentRevision);
            } else {
                pullFromServer();
            }
        } catch (Exception e) {
            // Never let the sync thread die; retry on the next cycle.
            log.warn("Carrier server sync failed", e);
        }
    }

    /**
     * Pushes local carrier state to the server, performing the HTTP call without holding the
     * object monitor.
     *
     * @param pending      the state snapshotted at the start of this cycle
     * @param sentRevision the revision that snapshot corresponds to
     */
    private void flushToServer(FleetCarrierDto pending, long sentRevision) {
        try {
            FleetCarrierDto updated = httpService.putCarrier(pending);
            settleFlush(updated, sentRevision);
        } catch (CarrierHttpService.VersionConflictException conflict) {
            FleetCarrierDto latest = conflict.getLatest();
            if (latest == null) {
                return;
            }
            // Adopt the server's version number and retry once with our local data.
            FleetCarrierDto retryPayload;
            long retryRevision;
            synchronized (this) {
                carrier.setVersion(latest.getVersion());
                retryRevision = revision;
                retryPayload = toDto();
            }
            try {
                FleetCarrierDto updated = httpService.putCarrier(retryPayload);
                settleFlush(updated, retryRevision);
            } catch (Exception retryFailure) {
                log.warn("Carrier version-conflict retry failed", retryFailure);
                // keep dirty, retry on next cycle
            }
        } catch (Exception e) {
            log.warn("Failed to push carrier state to server", e);
            // keep dirty, retry on next cycle
        }
    }

    /**
     * Adopts the server's response to a flush. {@link #dirty} is cleared only when no local
     * mutation landed while the request was in flight; otherwise the newer local state stays
     * pending and is flushed on the next cycle.
     *
     * @param updated      the DTO the server returned
     * @param sentRevision the revision that was flushed
     */
    private synchronized void settleFlush(FleetCarrierDto updated, long sentRevision) {
        if (revision != sentRevision) {
            // A mutation landed mid-flush: keep only the server's version number so the next
            // PUT does not trip optimistic locking, and leave the record dirty.
            if (updated != null) {
                carrier.setVersion(updated.getVersion());
            }
            return;
        }
        adoptFromDto(updated);
        dirty = false;
    }

    /**
     * Pulls carrier state from the server, adopting it only when it is strictly newer than the
     * local record and nothing local is pending. The HTTP call runs without the monitor held.
     */
    private void pullFromServer() {
        long carrierId;
        synchronized (this) {
            carrierId = carrier.getCarrierId();
        }
        try {
            httpService.getCarrier(carrierId).ifPresent(serverDto -> {
                synchronized (this) {
                    if (serverDto.getVersion() > carrier.getVersion() && !dirty) {
                        adoptFromDto(serverDto);
                        notifyListeners();
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to pull carrier state from server", e);
        }
    }

    private FleetCarrierDto toDto() {
        FleetCarrierDto dto = new FleetCarrierDto();
        dto.setCarrierId(carrier.getCarrierId());
        dto.setCallsign(carrier.getCallsign());
        dto.setName(carrier.getName());
        dto.setCurrentSystem(carrier.getCurrentSystem());
        dto.setSystemManuallySet(carrier.isSystemManuallySet());
        dto.setTotalCapacity(carrier.getTotalCapacity());
        dto.setCargoUsed(carrier.getCargoUsed());
        dto.setFreeSpace(carrier.getFreeSpace());
        dto.setHoldSnapshotAt(carrier.getHoldSnapshotAt());
        dto.setVersion(carrier.getVersion());
        dto.setLastUpdated(carrier.getLastUpdated());
        List<CarrierHoldItemDto> holdDtos = new ArrayList<>();
        for (CarrierHoldItem item : carrier.getHold()) {
            if (item.getCommodity() == null) {
                continue;
            }
            holdDtos.add(new CarrierHoldItemDto(toCommodityDto(item.getCommodity()), item.getQuantity()));
        }
        dto.setHold(holdDtos);
        return dto;
    }

    private void adoptFromDto(FleetCarrierDto dto) {
        if (dto == null) {
            return;
        }
        carrier.setCarrierId(dto.getCarrierId() != null ? dto.getCarrierId() : carrier.getCarrierId());
        carrier.setCallsign(dto.getCallsign());
        carrier.setName(dto.getName());
        carrier.setCurrentSystem(dto.getCurrentSystem());
        carrier.setSystemManuallySet(dto.isSystemManuallySet());
        carrier.setTotalCapacity(dto.getTotalCapacity());
        carrier.setCargoUsed(dto.getCargoUsed());
        carrier.setFreeSpace(dto.getFreeSpace());
        carrier.setHoldSnapshotAt(dto.getHoldSnapshotAt());
        carrier.setVersion(dto.getVersion());
        carrier.setLastUpdated(dto.getLastUpdated());
        carrier.getHold().clear();
        if (dto.getHold() != null) {
            for (CarrierHoldItemDto item : dto.getHold()) {
                if (item.getCommodity() == null || item.getCommodity().getId() == null) {
                    continue;
                }
                Commodity commodity = commodityRegistry.getCommodityById(item.getCommodity().getId());
                if (commodity == null) {
                    log.warn("Unknown commodity id {} in server carrier hold. Skipping item.", item.getCommodity().getId());
                    continue;
                }
                carrier.setHeldQuantity(commodity, item.getQuantity());
            }
        }
        known = true;
    }

    private CommodityDto toCommodityDto(Commodity commodity) {
        return new CommodityDto(
                commodity.getId(),
                commodity.getName(),
                commodity.getNameLocalised(),
                commodity.getCategory(),
                commodity.getCategoryLocalised()
        );
    }
}
