package com.zergatstage.monitor.service;

interface Observable {
    void addListener(Runnable listener);

    void removeListener(Runnable listener);

    void notifyListeners();
}