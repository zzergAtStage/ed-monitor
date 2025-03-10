package com.zergatstage.tools;

import org.h2.tools.*;

import java.sql.SQLException;

public class H2ConsoleLauncher {
    public static void main(String[] args) {
        try {
            // Start the H2 web console on port 8082
            Server webServer = Server.createWebServer("-webPort", "8082", "-tcpAllowOthers").start();
            System.out.println("H2 Console started at: http://localhost:8082");
            // Optionally, keep the application running
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
