package com.zergatstage.monitor.service;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

abstract class BaseManager implements Observable {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void notifyListeners() {
        SwingUtilities.invokeLater(() ->
                listeners.forEach(Runnable::run));
    }
}

