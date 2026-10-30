package com.zergatstage.monitor.routes.dto;

/**
 * Describes how a {@link RoutePlanDto} should be built with respect to a personal Fleet
 * Carrier: whether cargo is hauled straight from markets to the construction site, staged
 * through a carrier first, or unloaded from an already-loaded carrier onto the site.
 */
public enum HaulingMode {

    /**
     * Materials are bought at markets and delivered straight to the construction site.
     * This is the original, carrier-free behaviour and remains the default.
     */
    DIRECT,

    /**
     * Materials are bought at markets and hauled to the player's Fleet Carrier so it can be
     * stocked up for a later unload run, rather than delivered directly to the site.
     */
    LOAD_CARRIER,

    /**
     * Materials already sitting in the Fleet Carrier's hold are hauled from the carrier to the
     * construction site.
     */
    UNLOAD_TO_SITE
}
