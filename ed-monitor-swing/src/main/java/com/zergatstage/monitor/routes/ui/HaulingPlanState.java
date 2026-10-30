package com.zergatstage.monitor.routes.ui;

import com.zergatstage.monitor.routes.dto.DeliveryRunDto;
import com.zergatstage.monitor.routes.dto.PurchaseDto;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;
import com.zergatstage.monitor.routes.dto.RunLegDto;
import com.zergatstage.monitor.service.CommodityRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory execution state for a {@link RoutePlanDto} being worked through leg by leg.
 *
 * <p>This state is deliberately <strong>not</strong> persisted. On an application restart the
 * hauling plan is rebuilt from scratch from live server state (construction site outstanding
 * demand plus the ship/carrier hold), so there is nothing durable to reload, and reloading a
 * stale in-memory execution position across a restart would be actively wrong.</p>
 *
 * <p>The purpose of this class is to let {@link com.zergatstage.monitor.component.HaulingPanel}
 * track progress against the <em>current leg only</em> as the commander buys/sells cargo in game,
 * without triggering a full route re-optimization on every purchase. A full re-optimization would
 * reshuffle market ordering mid-shopping, which is explicitly unwanted; instead this class simply
 * credits purchases against the current leg's planned lines until the leg is complete, at which
 * point the caller is expected to pin completed legs and request a fresh plan for the remaining
 * tail via the normal {@link RouteOptimizerController}.</p>
 *
 * <p>Not thread-safe beyond synchronizing its own methods: all mutator/accessor methods are
 * {@code synchronized} so it is safe to call from the EDT (where all callers in this codebase are
 * expected to live, since {@link com.zergatstage.monitor.service.BaseManager#notifyListeners()}
 * already marshals manager callbacks there), but no additional locking guarantees are made.</p>
 */
@Slf4j
public class HaulingPlanState {

    private final CommodityRegistry commodityRegistry;

    private RoutePlanDto plan = new RoutePlanDto();
    private int currentRunIndex;
    private int currentLegIndex;

    /** Commodity id -> tons already acquired and credited against the current leg's plan. */
    private final Map<Long, Integer> purchasedInCurrentLeg = new LinkedHashMap<>();

    /** Commodity id -> tons the current leg still calls for, resolved when the leg becomes current. */
    private Map<Long, Integer> currentLegPlannedTons = Map.of();

    /** {@code "runIndex:legIndex"} keys for legs considered complete/pinned within this plan. */
    private final Set<String> completedLegKeys = new LinkedHashSet<>();

    /**
     * Creates a new execution-state tracker.
     *
     * @param commodityRegistry registry used to resolve a {@link PurchaseDto}'s material name back
     *                          to a canonical commodity id, so purchases (keyed by commodity id via
     *                          {@link com.zergatstage.monitor.service.managers.CargoInventoryManager})
     *                          can be matched against planned lines (keyed by display name)
     */
    public HaulingPlanState(CommodityRegistry commodityRegistry) {
        this.commodityRegistry = commodityRegistry;
    }

    /**
     * Resets execution state to the start of a newly computed plan. Called whenever the
     * commander loads a site or explicitly recalculates; never merges with prior progress, since a
     * fresh plan already reflects everything delivered/onboard up to that point.
     *
     * @param plan the newly computed plan; {@code null} is treated as an empty plan
     */
    public synchronized void adoptPlan(RoutePlanDto plan) {
        this.plan = plan != null ? plan : new RoutePlanDto();
        this.currentRunIndex = 0;
        this.currentLegIndex = 0;
        this.purchasedInCurrentLeg.clear();
        this.completedLegKeys.clear();
        recomputeCurrentLegPlan();
    }

    /**
     * Diffs two onboard-commodity snapshots (as returned by
     * {@link com.zergatstage.monitor.service.managers.CargoInventoryManager#getOnboardCommoditySnapshot()})
     * and credits positive deltas against the current leg's outstanding lines.
     *
     * <p>Only positive deltas (net gains) count as acquiring planned cargo — a sale (negative
     * delta) never credits the leg, and never reduces a prior credit either, so partially selling
     * back an over-purchase cannot un-complete a leg the commander already finished. A commodity
     * that gained tons but is not part of the current leg's plan is ignored rather than corrupting
     * state. Callers are responsible for never calling this with a snapshot taken while the ship's
     * cargo state is unknown (see {@code Ship#isCargoStateKnown()}); an unknown snapshot is not
     * "empty cargo" and must not be diffed as such.</p>
     *
     * @param before onboard snapshot prior to the change (commodity id -> tons)
     * @param after  onboard snapshot after the change (commodity id -> tons)
     */
    public synchronized void applyCargoDelta(Map<Long, Integer> before, Map<Long, Integer> after) {
        if (after == null || after.isEmpty() && (before == null || before.isEmpty())) {
            return;
        }
        if (currentLegPlannedTons.isEmpty()) {
            return;
        }
        Map<Long, Integer> safeBefore = before != null ? before : Map.of();
        for (Map.Entry<Long, Integer> entry : after.entrySet()) {
            long commodityId = entry.getKey();
            int newQty = entry.getValue() != null ? entry.getValue() : 0;
            int oldQty = safeBefore.getOrDefault(commodityId, 0);
            int delta = newQty - oldQty;
            if (delta <= 0) {
                // Sale or no change: never credits the leg.
                continue;
            }
            Integer planned = currentLegPlannedTons.get(commodityId);
            if (planned == null) {
                // Bought something outside this leg's plan; ignore rather than corrupt state.
                continue;
            }
            int alreadyCredited = purchasedInCurrentLeg.getOrDefault(commodityId, 0);
            int outstanding = Math.max(0, planned - alreadyCredited);
            int credited = Math.min(delta, outstanding);
            if (credited > 0) {
                purchasedInCurrentLeg.merge(commodityId, credited, Integer::sum);
            }
        }
    }

    /**
     * @return {@code true} when every planned line of the current leg has been fully credited by
     *         {@link #applyCargoDelta}; {@code false} if the plan has no current leg (exhausted or
     *         never adopted) or any line remains outstanding
     */
    public synchronized boolean isCurrentLegComplete() {
        if (currentLegPlannedTons.isEmpty()) {
            return false;
        }
        for (Map.Entry<Long, Integer> entry : currentLegPlannedTons.entrySet()) {
            int purchased = purchasedInCurrentLeg.getOrDefault(entry.getKey(), 0);
            if (purchased < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pins the current leg as completed and moves execution to the next leg (or the next run's
     * first leg, or past the end of the plan if this was the final leg). Resets the
     * purchased-against-current-leg counters for the new current leg.
     */
    public synchronized void advanceLeg() {
        completedLegKeys.add(legKey(currentRunIndex, currentLegIndex));
        purchasedInCurrentLeg.clear();

        var runs = plan.getRuns();
        if (currentRunIndex >= runs.size()) {
            currentLegPlannedTons = Map.of();
            return;
        }
        DeliveryRunDto run = runs.get(currentRunIndex);
        if (currentLegIndex + 1 < run.getLegs().size()) {
            currentLegIndex++;
        } else {
            currentRunIndex++;
            currentLegIndex = 0;
        }
        recomputeCurrentLegPlan();
    }

    /**
     * @return the leg currently being worked, or {@code null} if the plan is empty or exhausted
     */
    public synchronized RunLegDto getCurrentLeg() {
        var runs = plan.getRuns();
        if (currentRunIndex < 0 || currentRunIndex >= runs.size()) {
            return null;
        }
        var legs = runs.get(currentRunIndex).getLegs();
        if (currentLegIndex < 0 || currentLegIndex >= legs.size()) {
            return null;
        }
        return legs.get(currentLegIndex);
    }

    /**
     * @return zero-based index of the run currently being worked
     */
    public synchronized int getCurrentRunIndex() {
        return currentRunIndex;
    }

    /**
     * @return zero-based index, within the current run, of the leg currently being worked
     */
    public synchronized int getCurrentLegIndex() {
        return currentLegIndex;
    }

    /**
     * @return {@code true} once execution has advanced past the last leg of the last run
     */
    public synchronized boolean isPlanExhausted() {
        return getCurrentLeg() == null;
    }

    /**
     * @return an immutable snapshot of {@code "runIndex:legIndex"} keys for legs already pinned as
     *         complete in this plan
     */
    public synchronized Set<String> getCompletedLegKeys() {
        return Set.copyOf(completedLegKeys);
    }

    /**
     * Renders the current leg's outstanding lines: for each planned commodity, how many tons are
     * still needed (planned minus already credited). Commodities that are fully satisfied are
     * omitted.
     *
     * @return commodity id -> remaining tons still outstanding for the current leg
     */
    public synchronized Map<Long, Integer> getRemainingForCurrentLeg() {
        Map<Long, Integer> remaining = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : currentLegPlannedTons.entrySet()) {
            int purchased = purchasedInCurrentLeg.getOrDefault(entry.getKey(), 0);
            int outstanding = entry.getValue() - purchased;
            if (outstanding > 0) {
                remaining.put(entry.getKey(), outstanding);
            }
        }
        return remaining;
    }

    /**
     * @param commodityId canonical commodity id
     * @return tons of {@code commodityId} still outstanding for the current leg, or 0 if it is not
     *         part of the current leg's plan or is already fully satisfied
     */
    public synchronized int getRemainingForCurrentLeg(long commodityId) {
        Integer planned = currentLegPlannedTons.get(commodityId);
        if (planned == null) {
            return 0;
        }
        int purchased = purchasedInCurrentLeg.getOrDefault(commodityId, 0);
        return Math.max(0, planned - purchased);
    }

    /**
     * @return the plan this state is tracking execution against; never {@code null}
     */
    public synchronized RoutePlanDto getPlan() {
        return plan;
    }

    private void recomputeCurrentLegPlan() {
        RunLegDto leg = getCurrentLeg();
        if (leg == null) {
            currentLegPlannedTons = Map.of();
            return;
        }
        Map<Long, Integer> planned = new LinkedHashMap<>();
        for (PurchaseDto purchase : leg.getPurchases()) {
            long commodityId = resolveCommodityId(purchase);
            if (commodityId < 0) {
                // Dropping the line silently would make this leg look complete without the
                // commodity ever being bought, quietly advancing the player past it. The plan
                // is still usable, so don't fail — but make the gap visible.
                log.warn("Cannot resolve commodity id for planned purchase '{}'; it will not be "
                                + "tracked for leg completion.", purchase.getMaterialName());
                continue;
            }
            int tons = (int) Math.round(purchase.getAmountTons());
            planned.merge(commodityId, tons, Integer::sum);
        }
        currentLegPlannedTons = planned;
    }

    private long resolveCommodityId(PurchaseDto purchase) {
        if (commodityRegistry == null || purchase.getMaterialName() == null) {
            return -1;
        }
        return commodityRegistry.findCommodityId(purchase.getMaterialName(), purchase.getMaterialName());
    }

    private static String legKey(int runIndex, int legIndex) {
        return runIndex + ":" + legIndex;
    }
}
