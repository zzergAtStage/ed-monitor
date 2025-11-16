package com.zergatstage.monitor.routes.ui;

import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.routes.dto.RouteOptimizationRequest;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * UI-friendly model that stores the state required by the Route Optimizer view.
 * It exposes observable properties so Swing components can react to updates
 * without coupling directly to the optimization service.
 */
public class RouteOptimizerModel {

    public static final String PROPERTY_CONSTRUCTION_SITE = "constructionSite";
    public static final String PROPERTY_CANDIDATE_MARKETS = "candidateMarkets";
    public static final String PROPERTY_REQUEST = "routeOptimizationRequest";
    public static final String PROPERTY_ROUTE_PLAN = "routePlan";
    public static final String PROPERTY_ERROR = "lastError";

    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private ConstructionSiteDto constructionSite;
    private List<MarketDto> candidateMarkets = Collections.emptyList();
    private RouteOptimizationRequest request = new RouteOptimizationRequest();
    private RoutePlanDto routePlan = new RoutePlanDto();
    private Throwable lastError;

    /**
     * @return immutable snapshot of the currently loaded construction site
     */
    public synchronized ConstructionSiteDto getConstructionSite() {
        return constructionSite;
    }

    /**
     * Sets the construction site context for this model.
     *
     * @param constructionSite the selected site, or {@code null} when clearing selection
     */
    public synchronized void setConstructionSite(ConstructionSiteDto constructionSite) {
        ConstructionSiteDto old = this.constructionSite;
        this.constructionSite = constructionSite;
        firePropertyChange(PROPERTY_CONSTRUCTION_SITE, old, constructionSite);
    }

    /**
     * @return immutable list of candidate markets for the current site
     */
    public synchronized List<MarketDto> getCandidateMarkets() {
        return candidateMarkets;
    }

    /**
     * Replaces the known candidate markets backing the optimizer.
     *
     * @param markets markets to expose to the UI
     */
    public synchronized void setCandidateMarkets(List<MarketDto> markets) {
        List<MarketDto> old = this.candidateMarkets;
        this.candidateMarkets = markets == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(markets));
        firePropertyChange(PROPERTY_CANDIDATE_MARKETS, old, this.candidateMarkets);
    }

    /**
     * @return copy of the current optimization request parameters
     */
    public synchronized RouteOptimizationRequest getRouteOptimizationRequest() {
        return copyRequest(this.request);
    }

    /**
     * Replaces the stored optimization request with the provided one.
     *
     * @param request new request; must not be {@code null}
     */
    public synchronized void setRouteOptimizationRequest(RouteOptimizationRequest request) {
        Objects.requireNonNull(request, "request");
        RouteOptimizationRequest old = copyRequest(this.request);
        this.request = copyRequest(request);
        firePropertyChange(PROPERTY_REQUEST, old, copyRequest(this.request));
    }

    /**
     * Convenience method to mutate the stored request atomically while still firing
     * a property change event.
     *
     * @param mutator lambda that updates the mutable copy of the request
     */
    public synchronized void updateRouteOptimizationRequest(java.util.function.Consumer<RouteOptimizationRequest> mutator) {
        RouteOptimizationRequest mutable = copyRequest(this.request);
        mutator.accept(mutable);
        setRouteOptimizationRequest(mutable);
    }

    /**
     * @return last computed plan (never {@code null})
     */
    public synchronized RoutePlanDto getRoutePlan() {
        return routePlan;
    }

    /**
     * Stores the latest plan produced by the optimizer.
     *
     * @param routePlan plan to expose to the UI
     */
    public synchronized void setRoutePlan(RoutePlanDto routePlan) {
        RoutePlanDto old = this.routePlan;
        this.routePlan = routePlan == null ? new RoutePlanDto() : routePlan;
        firePropertyChange(PROPERTY_ROUTE_PLAN, old, this.routePlan);
    }

    /**
     * @return the last error produced while loading data or computing a plan
     */
    public synchronized Throwable getLastError() {
        return lastError;
    }

    /**
     * Stores the most recent error so the UI can surface it to the commander.
     *
     * @param error throwable or {@code null} to clear
     */
    public synchronized void setLastError(Throwable error) {
        Throwable old = this.lastError;
        this.lastError = error;
        firePropertyChange(PROPERTY_ERROR, old, error);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    private void firePropertyChange(String property, Object oldValue, Object newValue) {
        if (SwingUtilities.isEventDispatchThread()) {
            changeSupport.firePropertyChange(property, oldValue, newValue);
        } else {
            SwingUtilities.invokeLater(
                () -> changeSupport.firePropertyChange(property, oldValue, newValue)
            );
        }
    }

    private RouteOptimizationRequest copyRequest(RouteOptimizationRequest source) {
        if (source == null) {
            return new RouteOptimizationRequest();
        }
        return new RouteOptimizationRequest(
            source.getConstructionSiteId(),
            source.getCargoCapacityTons(),
            source.getMaxMarketsPerRun()
        );
    }
}
