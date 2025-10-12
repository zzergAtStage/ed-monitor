package com.zergatstage.monitor.service.managers;

import com.zergatstage.monitor.service.BaseManager;
import lombok.Setter;
import org.json.JSONObject;

public class AsteroidManager extends BaseManager {
    @Setter
    private String selectedMaterial = "Tritium"; // Default material
    double proportion = 0.0;

    public void updateProspectingLabel(JSONObject event) {

        try {
            if (event.has("Materials")) {
                var materials = event.getJSONArray("Materials");
                for (int i = 0; i < materials.length(); i++) {
                    var material = materials.getJSONObject(i);
                    if (selectedMaterial.equalsIgnoreCase(material.getString("Name"))) {
                        proportion = material.getDouble("Proportion");
                        break;
                    }
                    proportion = 0.0;
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing asteroid data: " + e.getMessage());
        }
        this.notifyListeners();
    }

    public double getProportionForSelectedMaterial() {
        return proportion;
    }
}
