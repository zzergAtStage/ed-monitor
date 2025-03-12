package com.zergatstage.services;


/**
 * Listener interface for receiving updates when construction site data changes.
 */
public interface ConstructionSiteUpdateListener {
    /**
     * Invoked when the construction site data has been updated.
     */
    void onConstructionSiteUpdated();
}