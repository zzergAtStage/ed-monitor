package com.zergatstage.monitor.handlers;

public class DefaultExitHandler implements ExitHandler {
    @Override
    public void exit(int status) {
        System.exit(status);
    }
}
