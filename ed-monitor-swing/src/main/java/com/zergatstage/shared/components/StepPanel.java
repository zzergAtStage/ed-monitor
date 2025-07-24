package com.zergatstage.shared.components;

public interface StepPanel {
    /**
     * Called before moving to the next step.
     * @return true if the input is valid and user can proceed
     */
    boolean isValidStep();

    /**
     * Called to extract the step-specific result
     * @return an Object or a structured DTO containing step result
     */
    Object getStepData();

    /**
     * Returns the title of this step
     */
    String getStepTitle();
}

