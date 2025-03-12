package com.zergatstage;

import com.zergatstage.monitor.EliteLogMonitorFrame;
import lombok.extern.log4j.Log4j2;
import org.h2.tools.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;
import java.sql.SQLException;

/**
 * @author S.Brusentsov
 * date on 12/01/2025
 */

@SpringBootApplication
@Log4j2
public class Main {
    public static void main(String[] args) {
        // Disable headless mode if a display is available
        System.setProperty("java.awt.headless", "false");

        SpringApplication.run(Main.class, args);
        // Start H2 Console
        try {
            Server webServer = Server.createWebServer("-webPort", "8082", "-tcpAllowOthers").start();
            System.out.println("H2 Console started at: http://localhost:8082");
        } catch (SQLException e) {
            log.error(e.getMessage());
        }
        // Ensure GUI is started on the Event Dispatch Thread
        SwingUtilities.invokeLater(EliteLogMonitorFrame::new);
    }
}