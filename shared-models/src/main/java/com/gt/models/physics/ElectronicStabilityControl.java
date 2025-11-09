package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Electronic Stability Control system with Traction Control and Stability Management
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElectronicStabilityControl {
    // System configuration
    private boolean escEnabled; // Electronic Stability Control
    private boolean tcsEnabled; // Traction Control System
    private boolean absEnabled; // Anti-lock Braking System (integrated)
    private boolean ebaEnabled; // Emergency Brake Assist
    private boolean rolloverProtection; // Rollover prevention

    // Control parameters
    private float escAggressiveness; // ESC intervention level (0.0 = minimal, 1.0 = maximum)
    private float tcsAggressiveness; // TCS intervention level
    private float absAggressiveness; // ABS intervention level

    // Thresholds
    private float maxSteerAngleDeviation; // Maximum allowed steering angle deviation (radians)
    private float maxSlipRatio; // Maximum allowed wheel slip ratio
    private float maxLateralAcceleration; // Maximum allowed lateral G-force
    private float maxYawRate; // Maximum allowed yaw rate (rad/s)
    private float oversteerThreshold; // Oversteer detection threshold
    private float understeerThreshold; // Understeer detection threshold

    // Sensor data simulation
    private Vector3 previousVelocity;
    private float previousYawRate;
    private Vector3 lateralAcceleration;
    private float yawRate;
    private float steerAngle;
    private float[] wheelSlipRatios; // [FL, FR, RL, RR]
    private float[] wheelSpeeds; // [FL, FR, RL, RR]

    // Control states
    private boolean escActive;
    private boolean tcsActive;
    private boolean absActive;
    private boolean oversteerDetected;
    private boolean understeerDetected;
    private boolean rolloverRisk;

    // Intervention outputs
    private float[] brakeAdjustments; // Brake force adjustments for each wheel
    private float throttleReduction; // Engine throttle reduction (0.0 to 1.0)
    private float steeringAssist; // Steering assistance force

    // Performance counters
    private long escActivationCount;
    private long tcsActivationCount;
    private float totalInterventionTime;

    public ElectronicStabilityControl() {
        // Default configuration - moderate intervention
        this.escEnabled = true;
        this.tcsEnabled = true;
        this.absEnabled = true;
        this.ebaEnabled = true;
        this.rolloverProtection = true;

        this.escAggressiveness = 0.7f;
        this.tcsAggressiveness = 0.6f;
        this.absAggressiveness = 0.8f;

        // Conservative thresholds for safety
        this.maxSteerAngleDeviation = (float)Math.toRadians(15); // 15 degrees
        this.maxSlipRatio = 0.15f; // 15% slip
        this.maxLateralAcceleration = 8.0f; // 8G (very high limit)
        this.maxYawRate = 2.0f; // 2 rad/s
        this.oversteerThreshold = 0.1f;
        this.understeerThreshold = 0.1f;

        // Initialize arrays
        this.previousVelocity = new Vector3();
        this.lateralAcceleration = new Vector3();
        this.wheelSlipRatios = new float[4];
        this.wheelSpeeds = new float[4];
        this.brakeAdjustments = new float[4];

        // Reset states
        resetControlStates();
    }

    /**
     * Main update function for stability control system
     * @param carVelocity Current vehicle velocity
     * @param carYawRate Current yaw rate
     * @param steerInput Steering input (-1.0 to 1.0)
     * @param throttleInput Throttle input (0.0 to 1.0)
     * @param brakeInput Brake input (0.0 to 1.0)
     * @param wheelSpeeds Array of wheel speeds [FL, FR, RL, RR]
     * @param deltaTime Time step in seconds
     * @return StabilityControlOutput with corrections
     */
    public StabilityControlOutput update(Vector3 carVelocity, float carYawRate,
                                        float steerInput, float throttleInput, float brakeInput,
                                        float[] wheelSpeeds, float deltaTime) {

        // Update sensor data
        updateSensorData(carVelocity, carYawRate, steerInput, wheelSpeeds, deltaTime);

        // Reset intervention outputs
        resetInterventions();

        // Perform stability analysis
        analyzeStability();

        // Apply control interventions
        if (escEnabled) {
            applyStabilityControl();
        }

        if (tcsEnabled) {
            applyTractionControl();
        }

        if (absEnabled) {
            applyAntiLockBraking();
        }

        if (ebaEnabled) {
            applyEmergencyBrakeAssist(brakeInput);
        }

        if (rolloverProtection) {
            applyRolloverProtection();
        }

        // Update performance counters
        updateCounters(deltaTime);

        // Return control outputs
        return createControlOutput(throttleInput, brakeInput);
    }

    /**
     * Update sensor data and calculate derived values
     */
    private void updateSensorData(Vector3 carVelocity, float carYawRate, float steerInput,
                                 float[] wheelSpeeds, float deltaTime) {

        // Calculate lateral acceleration
        if (deltaTime > 0) {
            Vector3 acceleration = carVelocity.subtract(previousVelocity).divide(deltaTime);
            lateralAcceleration = new Vector3(acceleration.getX(), 0, 0); // Simplified lateral component
        }

        // Update states
        this.yawRate = carYawRate;
        this.steerAngle = steerInput * (float)Math.toRadians(30); // Max 30 degrees
        System.arraycopy(wheelSpeeds, 0, this.wheelSpeeds, 0, 4);

        // Calculate wheel slip ratios
        float vehicleSpeed = carVelocity.magnitude();
        for (int i = 0; i < 4; i++) {
            if (vehicleSpeed > 1.0f) {
                wheelSlipRatios[i] = (wheelSpeeds[i] - vehicleSpeed) / vehicleSpeed;
            } else {
                wheelSlipRatios[i] = 0.0f;
            }
        }

        // Store previous values
        previousVelocity = carVelocity;
        previousYawRate = carYawRate;
    }

    /**
     * Analyze vehicle stability and detect dangerous conditions
     */
    private void analyzeStability() {
        // Detect oversteer (rear wheels losing grip)
        float rearSlipAvg = (Math.abs(wheelSlipRatios[2]) + Math.abs(wheelSlipRatios[3])) * 0.5f;
        float frontSlipAvg = (Math.abs(wheelSlipRatios[0]) + Math.abs(wheelSlipRatios[1])) * 0.5f;

        oversteerDetected = (rearSlipAvg - frontSlipAvg) > oversteerThreshold;

        // Detect understeer (front wheels losing grip)
        understeerDetected = (frontSlipAvg - rearSlipAvg) > understeerThreshold;

        // Check excessive yaw rate
        boolean excessiveYaw = Math.abs(yawRate) > maxYawRate;

        // Check excessive lateral acceleration
        boolean excessiveLateralG = lateralAcceleration.magnitude() > maxLateralAcceleration;

        // Determine if ESC should activate
        escActive = oversteerDetected || understeerDetected || excessiveYaw || excessiveLateralG;

        // Check for wheel slip (TCS activation)
        tcsActive = false;
        for (float slip : wheelSlipRatios) {
            if (Math.abs(slip) > maxSlipRatio) {
                tcsActive = true;
                break;
            }
        }

        // Check rollover risk
        rolloverRisk = lateralAcceleration.magnitude() > 6.0f; // 6G rollover threshold
    }

    /**
     * Apply stability control corrections
     */
    private void applyStabilityControl() {
        if (!escActive) return;

        float interventionStrength = escAggressiveness;

        if (oversteerDetected) {
            // Apply front outside brake to reduce oversteer
            float correctionForce = interventionStrength * 0.3f; // 30% brake force

            // Determine which side is outside of turn based on yaw rate
            if (yawRate > 0) { // Turning left, apply right front brake
                brakeAdjustments[1] += correctionForce;
            } else { // Turning right, apply left front brake
                brakeAdjustments[0] += correctionForce;
            }

            // Reduce engine power
            throttleReduction = Math.max(throttleReduction, interventionStrength * 0.5f);
        }

        if (understeerDetected) {
            // Apply rear inside brake to reduce understeer
            float correctionForce = interventionStrength * 0.4f;

            if (yawRate > 0) { // Turning left, apply left rear brake
                brakeAdjustments[2] += correctionForce;
            } else { // Turning right, apply right rear brake
                brakeAdjustments[3] += correctionForce;
            }

            // Reduce engine power
            throttleReduction = Math.max(throttleReduction, interventionStrength * 0.3f);
        }

        // Excessive yaw correction
        if (Math.abs(yawRate) > maxYawRate) {
            float yawCorrection = Math.min(0.6f, Math.abs(yawRate) / maxYawRate) * interventionStrength;

            // Apply opposite yaw moment
            if (yawRate > 0) {
                // Counter clockwise yaw, brake left wheels more
                brakeAdjustments[0] += yawCorrection * 0.3f;
                brakeAdjustments[2] += yawCorrection * 0.2f;
            } else {
                // Clockwise yaw, brake right wheels more
                brakeAdjustments[1] += yawCorrection * 0.3f;
                brakeAdjustments[3] += yawCorrection * 0.2f;
            }
        }
    }

    /**
     * Apply traction control to prevent wheel spin
     */
    private void applyTractionControl() {
        if (!tcsActive) return;

        float interventionStrength = tcsAggressiveness;

        for (int i = 0; i < 4; i++) {
            float slip = wheelSlipRatios[i];

            if (slip > maxSlipRatio) {
                // Wheel is spinning too fast - apply brake
                float slipExcess = slip - maxSlipRatio;
                float brakeCorrection = Math.min(0.5f, slipExcess * 3.0f) * interventionStrength;
                brakeAdjustments[i] += brakeCorrection;

                // Reduce engine power for drive wheels
                if (i >= 2) { // Assuming RWD - rear wheels are drive wheels
                    throttleReduction = Math.max(throttleReduction, slipExcess * interventionStrength);
                }
            }
        }
    }

    /**
     * Apply anti-lock braking to prevent wheel lockup
     */
    private void applyAntiLockBraking() {
        absActive = false;

        for (int i = 0; i < 4; i++) {
            float slip = wheelSlipRatios[i];

            if (slip < -maxSlipRatio) { // Negative slip = wheel lockup
                absActive = true;

                // Reduce brake force to prevent lockup
                float lockupSeverity = Math.abs(slip) - maxSlipRatio;
                float brakeReduction = Math.min(0.8f, lockupSeverity * 4.0f) * absAggressiveness;
                brakeAdjustments[i] -= brakeReduction;
            }
        }
    }

    /**
     * Apply emergency brake assist for panic braking
     */
    private void applyEmergencyBrakeAssist(float brakeInput) {
        // Detect rapid brake pedal application
        float brakeRate = brakeInput; // Simplified - would need previous brake input for rate

        if (brakeInput > 0.8f && brakeRate > 0.5f) {
            // Emergency braking detected - maximize brake force
            for (int i = 0; i < 4; i++) {
                brakeAdjustments[i] = Math.max(brakeAdjustments[i], 0.9f);
            }
        }
    }

    /**
     * Apply rollover protection
     */
    private void applyRolloverProtection() {
        if (!rolloverRisk) return;

        // Aggressive intervention to prevent rollover
        float correctionStrength = 1.0f; // Maximum intervention

        // Reduce engine power immediately
        throttleReduction = Math.max(throttleReduction, 0.8f);

        // Apply brakes to all wheels
        for (int i = 0; i < 4; i++) {
            brakeAdjustments[i] = Math.max(brakeAdjustments[i], 0.6f);
        }
    }

    /**
     * Reset intervention outputs
     */
    private void resetInterventions() {
        for (int i = 0; i < 4; i++) {
            brakeAdjustments[i] = 0.0f;
        }
        throttleReduction = 0.0f;
        steeringAssist = 0.0f;
    }

    /**
     * Reset control states
     */
    private void resetControlStates() {
        escActive = false;
        tcsActive = false;
        absActive = false;
        oversteerDetected = false;
        understeerDetected = false;
        rolloverRisk = false;
    }

    /**
     * Update performance counters
     */
    private void updateCounters(float deltaTime) {
        if (escActive) {
            escActivationCount++;
            totalInterventionTime += deltaTime;
        }

        if (tcsActive) {
            tcsActivationCount++;
        }
    }

    /**
     * Create control output with all corrections
     */
    private StabilityControlOutput createControlOutput(float throttleInput, float brakeInput) {
        StabilityControlOutput output = new StabilityControlOutput();

        // Apply throttle reduction
        output.throttleOutput = Math.max(0.0f, throttleInput - throttleReduction);

        // Apply brake adjustments
        output.brakeOutput = Math.min(1.0f, brakeInput);
        output.brakeAdjustments = brakeAdjustments.clone();

        // Status information
        output.escActive = escActive;
        output.tcsActive = tcsActive;
        output.absActive = absActive;
        output.oversteerDetected = oversteerDetected;
        output.understeerDetected = understeerDetected;
        output.rolloverRisk = rolloverRisk;

        return output;
    }

    /**
     * Set driving mode (affects intervention aggressiveness)
     */
    public void setDrivingMode(String mode) {
        switch (mode.toLowerCase()) {
            case "comfort":
                escAggressiveness = 1.0f;
                tcsAggressiveness = 1.0f;
                break;
            case "sport":
                escAggressiveness = 0.7f;
                tcsAggressiveness = 0.6f;
                break;
            case "track":
                escAggressiveness = 0.4f;
                tcsAggressiveness = 0.3f;
                break;
            case "off":
                escEnabled = false;
                tcsEnabled = false;
                break;
        }
    }

    /**
     * Output class for stability control corrections
     */
    public static class StabilityControlOutput {
        public float throttleOutput;
        public float brakeOutput;
        public float[] brakeAdjustments;
        public boolean escActive;
        public boolean tcsActive;
        public boolean absActive;
        public boolean oversteerDetected;
        public boolean understeerDetected;
        public boolean rolloverRisk;
    }

    // Getters and setters
    public boolean isEscEnabled() { return escEnabled; }
    public void setEscEnabled(boolean escEnabled) { this.escEnabled = escEnabled; }

    public boolean isTcsEnabled() { return tcsEnabled; }
    public void setTcsEnabled(boolean tcsEnabled) { this.tcsEnabled = tcsEnabled; }

    public boolean isAbsEnabled() { return absEnabled; }
    public void setAbsEnabled(boolean absEnabled) { this.absEnabled = absEnabled; }

    public float getEscAggressiveness() { return escAggressiveness; }
    public void setEscAggressiveness(float escAggressiveness) {
        this.escAggressiveness = Math.max(0.0f, Math.min(1.0f, escAggressiveness));
    }

    public float getTcsAggressiveness() { return tcsAggressiveness; }
    public void setTcsAggressiveness(float tcsAggressiveness) {
        this.tcsAggressiveness = Math.max(0.0f, Math.min(1.0f, tcsAggressiveness));
    }

    public boolean isEscActive() { return escActive; }
    public boolean isTcsActive() { return tcsActive; }
    public boolean isAbsActive() { return absActive; }

    public long getEscActivationCount() { return escActivationCount; }
    public long getTcsActivationCount() { return tcsActivationCount; }
    public float getTotalInterventionTime() { return totalInterventionTime; }
}
