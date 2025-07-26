package com.zergatstage.monitor.component;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.AsteroidManager;
import com.zergatstage.monitor.service.DroneManager;

import javax.swing.*;
import java.awt.*;

public class DroneProvisionerPanel extends JPanel{

    private final JLabel droneLaunchedLabel;
    private final JLabel asteroidProspectedLabel;
    private final DroneManager droneManager;
    private final AsteroidManager asteroidManager;

    public DroneProvisionerPanel() {
        this.droneManager = DefaultManagerFactory.getInstance().getDroneManager();
        this.asteroidManager = DefaultManagerFactory.getInstance().getAsteroidManager();
        setLayout(new GridLayout(2, 1));
        this.droneLaunchedLabel = new JLabel("Drone Launched: No");
        this.asteroidProspectedLabel = new JLabel("Asteroid Prospected: No");
    }


}

