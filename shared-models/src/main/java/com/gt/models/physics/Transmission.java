package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Transmission simulation with realistic gear ratios and shifting behavior
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transmission {
    private float[] gearRatios; // Gear ratios [reverse, 1st, 2nd, 3rd, ...]
    private float finalDriveRatio; // Final drive/differential ratio
    private float efficiency; // Power transmission efficiency (0.0 to 1.0)
    private String transmissionType; // "manual", "automatic", "cvt", "dual_clutch"
    private boolean isAutomatic;
    private float shiftTime; // Time to complete a gear change in seconds
    private float currentShiftProgress; // Current shift progress (0.0 to 1.0)
    private boolean isShifting;
    private int targetGear;
    private float clutchEngagement; // 0.0 = disengaged, 1.0 = fully engaged
    private int gearCount;

    public Transmission() {
        // Default 6-speed manual transmission ratios
        this.gearRatios = new float[]{
            -3.5f,  // Reverse
             0.0f,  // Neutral (not used in array, gear 0)
             3.83f, // 1st
             2.36f, // 2nd
             1.68f, // 3rd
             1.31f, // 4th
             1.00f, // 5th
             0.79f  // 6th
        };
        this.finalDriveRatio = 3.73f;
        this.efficiency = 0.95f; // 95% efficiency
        this.transmissionType = "manual";
        this.isAutomatic = false;
        this.shiftTime = 0.3f; // 300ms shift time
        this.currentShiftProgress = 0.0f;
        this.isShifting = false;
        this.targetGear = 1;
        this.clutchEngagement = 1.0f;
        this.gearCount = gearRatios.length - 2; // Exclude reverse and neutral
    }

    public Transmission(String type, float[] customGearRatios) {
        this();
        this.transmissionType = type;
        this.isAutomatic = !type.equals("manual");

        if (customGearRatios != null && customGearRatios.length > 0) {
            this.gearRatios = customGearRatios;
            this.gearCount = customGearRatios.length - 2;
        }

        // Adjust shift characteristics based on type
        switch (type.toLowerCase()) {
            case "automatic":
                this.shiftTime = 0.5f;
                this.efficiency = 0.92f;
                break;
            case "cvt":
                this.shiftTime = 0.0f; // Continuous variation
                this.efficiency = 0.88f;
                break;
            case "dual_clutch":
                this.shiftTime = 0.15f;
                this.efficiency = 0.97f;
                break;
            default: // manual
                this.shiftTime = 0.3f;
                this.efficiency = 0.95f;
        }
    }

    // Getters and Setters
    public float[] getGearRatios() {
        return gearRatios;
    }

    public void setGearRatios(float[] gearRatios) {
        this.gearRatios = gearRatios;
        this.gearCount = gearRatios.length - 2; // Exclude reverse and neutral
    }

    public float getFinalDriveRatio() {
        return finalDriveRatio;
    }

    public void setFinalDriveRatio(float finalDriveRatio) {
        this.finalDriveRatio = finalDriveRatio;
    }

    public float getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(float efficiency) {
        this.efficiency = Math.max(0.5f, Math.min(1.0f, efficiency));
    }

    public String getTransmissionType() {
        return transmissionType;
    }

    public void setTransmissionType(String transmissionType) {
        this.transmissionType = transmissionType;
        this.isAutomatic = !transmissionType.equals("manual");
    }

    public boolean isAutomatic() {
        return isAutomatic;
    }

    public float getShiftTime() {
        return shiftTime;
    }

    public void setShiftTime(float shiftTime) {
        this.shiftTime = Math.max(0.0f, shiftTime);
    }

    public boolean isShifting() {
        return isShifting;
    }

    public float getClutchEngagement() {
        return clutchEngagement;
    }

    public void setClutchEngagement(float clutchEngagement) {
        this.clutchEngagement = Math.max(0.0f, Math.min(1.0f, clutchEngagement));
    }

    public int getGearCount() {
        return gearCount;
    }

    /**
     * Get gear ratio for specific gear
     * @param gear Gear number (-1 = reverse, 0 = neutral, 1+ = forward gears)
     * @return Gear ratio
     */
    public float getGearRatio(int gear) {
        if (gear == 0) {
            return 0.0f; // Neutral
        }

        int arrayIndex;
        if (gear == -1) {
            arrayIndex = 0; // Reverse
        } else if (gear > 0 && gear <= gearCount) {
            arrayIndex = gear + 1; // Skip reverse and neutral positions
        } else {
            return 0.0f; // Invalid gear
        }

        return gearRatios[arrayIndex];
    }

    /**
     * Calculate optimal speed for given gear and RPM
     * @param gear Current gear
     * @param rpm Engine RPM
     * @return Optimal speed in km/h
     */
    public float getOptimalSpeedForGear(int gear, float rpm) {
        if (gear == 0) return 0.0f;

        float gearRatio = getGearRatio(gear);
        if (gearRatio == 0.0f) return 0.0f;

        // Simplified calculation assuming standard wheel circumference
        float wheelCircumference = 2.0f; // meters (approximation)
        float wheelRpm = rpm / (gearRatio * finalDriveRatio);
        float speedMs = (wheelRpm * wheelCircumference) / 60.0f; // m/s
        return speedMs * 3.6f; // Convert to km/h
    }

    /**
     * Determine best gear for current speed and RPM
     * @param currentSpeed Speed in km/h
     * @param targetRpm Target engine RPM
     * @return Optimal gear number
     */
    public int getOptimalGear(float currentSpeed, float targetRpm) {
        if (currentSpeed < 5.0f) {
            return 1; // Always use 1st gear at very low speeds
        }

        int bestGear = 1;
        float bestRpmDifference = Float.MAX_VALUE;

        for (int gear = 1; gear <= gearCount; gear++) {
            float optimalSpeed = getOptimalSpeedForGear(gear, targetRpm);
            float speedDifference = Math.abs(currentSpeed - optimalSpeed);

            if (speedDifference < bestRpmDifference) {
                bestRpmDifference = speedDifference;
                bestGear = gear;
            }
        }

        return bestGear;
    }

    /**
     * Start a gear change
     * @param newGear Target gear
     */
    public void startShift(int newGear) {
        if (newGear >= -1 && newGear <= gearCount && !isShifting) {
            this.targetGear = newGear;
            this.isShifting = true;
            this.currentShiftProgress = 0.0f;

            if (transmissionType.equals("cvt")) {
                // CVT shifts instantly
                completeShift();
            }
        }
    }

    /**
     * Update transmission state
     * @param deltaTime Time step in seconds
     * @param currentGear Current gear
     * @param rpm Current engine RPM
     * @param throttleInput Throttle position
     * @param speed Current vehicle speed
     * @return New gear after update
     */
    public int update(float deltaTime, int currentGear, float rpm, float throttleInput, float speed) {
        // Handle ongoing gear shifts
        if (isShifting && shiftTime > 0) {
            currentShiftProgress += deltaTime / shiftTime;

            // Update clutch engagement during shift
            if (currentShiftProgress < 0.5f) {
                // Disengage clutch during first half of shift
                clutchEngagement = 1.0f - (currentShiftProgress * 2.0f);
            } else {
                // Re-engage clutch during second half of shift
                clutchEngagement = (currentShiftProgress - 0.5f) * 2.0f;
            }

            if (currentShiftProgress >= 1.0f) {
                completeShift();
                return targetGear;
            }

            return currentGear; // Still shifting, don't change gear yet
        }

        // Automatic transmission logic
        if (isAutomatic) {
            int newGear = calculateAutomaticGear(currentGear, rpm, throttleInput, speed);
            if (newGear != currentGear) {
                startShift(newGear);
            }
        }

        return currentGear;
    }

    private void completeShift() {
        this.isShifting = false;
        this.currentShiftProgress = 0.0f;
        this.clutchEngagement = 1.0f;
    }

    private int calculateAutomaticGear(int currentGear, float rpm, float throttleInput, float speed) {
        // Automatic transmission shift logic
        float shiftUpRpm = 6000.0f - (throttleInput * 1000.0f); // Shift earlier under light throttle
        float shiftDownRpm = 2500.0f + (throttleInput * 500.0f); // Shift later under heavy throttle

        // Prevent shifting if already at limits
        if (currentGear >= gearCount && rpm > shiftUpRpm) {
            return currentGear; // Can't shift up further
        }

        if (currentGear <= 1 && rpm < shiftDownRpm) {
            return currentGear; // Can't shift down further
        }

        // Shift up if RPM is too high
        if (rpm > shiftUpRpm && currentGear < gearCount) {
            return currentGear + 1;
        }

        // Shift down if RPM is too low (but not in 1st gear)
        if (rpm < shiftDownRpm && currentGear > 1) {
            return currentGear - 1;
        }

        // Kickdown logic - downshift for better acceleration
        if (throttleInput > 0.8f && currentGear > 1) {
            float nextGearRpm = rpm * (getGearRatio(currentGear - 1) / getGearRatio(currentGear));
            if (nextGearRpm < 7000.0f) { // Don't kickdown if it would over-rev
                return currentGear - 1;
            }
        }

        return currentGear; // No gear change needed
    }

    /**
     * Calculate power loss due to transmission efficiency and current state
     * @return Power multiplier (0.0 to 1.0)
     */
    public float getPowerMultiplier() {
        float baseEfficiency = efficiency;

        // Reduce efficiency during gear shifts
        if (isShifting) {
            baseEfficiency *= (0.5f + (clutchEngagement * 0.5f));
        }

        // Reduce efficiency if clutch is slipping (manual transmission)
        if (!isAutomatic && clutchEngagement < 1.0f) {
            baseEfficiency *= clutchEngagement;
        }

        return baseEfficiency;
    }

    /**
     * Check if transmission can handle the given torque
     * @param torque Input torque in Nm
     * @return True if transmission can handle the torque
     */
    public boolean canHandleTorque(float torque) {
        // Different transmission types have different torque limits
        float maxTorque;
        switch (transmissionType.toLowerCase()) {
            case "manual":
                maxTorque = 600.0f; // Nm
                break;
            case "automatic":
                maxTorque = 500.0f; // Nm
                break;
            case "cvt":
                maxTorque = 400.0f; // Nm
                break;
            case "dual_clutch":
                maxTorque = 700.0f; // Nm
                break;
            default:
                maxTorque = 500.0f; // Nm
        }

        return torque <= maxTorque;
    }

    /**
     * Get transmission temperature based on usage
     * @param load Current load factor (0.0 to 1.0)
     * @return Temperature in Celsius
     */
    public float getTemperature(float load) {
        // Base temperature + load-based increase
        float baseTemp = 70.0f;
        float loadTemp = load * 50.0f; // Up to 50°C increase under full load

        // Automatic transmissions run hotter
        if (isAutomatic) {
            baseTemp += 10.0f;
        }

        return baseTemp + loadTemp;
    }
}
