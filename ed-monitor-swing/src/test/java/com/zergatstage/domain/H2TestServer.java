package com.zergatstage.domain;

import org.h2.tools.Server;
import java.sql.SQLException;

public class H2TestServer {
    private static Server server;

    public static void start() {
        try {
            server = Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
            System.out.println("H2 TCP Server started on port 9092");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to start H2 server", e);
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop();
            System.out.println("H2 TCP Server stopped.");
        }
    }

    public static void main(String[] args) {
        start();
        Runtime.getRuntime().addShutdownHook(new Thread(H2TestServer::stop)); // Ensure clean shutdown
    }
}
