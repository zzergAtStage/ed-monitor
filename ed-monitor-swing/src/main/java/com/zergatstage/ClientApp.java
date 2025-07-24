package com.zergatstage;

import com.zergatstage.monitor.EliteLogMonitorFrame;
import lombok.extern.log4j.Log4j2;

import javax.swing.*;

/**
 * @author S.Brusentsov
 * date on 12/01/2025
 */


@Log4j2
public class ClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(EliteLogMonitorFrame::new);
    }
}