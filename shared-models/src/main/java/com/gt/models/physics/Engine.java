package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Engine simulation model with realistic power curves and characteristics
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Engine {
    private float maxPower; // Maximum power in horsepower
    private float maxTorque; // Maximum torque in Nm
    private float idleRpm; // Idle RPM
    private float redlineRpm; // Maximum safe RPM
    private float shiftUpRpm; // Recommended shift up RPM
    private float shiftDownRpm; // Recommended shift down RPM
    private float[] powerCurve; // Power at different RPM points
    private float[] torqueCurve; // Torque at different RPM points
    private float fuelConsumptionRate; // Fuel consumption rate (L/s at full throttle)
    private float damageMultiplier; // Performance reduction due to damage (0.0-1.0)
    private float temperature; // Engine temperature in Celsius
    private boolean turboCharged;
    private float boostPressure; // Turbo boost pressure in bar
    private String engineType; // "inline4", "v6", "v8", "v10", "v12", etc.
    private float rpm; // Current RPM for sound calculation

    public Engine() {
        this.maxPower = 300.0f; // 300 HP
        this.maxTorque = 400.0f; // 400 Nm
        this.idleRpm = 800.0f;
        this.redlineRpm = 7500.0f;
        this.shiftUpRpm = 6500.0f;
        this.shiftDownRpm = 2500.0f;
        this.fuelConsumptionRate = 0.001f; // 1 mL/s at full throttle
        this.damageMultiplier = 1.0f;
        this.temperature = 90.0f; // Normal operating temperature
        this.turboCharged = false;
        this.boostPressure = 0.0f;
        this.engineType = "v6";

        // Initialize power and torque curves
        initializeCurves();
    }

    public Engine(float maxPower, float maxTorque, float redlineRpm) {
        this();
        this.maxPower = maxPower;
        this.maxTorque = maxTorque;
        this.redlineRpm = redlineRpm;
        this.shiftUpRpm = redlineRpm * 0.85f;
        initializeCurves();
    }

    // Getters and Setters
    public float getMaxPower() {
        return maxPower;
    }

    public void setMaxPower(float maxPower) {
        this.maxPower = maxPower;
        initializeCurves();
    }

    public float getMaxTorque() {
        return maxTorque;
    }

    public void setMaxTorque(float maxTorque) {
        this.maxTorque = maxTorque;
        initializeCurves();
    }

    public float getIdleRpm() {
        return idleRpm;
    }

    public void setIdleRpm(float idleRpm) {
        this.idleRpm = idleRpm;
    }

    public float getRedlineRpm() {
        return redlineRpm;
    }

    public void setRedlineRpm(float redlineRpm) {
        this.redlineRpm = redlineRpm;
        this.shiftUpRpm = redlineRpm * 0.85f;
        initializeCurves();
    }

    public float getShiftUpRpm() {
        return shiftUpRpm;
    }

    public void setShiftUpRpm(float shiftUpRpm) {
        this.shiftUpRpm = shiftUpRpm;
    }

    public float getShiftDownRpm() {
        return shiftDownRpm;
    }

    public void setShiftDownRpm(float shiftDownRpm) {
        this.shiftDownRpm = shiftDownRpm;
    }

    public float getFuelConsumptionRate() {
        return fuelConsumptionRate;
    }

    public void setFuelConsumptionRate(float fuelConsumptionRate) {
        this.fuelConsumptionRate = fuelConsumptionRate;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = Math.max(0.0f, Math.min(1.0f, damageMultiplier));
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public boolean isTurboCharged() {
        return turboCharged;
    }

    public void setTurboCharged(boolean turboCharged) {
        this.turboCharged = turboCharged;
    }

    public float getBoostPressure() {
        return boostPressure;
    }

    public void setBoostPressure(float boostPressure) {
        this.boostPressure = Math.max(0.0f, Math.min(3.0f, boostPressure));
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public void setCurrentRpm(float rpm) {
        this.rpm = rpm;
    }

    /**
     * Initialize power and torque curves based on engine characteristics
     */
    private void initializeCurves() {
        int numPoints = 100;
        powerCurve = new float[numPoints];
        torqueCurve = new float[numPoints];

        float rpmRange = redlineRpm - idleRpm;
        float rpmStep = rpmRange / (numPoints - 1);

        for (int i = 0; i < numPoints; i++) {
            float rpm = idleRpm + (i * rpmStep);
            float normalizedRpm = (rpm - idleRpm) / rpmRange;

            // Realistic engine curves - peak torque at lower RPM, peak power at higher RPM
            torqueCurve[i] = calculateTorqueAtRpm(normalizedRpm);
            powerCurve[i] = calculatePowerAtRpm(normalizedRpm, rpm);
        }
    }

    private float calculateTorqueAtRpm(float normalizedRpm) {
        // Peak torque typically occurs around 30-50% of RPM range
        float peakRpm = 0.4f;
        float falloffRate = 2.0f;

        float torqueMultiplier;
        if (normalizedRpm <= peakRpm) {
            // Rising torque curve
            torqueMultiplier = normalizedRpm / peakRpm;
        } else {
            // Falling torque curve
            float falloff = Math.min(1.0f, (normalizedRpm - peakRpm) * falloffRate);
            torqueMultiplier = 1.0f - falloff;
        }

        return maxTorque * Math.max(0.3f, torqueMultiplier);
    }

    private float calculatePowerAtRpm(float normalizedRpm, float rpm) {
        // Power = Torque * RPM / constant
        // Peak power typically occurs around 70-80% of RPM range
        float peakRpm = 0.75f;
        float powerMultiplier;

        if (normalizedRpm <= peakRpm) {
            // Rising power curve
            powerMultiplier = normalizedRpm / peakRpm;
        } else {
            // Gradual power falloff after peak
            float falloff = (normalizedRpm - peakRpm) / (1.0f - peakRpm);
            powerMultiplier = 1.0f - (falloff * 0.2f);
        }

        return maxPower * Math.max(0.1f, powerMultiplier);
    }

    /**
     * Get torque at specific RPM
     * @param rpm Engine RPM
     * @return Torque in Nm
     */
    public float getTorque(float rpm) {
        if (rpm < idleRpm || rpm > redlineRpm) {
            return 0.0f;
        }

        float normalizedRpm = (rpm - idleRpm) / (redlineRpm - idleRpm);
        int index = Math.min(torqueCurve.length - 1, (int) (normalizedRpm * (torqueCurve.length - 1)));

        float baseTorque = torqueCurve[index];

        // Apply turbo boost if equipped
        if (turboCharged && boostPressure > 0) {
            float boostMultiplier = 1.0f + (boostPressure * 0.3f); // 30% per bar of boost
            baseTorque *= boostMultiplier;
        }

        // Apply damage reduction
        baseTorque *= damageMultiplier;

        // Temperature affects performance
        float tempMultiplier = getTemperatureMultiplier();
        baseTorque *= tempMultiplier;

        return baseTorque;
    }

    /**
     * Get power at specific RPM
     * @param rpm Engine RPM
     * @return Power in HP
     */
    public float getPower(float rpm) {
        if (rpm < idleRpm || rpm > redlineRpm) {
            return 0.0f;
        }

        float normalizedRpm = (rpm - idleRpm) / (redlineRpm - idleRpm);
        int index = Math.min(powerCurve.length - 1, (int) (normalizedRpm * (powerCurve.length - 1)));

        float basePower = powerCurve[index];

        // Apply turbo boost if equipped
        if (turboCharged && boostPressure > 0) {
            float boostMultiplier = 1.0f + (boostPressure * 0.25f); // 25% per bar of boost
            basePower *= boostMultiplier;
        }

        // Apply damage reduction
        basePower *= damageMultiplier;

        // Temperature affects performance
        float tempMultiplier = getTemperatureMultiplier();
        basePower *= tempMultiplier;

        return basePower;
    }

    /**
     * Calculate target RPM based on throttle input and load
     * @param throttleInput Throttle position (0.0 to 1.0)
     * @param load Engine load factor
     * @param currentGear Current gear
     * @return Target RPM
     */
    public float calculateTargetRpm(float throttleInput, float load, int currentGear) {
        if (throttleInput <= 0) {
            return idleRpm;
        }

        // Base target based on throttle input
        float baseTarget = idleRpm + (redlineRpm - idleRpm) * throttleInput;

        // Adjust for load - higher load means lower RPM for same throttle input
        float loadAdjustment = 1.0f - (load * 0.3f);
        baseTarget *= loadAdjustment;

        // Gear-specific adjustments
        if (currentGear > 0) {
            // Lower gears can reach higher RPM more easily
            float gearMultiplier = 1.0f + (1.0f / currentGear * 0.1f);
            baseTarget *= gearMultiplier;
        }

        return Math.max(idleRpm, Math.min(redlineRpm, baseTarget));
    }

    /**
     * Update engine state
     * @param deltaTime Time step in seconds
     * @param currentRpm Current engine RPM
     * @param throttleInput Current throttle input
     */
    public void update(float deltaTime, float currentRpm, float throttleInput) {
        // Update temperature based on load
        float targetTemp = 90.0f + (throttleInput * 30.0f); // 90-120°C range
        float tempDelta = (targetTemp - temperature) * 2.0f * deltaTime;
        temperature += tempDelta;

        // Update turbo boost pressure if equipped
        if (turboCharged) {
            float targetBoost = throttleInput * 1.5f; // Max 1.5 bar
            float boostDelta = (targetBoost - boostPressure) * 5.0f * deltaTime;
            boostPressure = Math.max(0.0f, Math.min(3.0f, boostPressure + boostDelta));
        }

        // Check for overheating damage
        if (temperature > 120.0f) {
            float overheatingDamage = (temperature - 120.0f) * 0.001f * deltaTime;
            damageMultiplier = Math.max(0.5f, damageMultiplier - overheatingDamage);
        }
    }

    private float getTemperatureMultiplier() {
        // Optimal temperature range: 90-100°C
        if (temperature >= 90.0f && temperature <= 100.0f) {
            return 1.0f;
        } else if (temperature < 90.0f) {
            // Cold engine penalty
            return 0.8f + (temperature / 90.0f) * 0.2f;
        } else {
            // Overheating penalty
            return Math.max(0.5f, 1.0f - ((temperature - 100.0f) / 50.0f) * 0.3f);
        }
    }

    /**
     * Check if engine should automatically shift up
     */
    public boolean shouldShiftUp(float currentRpm) {
        return currentRpm >= shiftUpRpm;
    }

    /**
     * Check if engine should automatically shift down
     */
    public boolean shouldShiftDown(float currentRpm) {
        return currentRpm <= shiftDownRpm;
    }

    /**
     * Get engine sound pitch multiplier based on RPM
     */
    public float getSoundPitch() {
        return Math.max(0.5f, Math.min(2.0f, rpm / (redlineRpm * 0.5f)));
    }

    /**
     * Get engine efficiency at current RPM
     * @param rpm Current RPM
     * @return Efficiency (0.0 to 1.0)
     */
    public float getEfficiency(float rpm) {
        // Most efficient around 2000-3000 RPM for most engines
        float optimalRpm = idleRpm + (redlineRpm - idleRpm) * 0.3f;
        float rpmDiff = Math.abs(rpm - optimalRpm);
        float maxDiff = redlineRpm - idleRpm;

        return Math.max(0.2f, 1.0f - (rpmDiff / maxDiff));
    }
}
