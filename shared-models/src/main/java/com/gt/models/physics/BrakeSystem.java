package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Advanced brake system with temperature modeling, fade, and ABS simulation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrakeSystem {
    private float frontDiscDiameter; // Front disc diameter in mm
    private float rearDiscDiameter; // Rear disc diameter in mm
    private float frontDiscThickness; // Front disc thickness in mm
    private float rearDiscThickness; // Rear disc thickness in mm
    private float frontPadArea; // Front pad contact area in cm²
    private float rearPadArea; // Rear pad contact area in cm²
    private String frontPadCompound; // Brake pad compound type
    private String rearPadCompound; // Brake pad compound type

    // Temperature and fade modeling
    private float frontDiscTemperature; // Current front disc temperature °C
    private float rearDiscTemperature; // Current rear disc temperature °C
    private float maxTemperature; // Maximum safe operating temperature
    private float fadeThreshold; // Temperature at which fade begins
    private float frontPadWear; // Front pad wear level (0.0-1.0)
    private float rearPadWear; // Rear pad wear level (0.0-1.0)

    // Performance characteristics
    private float brakeBias; // Brake bias (0.0 = all rear, 1.0 = all front)
    private float maxBrakeForce; // Maximum brake force in Newtons
    private float brakePedalSensitivity; // Pedal input sensitivity
    private boolean absEnabled; // Anti-lock braking system
    private boolean ebdEnabled; // Electronic brake distribution
    private float absThreshold; // Wheel slip threshold for ABS activation
    private float absReleaseRate; // ABS pressure release rate

    // Cooling and heat dissipation
    private float ambientTemperature; // Ambient air temperature
    private float airflowCooling; // Cooling effect of airflow
    private float discCoolingRate; // Natural cooling rate per second
    private boolean ventilatedDiscs; // Ventilated disc brake type

    public BrakeSystem() {
        this.frontDiscDiameter = 330.0f; // mm
        this.rearDiscDiameter = 310.0f; // mm
        this.frontDiscThickness = 32.0f; // mm
        this.rearDiscThickness = 28.0f; // mm
        this.frontPadArea = 45.0f; // cm²
        this.rearPadArea = 35.0f; // cm²
        this.frontPadCompound = "semi_metallic";
        this.rearPadCompound = "semi_metallic";

        this.frontDiscTemperature = 25.0f; // °C
        this.rearDiscTemperature = 25.0f; // °C
        this.maxTemperature = 800.0f; // °C
        this.fadeThreshold = 400.0f; // °C
        this.frontPadWear = 0.0f;
        this.rearPadWear = 0.0f;

        this.brakeBias = 0.65f; // 65% front bias
        this.maxBrakeForce = 12000.0f; // N
        this.brakePedalSensitivity = 1.0f;
        this.absEnabled = true;
        this.ebdEnabled = true;
        this.absThreshold = 0.15f; // 15% slip threshold
        this.absReleaseRate = 0.8f;

        this.ambientTemperature = 25.0f; // °C
        this.airflowCooling = 1.0f;
        this.discCoolingRate = 2.0f; // °C per second
        this.ventilatedDiscs = true;
    }

    /**
     * Calculate brake force considering temperature, wear, and fade
     * @param pedalInput Brake pedal input (0.0 to 1.0)
     * @param wheelSpeeds Array of wheel speeds [FL, FR, RL, RR]
     * @param deltaTime Time step in seconds
     * @return Array of brake forces for each wheel [FL, FR, RL, RR]
     */
    public float[] calculateBrakeForces(float pedalInput, float[] wheelSpeeds, float deltaTime) {
        float[] brakeForces = new float[4];

        if (pedalInput <= 0.0f) {
            return brakeForces; // No braking
        }

        // Calculate base brake force
        float baseFrontForce = maxBrakeForce * brakeBias * pedalInput * brakePedalSensitivity;
        float baseRearForce = maxBrakeForce * (1.0f - brakeBias) * pedalInput * brakePedalSensitivity;

        // Apply temperature fade to front brakes
        float frontFadeMultiplier = calculateFadeMultiplier(frontDiscTemperature);
        float frontForce = baseFrontForce * frontFadeMultiplier;

        // Apply temperature fade to rear brakes
        float rearFadeMultiplier = calculateFadeMultiplier(rearDiscTemperature);
        float rearForce = baseRearForce * rearFadeMultiplier;

        // Apply pad wear reduction
        frontForce *= (1.0f - frontPadWear * 0.3f); // 30% reduction at full wear
        rearForce *= (1.0f - rearPadWear * 0.3f);

        // Distribute force between left and right wheels
        brakeForces[0] = frontForce * 0.5f; // Front left
        brakeForces[1] = frontForce * 0.5f; // Front right
        brakeForces[2] = rearForce * 0.5f;  // Rear left
        brakeForces[3] = rearForce * 0.5f;  // Rear right

        // Apply ABS if enabled
        if (absEnabled) {
            applyABS(brakeForces, wheelSpeeds, deltaTime);
        }

        // Apply EBD if enabled
        if (ebdEnabled) {
            applyEBD(brakeForces, wheelSpeeds);
        }

        // Update brake temperatures
        updateTemperatures(brakeForces, deltaTime);

        // Update pad wear
        updatePadWear(brakeForces, deltaTime);

        return brakeForces;
    }

    /**
     * Calculate brake fade multiplier based on disc temperature
     */
    private float calculateFadeMultiplier(float discTemperature) {
        if (discTemperature <= fadeThreshold) {
            return 1.0f; // No fade
        }

        float fadeRange = maxTemperature - fadeThreshold;
        float tempAboveThreshold = discTemperature - fadeThreshold;
        float fadeRatio = tempAboveThreshold / fadeRange;

        // Exponential fade curve - more realistic
        return Math.max(0.2f, 1.0f - (float)Math.pow(fadeRatio, 1.5f) * 0.8f);
    }

    /**
     * Apply Anti-lock Braking System logic
     */
    private void applyABS(float[] brakeForces, float[] wheelSpeeds, float deltaTime) {
        // Calculate average wheel speed for reference
        float avgSpeed = 0.0f;
        for (float speed : wheelSpeeds) {
            avgSpeed += Math.abs(speed);
        }
        avgSpeed /= wheelSpeeds.length;

        if (avgSpeed < 1.0f) return; // No ABS at very low speeds

        // Check each wheel for excessive slip
        for (int i = 0; i < 4; i++) {
            float wheelSpeed = Math.abs(wheelSpeeds[i]);
            float slipRatio = (avgSpeed - wheelSpeed) / avgSpeed;

            if (slipRatio > absThreshold) {
                // Reduce brake force to prevent lock-up
                brakeForces[i] *= absReleaseRate;
            }
        }
    }

    /**
     * Apply Electronic Brake Distribution
     */
    private void applyEBD(float[] brakeForces, float[] wheelSpeeds) {
        // Adjust rear brake force based on wheel speeds to prevent rear lock-up
        float frontAvgSpeed = (Math.abs(wheelSpeeds[0]) + Math.abs(wheelSpeeds[1])) * 0.5f;
        float rearAvgSpeed = (Math.abs(wheelSpeeds[2]) + Math.abs(wheelSpeeds[3])) * 0.5f;

        if (frontAvgSpeed > 1.0f) { // Only apply EBD when moving
            float speedRatio = rearAvgSpeed / frontAvgSpeed;

            if (speedRatio < 0.9f) { // Rear wheels slowing faster
                float reduction = 1.0f - (0.9f - speedRatio) * 2.0f;
                brakeForces[2] *= Math.max(0.5f, reduction); // Rear left
                brakeForces[3] *= Math.max(0.5f, reduction); // Rear right
            }
        }
    }

    /**
     * Update brake disc temperatures based on energy dissipation
     */
    private void updateTemperatures(float[] brakeForces, float deltaTime) {
        // Heat generation from front brakes
        float frontHeatGeneration = (brakeForces[0] + brakeForces[1]) * 0.001f; // Simplified heat model
        frontDiscTemperature += frontHeatGeneration * deltaTime;

        // Heat generation from rear brakes
        float rearHeatGeneration = (brakeForces[2] + brakeForces[3]) * 0.001f;
        rearDiscTemperature += rearHeatGeneration * deltaTime;

        // Cooling effects
        float coolingMultiplier = ventilatedDiscs ? 1.5f : 1.0f;
        float frontCooling = (discCoolingRate + airflowCooling) * coolingMultiplier * deltaTime;
        float rearCooling = (discCoolingRate + airflowCooling * 0.8f) * coolingMultiplier * deltaTime;

        // Apply cooling toward ambient temperature
        if (frontDiscTemperature > ambientTemperature) {
            frontDiscTemperature = Math.max(ambientTemperature, frontDiscTemperature - frontCooling);
        }

        if (rearDiscTemperature > ambientTemperature) {
            rearDiscTemperature = Math.max(ambientTemperature, rearDiscTemperature - rearCooling);
        }

        // Clamp temperatures to reasonable limits
        frontDiscTemperature = Math.min(maxTemperature, frontDiscTemperature);
        rearDiscTemperature = Math.min(maxTemperature, rearDiscTemperature);
    }

    /**
     * Update brake pad wear based on usage
     */
    private void updatePadWear(float[] brakeForces, float deltaTime) {
        // Wear rate based on force and temperature
        float frontWearRate = (brakeForces[0] + brakeForces[1]) * 0.0001f * deltaTime;
        float rearWearRate = (brakeForces[2] + brakeForces[3]) * 0.0001f * deltaTime;

        // Temperature increases wear rate
        if (frontDiscTemperature > 300.0f) {
            frontWearRate *= 1.0f + (frontDiscTemperature - 300.0f) / 500.0f;
        }

        if (rearDiscTemperature > 300.0f) {
            rearWearRate *= 1.0f + (rearDiscTemperature - 300.0f) / 500.0f;
        }

        frontPadWear = Math.min(1.0f, frontPadWear + frontWearRate);
        rearPadWear = Math.min(1.0f, rearPadWear + rearWearRate);
    }

    /**
     * Set airflow cooling based on vehicle speed
     */
    public void updateAirflowCooling(float vehicleSpeed) {
        // More airflow = better cooling (speed in m/s)
        this.airflowCooling = Math.min(5.0f, vehicleSpeed * 0.1f);
    }

    /**
     * Get brake efficiency (0.0 to 1.0)
     */
    public float getBrakeEfficiency() {
        float frontEfficiency = calculateFadeMultiplier(frontDiscTemperature) * (1.0f - frontPadWear * 0.3f);
        float rearEfficiency = calculateFadeMultiplier(rearDiscTemperature) * (1.0f - rearPadWear * 0.3f);
        return (frontEfficiency + rearEfficiency) * 0.5f;
    }

    /**
     * Check if brakes are overheated
     */
    public boolean isOverheated() {
        return frontDiscTemperature > fadeThreshold || rearDiscTemperature > fadeThreshold;
    }

    /**
     * Get brake balance (front/rear distribution)
     */
    public float getCurrentBrakeBalance() {
        // Dynamic balance considering fade and wear
        float frontEfficiency = calculateFadeMultiplier(frontDiscTemperature) * (1.0f - frontPadWear * 0.3f);
        float rearEfficiency = calculateFadeMultiplier(rearDiscTemperature) * (1.0f - rearPadWear * 0.3f);

        float totalEfficiency = frontEfficiency + rearEfficiency;
        return totalEfficiency > 0 ? frontEfficiency / totalEfficiency : 0.5f;
    }

    // Getters and Setters
    public float getFrontDiscDiameter() { return frontDiscDiameter; }
    public void setFrontDiscDiameter(float frontDiscDiameter) { this.frontDiscDiameter = frontDiscDiameter; }

    public float getRearDiscDiameter() { return rearDiscDiameter; }
    public void setRearDiscDiameter(float rearDiscDiameter) { this.rearDiscDiameter = rearDiscDiameter; }

    public float getFrontDiscTemperature() { return frontDiscTemperature; }
    public void setFrontDiscTemperature(float frontDiscTemperature) { this.frontDiscTemperature = frontDiscTemperature; }

    public float getRearDiscTemperature() { return rearDiscTemperature; }
    public void setRearDiscTemperature(float rearDiscTemperature) { this.rearDiscTemperature = rearDiscTemperature; }

    public float getBrakeBias() { return brakeBias; }
    public void setBrakeBias(float brakeBias) { this.brakeBias = Math.max(0.3f, Math.min(0.8f, brakeBias)); }

    public boolean isAbsEnabled() { return absEnabled; }
    public void setAbsEnabled(boolean absEnabled) { this.absEnabled = absEnabled; }

    public boolean isEbdEnabled() { return ebdEnabled; }
    public void setEbdEnabled(boolean ebdEnabled) { this.ebdEnabled = ebdEnabled; }

    public float getFrontPadWear() { return frontPadWear; }
    public void setFrontPadWear(float frontPadWear) { this.frontPadWear = Math.max(0.0f, Math.min(1.0f, frontPadWear)); }

    public float getRearPadWear() { return rearPadWear; }
    public void setRearPadWear(float rearPadWear) { this.rearPadWear = Math.max(0.0f, Math.min(1.0f, rearPadWear)); }
}
