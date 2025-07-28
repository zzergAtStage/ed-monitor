package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.MonitorController;
import com.zergatstage.monitor.service.JournalLogMonitor;


import javax.swing.*;

interface UIComponentFactory {
    JPanel createLogTab(JournalLogMonitor logService);
    //JPanel createRunnerPanel(StatusDisplayManager statusManager, MonitorController controller);
    JMenuBar createMenuBar(MonitorController controller, JFrame parentFrame);
}
