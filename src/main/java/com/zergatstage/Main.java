package com.zergatstage;

import com.zergatstage.monitor.EliteLogMonitorFrame;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;

/**
 * @author S.Brusentsov
 * date on 12/01/2025
 */

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        // Disable headless mode if a display is available
        System.setProperty("java.awt.headless", "false");

        SpringApplication.run(Main.class, args);

        // Ensure GUI is started on the Event Dispatch Thread
        SwingUtilities.invokeLater(EliteLogMonitorFrame::new);
    }
}