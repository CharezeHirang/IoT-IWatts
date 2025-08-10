package com.example.sampleiwatts;

public class AlertSettings {
    final Double powerThresholdWatts;
    final Double budgetPhp;
    final boolean alertVoltageFluctuations;
    final boolean alertSystemUpdates;
    final boolean pushEnabled;

    AlertSettings(Double powerThresholdWatts,
                  Double budgetPhp,
                  boolean alertVoltageFluctuations,
                  boolean alertSystemUpdates,
                  boolean pushEnabled) {
        this.powerThresholdWatts = powerThresholdWatts;
        this.budgetPhp = budgetPhp;
        this.alertVoltageFluctuations = alertVoltageFluctuations;
        this.alertSystemUpdates = alertSystemUpdates;
        this.pushEnabled = pushEnabled;
    }
}
