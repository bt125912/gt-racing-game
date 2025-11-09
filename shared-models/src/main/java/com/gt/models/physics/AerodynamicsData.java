package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aerodynamics data for realistic air resistance and downforce simulation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AerodynamicsData {
    private float dragCoefficient; // Cd value (0.2-0.8 typical for cars)
    private float frontalArea; // Frontal area in square meters
    private float downforceCoefficient; // Downforce generation coefficient
    private float liftCoefficient; // Lift coefficient (negative = downforce)
    private float sideForceCoefficient; // Side force coefficient for crosswinds
    private float airDensity; // Air density in kg/m³ (varies with altitude/temperature)
    private Vector3 centerOfPressure; // Center of aerodynamic pressure
    private float[] speedDragMultipliers; // Speed-dependent drag multipliers
    private float[] speedDownforceMultipliers; // Speed-dependent downforce multipliers
    private boolean hasActiveAero; // Active aerodynamic elements
    private float activeAeroPosition; // Position of active aero (0.0 = low drag, 1.0 = high downforce)
    private float groundEffectFactor; // Ground effect strength (0.0 = none, 1.0 = maximum)

    public AerodynamicsData() {
        // Default values for a typical sports car
        this.dragCoefficient = 0.32f;
        this.frontalArea = 2.2f; // m²
        this.downforceCoefficient = 0.8f;
        this.liftCoefficient = -0.2f; // Slight downforce
        this.sideForceCoefficient = 0.1f;
        this.airDensity = 1.225f; // kg/m³ at sea level, 15°C
        this.centerOfPressure = new Vector3(0, 0, -0.5f); // Slightly behind center
        this.hasActiveAero = false;
        this.activeAeroPosition = 0.0f;
        this.groundEffectFactor = 0.0f;

        initializeSpeedCurves();
    }

    public AerodynamicsData(float dragCoefficient, float frontalArea, float downforceCoefficient) {
        this();
        this.dragCoefficient = dragCoefficient;
        this.frontalArea = frontalArea;
        this.downforceCoefficient = downforceCoefficient;
        initializeSpeedCurves();
    }

    // Getters and Setters
    public float getDragCoefficient() {
        return dragCoefficient;
    }

    public void setDragCoefficient(float dragCoefficient) {
        this.dragCoefficient = Math.max(0.1f, Math.min(2.0f, dragCoefficient));
        initializeSpeedCurves();
    }

    public float getFrontalArea() {
        return frontalArea;
    }

    public void setFrontalArea(float frontalArea) {
        this.frontalArea = Math.max(1.0f, Math.min(5.0f, frontalArea));
    }

    public float getDownforceCoefficient() {
        return downforceCoefficient;
    }

    public void setDownforceCoefficient(float downforceCoefficient) {
        this.downforceCoefficient = Math.max(0.0f, Math.min(5.0f, downforceCoefficient));
        initializeSpeedCurves();
    }

    public float getLiftCoefficient() {
        return liftCoefficient;
    }

    public void setLiftCoefficient(float liftCoefficient) {
        this.liftCoefficient = Math.max(-2.0f, Math.min(1.0f, liftCoefficient));
    }

    public float getSideForceCoefficient() {
        return sideForceCoefficient;
    }

    public void setSideForceCoefficient(float sideForceCoefficient) {
        this.sideForceCoefficient = Math.max(0.0f, Math.min(1.0f, sideForceCoefficient));
    }

    public float getAirDensity() {
        return airDensity;
    }

    public void setAirDensity(float airDensity) {
        this.airDensity = Math.max(0.5f, Math.min(1.5f, airDensity));
    }

    public Vector3 getCenterOfPressure() {
        return centerOfPressure;
    }

    public void setCenterOfPressure(Vector3 centerOfPressure) {
        this.centerOfPressure = centerOfPressure;
    }

    public boolean hasActiveAero() {
        return hasActiveAero;
    }

    public void setHasActiveAero(boolean hasActiveAero) {
        this.hasActiveAero = hasActiveAero;
    }

    public float getActiveAeroPosition() {
        return activeAeroPosition;
    }

    public void setActiveAeroPosition(float activeAeroPosition) {
        this.activeAeroPosition = Math.max(0.0f, Math.min(1.0f, activeAeroPosition));
    }

    public float getGroundEffectFactor() {
        return groundEffectFactor;
    }

    public void setGroundEffectFactor(float groundEffectFactor) {
        this.groundEffectFactor = Math.max(0.0f, Math.min(2.0f, groundEffectFactor));
    }

    /**
     * Initialize speed-dependent aerodynamic curves
     */
    private void initializeSpeedCurves() {
        int numPoints = 50; // Speed points from 0 to 300 km/h
        speedDragMultipliers = new float[numPoints];
        speedDownforceMultipliers = new float[numPoints];

        for (int i = 0; i < numPoints; i++) {
            float speed = (i / (float)(numPoints - 1)) * 300.0f; // 0-300 km/h
            float speedMs = speed / 3.6f; // Convert to m/s

            // Drag increases quadratically with speed
            speedDragMultipliers[i] = speedMs * speedMs;

            // Downforce also increases quadratically, but may have different characteristics
            speedDownforceMultipliers[i] = speedMs * speedMs;
        }
    }

    /**
     * Calculate drag force at given speed
     * @param velocity Velocity vector
     * @return Drag force vector (opposite to velocity direction)
     */
    public Vector3 calculateDragForce(Vector3 velocity) {
        float speed = velocity.magnitude();
        if (speed < 0.1f) return new Vector3(); // No drag at very low speeds

        // Basic drag formula: F = 0.5 * ρ * Cd * A * v²
        float dragMagnitude = 0.5f * airDensity * getEffectiveDragCoefficient() * frontalArea * speed * speed;

        // Apply ground effect (reduces drag when close to ground)
        if (groundEffectFactor > 0) {
            dragMagnitude *= (1.0f - groundEffectFactor * 0.1f);
        }

        // Return force opposite to velocity direction
        return velocity.normalize().multiply(-dragMagnitude);
    }

    /**
     * Calculate downforce at given speed
     * @param speed Speed in m/s
     * @param rideHeight Vehicle ride height (affects ground effect)
     * @return Downforce magnitude (positive = downward force)
     */
    public float calculateDownforce(float speed, float rideHeight) {
        if (speed < 1.0f) return 0.0f; // No downforce at very low speeds

        // Basic downforce formula: F = 0.5 * ρ * Cl * A * v²
        float baseDownforce = 0.5f * airDensity * getEffectiveDownforceCoefficient() * frontalArea * speed * speed;

        // Apply ground effect
        if (groundEffectFactor > 0 && rideHeight < 0.2f) {
            float groundEffectMultiplier = 1.0f + (groundEffectFactor * (0.2f - rideHeight) / 0.2f);
            baseDownforce *= groundEffectMultiplier;
        }

        return baseDownforce;
    }

    /**
     * Calculate side force due to crosswind or yaw angle
     * @param velocity Vehicle velocity
     * @param windVelocity Wind velocity
     * @param yawAngle Vehicle yaw angle in radians
     * @return Side force vector
     */
    public Vector3 calculateSideForce(Vector3 velocity, Vector3 windVelocity, float yawAngle) {
        // Relative wind velocity
        Vector3 relativeWind = windVelocity.subtract(velocity);
        float relativeWindSpeed = relativeWind.magnitude();

        if (relativeWindSpeed < 1.0f) return new Vector3(); // No significant side force

        // Calculate effective angle of attack
        float effectiveYaw = yawAngle + (float)Math.atan2(relativeWind.getX(), relativeWind.getZ());

        // Side force magnitude
        float sideForceMagnitude = 0.5f * airDensity * sideForceCoefficient * frontalArea *
                                  relativeWindSpeed * relativeWindSpeed * (float)Math.sin(effectiveYaw);

        // Side force direction (perpendicular to vehicle)
        Vector3 sideDirection = new Vector3((float)Math.cos(yawAngle), 0, -(float)Math.sin(yawAngle));
        return sideDirection.multiply(sideForceMagnitude);
    }

    /**
     * Get effective drag coefficient considering active aero and speed
     * @return Effective drag coefficient
     */
    private float getEffectiveDragCoefficient() {
        float effectiveCd = dragCoefficient;

        // Active aerodynamics effect
        if (hasActiveAero) {
            // Higher active aero position = more drag (but more downforce)
            effectiveCd += activeAeroPosition * 0.1f;
        }

        return effectiveCd;
    }

    /**
     * Get effective downforce coefficient considering active aero and speed
     * @return Effective downforce coefficient
     */
    private float getEffectiveDownforceCoefficient() {
        float effectiveCl = downforceCoefficient;

        // Active aerodynamics effect
        if (hasActiveAero) {
            // Higher active aero position = more downforce
            effectiveCl += activeAeroPosition * 1.0f;
        }

        return effectiveCl;
    }

    /**
     * Update active aerodynamics based on vehicle state
     * @param speed Current speed in m/s
     * @param throttleInput Throttle input (0.0-1.0)
     * @param brakeInput Brake input (0.0-1.0)
     * @param steeringAngle Steering angle in radians
     */
    public void updateActiveAero(float speed, float throttleInput, float brakeInput, float steeringAngle) {
        if (!hasActiveAero) return;

        float targetPosition = 0.0f;

        // Increase downforce at high speeds
        if (speed > 50.0f) { // Above ~180 km/h
            targetPosition += (speed - 50.0f) / 50.0f; // Gradually increase
        }

        // Increase downforce during braking
        if (brakeInput > 0.3f) {
            targetPosition += brakeInput * 0.5f;
        }

        // Increase downforce during cornering
        float corneringForce = Math.abs(steeringAngle);
        if (corneringForce > 0.2f) {
            targetPosition += corneringForce * 0.3f;
        }

        // Reduce downforce for straight-line speed
        if (throttleInput > 0.8f && Math.abs(steeringAngle) < 0.1f && brakeInput < 0.1f) {
            targetPosition *= 0.5f; // Reduce for top speed
        }

        // Smooth transition to target position
        float transitionSpeed = 2.0f; // Adjust transition speed as needed
        float deltaTime = 1.0f / 60.0f; // Assume 60 FPS for now

        if (activeAeroPosition < targetPosition) {
            activeAeroPosition = Math.min(targetPosition, activeAeroPosition + transitionSpeed * deltaTime);
        } else if (activeAeroPosition > targetPosition) {
            activeAeroPosition = Math.max(targetPosition, activeAeroPosition - transitionSpeed * deltaTime);
        }

        // Clamp to valid range
        activeAeroPosition = Math.max(0.0f, Math.min(1.0f, activeAeroPosition));
    }

    /**
     * Calculate air density based on altitude and temperature
     * @param altitude Altitude in meters
     * @param temperature Temperature in Celsius
     * @return Air density in kg/m³
     */
    public static float calculateAirDensity(float altitude, float temperature) {
        // Standard atmospheric model
        float seaLevelDensity = 1.225f; // kg/m³
        float temperatureK = temperature + 273.15f; // Convert to Kelvin
        float standardTempK = 288.15f; // Standard temperature at sea level

        // Simplified atmospheric model
        float pressureRatio = (float)Math.pow(1.0 - (altitude / 44300.0), 5.255);
        float densityRatio = pressureRatio * (standardTempK / temperatureK);

        return seaLevelDensity * densityRatio;
    }

    /**
     * Get aerodynamic balance (front vs rear downforce distribution)
     * @return Balance factor (-1.0 = all rear, 0.0 = balanced, 1.0 = all front)
     */
    public float getAerodynamicBalance() {
        // This would be configurable in a real implementation
        // For now, assume slightly rear-biased for stability
        return -0.1f;
    }

    /**
     * Calculate total aerodynamic force and moment
     * @param velocity Vehicle velocity
     * @param windVelocity Wind velocity
     * @param yawAngle Vehicle yaw angle
     * @param rideHeight Vehicle ride height
     * @return Array containing [dragForce, downforce, sideForce, pitchingMoment, yawingMoment]
     */
    public float[] calculateTotalAerodynamicForces(Vector3 velocity, Vector3 windVelocity,
                                                  float yawAngle, float rideHeight) {
        Vector3 dragForce = calculateDragForce(velocity);
        float downforce = calculateDownforce(velocity.magnitude(), rideHeight);
        Vector3 sideForce = calculateSideForce(velocity, windVelocity, yawAngle);

        // Calculate moments (simplified)
        float pitchingMoment = downforce * centerOfPressure.getZ();
        float yawingMoment = sideForce.magnitude() * centerOfPressure.getZ();

        return new float[]{
            dragForce.magnitude(),
            downforce,
            sideForce.magnitude(),
            pitchingMoment,
            yawingMoment
        };
    }
}
