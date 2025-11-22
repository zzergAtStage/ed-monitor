package com.zergatstage.monitor.routes.ui;

import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.routes.dto.RouteOptimizationRequest;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;
import com.zergatstage.monitor.routes.service.RouteOptimizationService;
import com.zergatstage.monitor.routes.spi.RouteOptimizerDataProvider;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Controller orchestrating data flow between the Swing UI, the data provider, and the
 * greedy optimization service. It keeps blocking operations off the EDT while updating
 * the {@link RouteOptimizerModel} on completion.
 */
public class RouteOptimizerController {

    private final RouteOptimizerModel model;
    private final RouteOptimizerDataProvider dataProvider;
    private final RouteOptimizationService optimizationService;
    private final ExecutorService executorService;
    private Consumer<Throwable> errorHandler = throwable -> {
        throwable.printStackTrace();
    };

    /**
     * Creates a controller with default background execution.
     *
     * @param model                model backing the UI
     * @param dataProvider         facade that supplies construction site and market data
     * @param optimizationService  greedy optimization implementation
     */
    public RouteOptimizerController(RouteOptimizerModel model,
                                    RouteOptimizerDataProvider dataProvider,
                                    RouteOptimizationService optimizationService) {
        this(model, dataProvider, optimizationService,
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "route-optimizer-controller");
                t.setDaemon(true);
                return t;
            }));
    }

    /**
     * Package-private constructor that allows injecting a custom executor (useful for tests).
     */
    RouteOptimizerController(RouteOptimizerModel model,
                             RouteOptimizerDataProvider dataProvider,
                             RouteOptimizationService optimizationService,
                             ExecutorService executorService) {
        this.model = Objects.requireNonNull(model, "model");
        this.dataProvider = Objects.requireNonNull(dataProvider, "dataProvider");
        this.optimizationService = Objects.requireNonNull(optimizationService, "optimizationService");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
    }

    /**
     * Registers an error handler to surface failures (network errors, validation, etc.) to the UI.
     *
     * @param errorHandler consumer invoked on the EDT when an error occurs
     */
    public void setErrorHandler(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler != null ? errorHandler : this.errorHandler;
    }

    /**
     * Loads the specified construction site and candidate markets asynchronously.
     *
     * @param constructionSiteId identifier of the site to load
     */
    public void loadConstructionSite(long constructionSiteId) {
        executorService.submit(() -> {
            try {
                ConstructionSiteDto site = dataProvider.loadConstructionSite(constructionSiteId);
                if (site == null) {
                    throw new IllegalStateException("Construction site " + constructionSiteId + " not found.");
                }
                List<MarketDto> markets = dataProvider.loadCandidateMarkets(constructionSiteId);
                SwingUtilities.invokeLater(() -> {
                    // Ensure the optimization request knows which site we are working with before UI listeners fire
                    model.updateRouteOptimizationRequest(request -> request.setConstructionSiteId(site.getMarketId()));
                    model.setConstructionSite(site);
                    model.setCandidateMarkets(markets);
                    buildRoutePlan();
                });
            } catch (IOException | IllegalStateException e) {
                handleError(e);
            }
        });
    }

    /**
     * Updates the optimization request parameters tracked by the model.
     *
     * @param cargoCapacityTons capacity per run in tons
     * @param maxMarketsPerRun  market leg limit per run
     */
    public void updateOptimizationParameters(double cargoCapacityTons, int maxMarketsPerRun) {
        model.updateRouteOptimizationRequest(request -> {
            request.setCargoCapacityTons(cargoCapacityTons);
            request.setMaxMarketsPerRun(maxMarketsPerRun);
        });
    }

    /**
     * Triggers the greedy optimizer using the current model state. Work is executed off
     * the EDT and the resulting plan is stored back on success.
     */
    public void buildRoutePlan() {
        RouteOptimizationRequest requestSnapshot = model.getRouteOptimizationRequest();
        if (requestSnapshot.getConstructionSiteId() == null) {
            handleError(new IllegalStateException("Select a construction site before planning routes."));
            return;
        }
        executorService.submit(() -> {
            try {
                RoutePlanDto plan = optimizationService.buildRoutePlan(requestSnapshot);
                SwingUtilities.invokeLater(() -> model.setRoutePlan(plan));
            } catch (RuntimeException e) {
                handleError(e);
            }
        });
    }

    /**
     * Stops internal background processing. Should be invoked when the UI is disposed.
     */
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void handleError(Throwable throwable) {
        SwingUtilities.invokeLater(() -> {
            model.setLastError(throwable);
            if (errorHandler != null) {
                errorHandler.accept(throwable);
            }
        });
    }
}
