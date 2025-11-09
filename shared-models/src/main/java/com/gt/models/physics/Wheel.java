package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wheel physics component for tire simulation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Wheel {
    private float grip; // Tire grip coefficient (0.0 to 2.0+)
    private float radius; // Wheel radius in meters
    private float suspensionForce; // Current suspension force
    private float maxSuspensionForce; // Maximum suspension force
    private float suspensionStiffness; // Suspension spring constant
    private float suspensionDamping; // Suspension damping coefficient
    private float suspensionLength; // Current suspension compression
    private float maxSuspensionLength; // Maximum suspension travel
    private boolean isGrounded; // Is wheel touching ground
    private float slipRatio; // Longitudinal slip (for acceleration/braking)
    private float slipAngle; // Lateral slip (for cornering)
    private float temperature; // Tire temperature (affects grip)
    private float pressure; // Tire pressure
    private float wear; // Tire wear level (0.0 = new, 1.0 = completely worn)
    private String tireCompound; // Tire compound type
    private Vector3 worldPosition; // Wheel position in world space
    private Vector3 contactPoint; // Ground contact point
    private Vector3 contactNormal; // Ground normal at contact point

    public Wheel() {
        this.grip = 1.0f;
        this.radius = 0.3f; // 30cm radius (typical car wheel)
        this.suspensionForce = 0.0f;
        this.maxSuspensionForce = 15000.0f; // Newtons
        this.suspensionStiffness = 35000.0f; // N/m
        this.suspensionDamping = 3000.0f; // N*s/m
        this.suspensionLength = 0.3f; // 30cm
        this.maxSuspensionLength = 0.3f;
        this.isGrounded = false;
        this.slipRatio = 0.0f;
        this.slipAngle = 0.0f;
        this.temperature = 25.0f; // Celsius
        this.pressure = 2.2f; // Bar
        this.wear = 0.0f;
        this.tireCompound = "medium";
        this.worldPosition = new Vector3();
        this.contactPoint = new Vector3();
        this.contactNormal = Vector3.up();
    }

    public Wheel(float radius, float grip) {
        this();
        this.radius = radius;
        this.grip = grip;
    }

    // Getters and Setters
    public float getGrip() {
        return grip;
    }

    public void setGrip(float grip) {
        this.grip = Math.max(0.0f, Math.min(3.0f, grip)); // Clamp between 0 and 3
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = Math.max(0.1f, radius); // Minimum 10cm radius
    }

    public float getSuspensionForce() {
        return suspensionForce;
    }

    public void setSuspensionForce(float suspensionForce) {
        this.suspensionForce = suspensionForce;
    }

    public float getMaxSuspensionForce() {
        return maxSuspensionForce;
    }

    public void setMaxSuspensionForce(float maxSuspensionForce) {
        this.maxSuspensionForce = maxSuspensionForce;
    }

    public float getSuspensionStiffness() {
        return suspensionStiffness;
    }

    public void setSuspensionStiffness(float suspensionStiffness) {
        this.suspensionStiffness = suspensionStiffness;
    }

    public float getSuspensionDamping() {
        return suspensionDamping;
    }

    public void setSuspensionDamping(float suspensionDamping) {
        this.suspensionDamping = suspensionDamping;
    }

    public float getSuspensionLength() {
        return suspensionLength;
    }

    public void setSuspensionLength(float suspensionLength) {
        this.suspensionLength = Math.max(0.0f, Math.min(maxSuspensionLength, suspensionLength));
    }

    public float getMaxSuspensionLength() {
        return maxSuspensionLength;
    }

    public void setMaxSuspensionLength(float maxSuspensionLength) {
        this.maxSuspensionLength = maxSuspensionLength;
    }

    public boolean isGrounded() {
        return isGrounded;
    }

    public void setGrounded(boolean grounded) {
        isGrounded = grounded;
    }

    public float getSlipRatio() {
        return slipRatio;
    }

    public void setSlipRatio(float slipRatio) {
        this.slipRatio = slipRatio;
    }

    public float getSlipAngle() {
        return slipAngle;
    }

    public void setSlipAngle(float slipAngle) {
        this.slipAngle = slipAngle;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getPressure() {
        return pressure;
    }

    public void setPressure(float pressure) {
        this.pressure = Math.max(0.5f, Math.min(4.0f, pressure)); // Clamp between 0.5 and 4.0 bar
    }

    public float getWear() {
        return wear;
    }

    public void setWear(float wear) {
        this.wear = Math.max(0.0f, Math.min(1.0f, wear)); // Clamp between 0 and 1
    }

    public String getTireCompound() {
        return tireCompound;
    }

    public void setTireCompound(String tireCompound) {
        this.tireCompound = tireCompound;
    }

    public Vector3 getWorldPosition() {
        return worldPosition;
    }

    public void setWorldPosition(Vector3 worldPosition) {
        this.worldPosition = worldPosition;
    }

    public Vector3 getContactPoint() {
        return contactPoint;
    }

    public void setContactPoint(Vector3 contactPoint) {
        this.contactPoint = contactPoint;
    }

    public Vector3 getContactNormal() {
        return contactNormal;
    }

    public void setContactNormal(Vector3 contactNormal) {
        this.contactNormal = contactNormal;
    }

    /**
     * Update tire slip based on speed and traction conditions
     * @param wheelSpeed Speed of the wheel in m/s
     * @param carSpeed Speed of the car in m/s
     * @param traction Traction coefficient (0.0 to 1.0)
     */
    public void updateSlip(float wheelSpeed, float carSpeed, float traction) {
        // Calculate longitudinal slip ratio
        if (carSpeed > 0.1f) {
            this.slipRatio = (wheelSpeed - carSpeed) / carSpeed;
        } else {
            this.slipRatio = 0.0f;
        }

        // Clamp slip ratio
        this.slipRatio = Math.max(-1.0f, Math.min(1.0f, this.slipRatio));

        // Update temperature based on slip and speed
        float slipHeat = Math.abs(this.slipRatio) * Math.abs(wheelSpeed) * 2.0f;
        this.temperature += slipHeat * 0.01f;

        // Cool down over time
        this.temperature = Math.max(20.0f, this.temperature - 0.1f);

        // Update wear based on slip and temperature
        float wearRate = Math.abs(this.slipRatio) * 0.001f;
        if (this.temperature > 100.0f) {
            wearRate *= 2.0f; // Increased wear at high temperatures
        }
        this.wear = Math.min(1.0f, this.wear + wearRate);
    }

    /**
     * Calculate the current effective grip taking into account all factors
     * @return Effective grip coefficient
     */
    public float getEffectiveGrip() {
        float effectiveGrip = this.grip;

        // Temperature affects grip
        float optimalTemp = 85.0f;
        float tempDiff = Math.abs(this.temperature - optimalTemp);
        float tempFactor = Math.max(0.6f, 1.0f - (tempDiff / 100.0f));
        effectiveGrip *= tempFactor;

        // Pressure affects grip
        float optimalPressure = 2.2f;
        float pressureDiff = Math.abs(this.pressure - optimalPressure);
        float pressureFactor = Math.max(0.8f, 1.0f - (pressureDiff / 2.0f));
        effectiveGrip *= pressureFactor;

        // Wear reduces grip
        effectiveGrip *= (1.0f - this.wear * 0.3f);

        // Tire compound affects grip
        switch (this.tireCompound.toLowerCase()) {
            case "soft":
                effectiveGrip *= 1.2f;
                break;
            case "medium":
                effectiveGrip *= 1.0f;
                break;
            case "hard":
                effectiveGrip *= 0.85f;
                break;
            case "wet":
                effectiveGrip *= 1.5f; // Much better in wet conditions
                break;
        }

        return Math.max(0.1f, effectiveGrip);
    }

    /**
     * Calculate tire forces based on slip and load
     * @param load Normal force on the tire (Newtons)
     * @param surfaceFriction Surface friction coefficient
     * @return Tire force vector
     */
    public Vector3 calculateTireForces(float load, float surfaceFriction) {
        if (!isGrounded || load <= 0) {
            return new Vector3();
        }

        float effectiveGrip = getEffectiveGrip() * surfaceFriction;
        float maxForce = load * effectiveGrip;

        // Longitudinal force (acceleration/braking)
        float longitudinalForce = calculateLongitudinalForce(maxForce);

        // Lateral force (cornering)
        float lateralForce = calculateLateralForce(maxForce);

        // Combine forces (simplified Pacejka tire model)
        float totalSlip = (float) Math.sqrt(slipRatio * slipRatio + slipAngle * slipAngle);
        float combinedForce = maxForce * (float) Math.sin(Math.PI * totalSlip / 2.0);
        combinedForce = Math.min(combinedForce, maxForce);

        // Distribute combined force
        if (totalSlip > 0) {
            float longRatio = Math.abs(slipRatio) / totalSlip;
            float latRatio = Math.abs(slipAngle) / totalSlip;

            longitudinalForce = combinedForce * longRatio * Math.signum(slipRatio);
            lateralForce = combinedForce * latRatio * Math.signum(slipAngle);
        }

        return new Vector3(lateralForce, 0, longitudinalForce);
    }

    private float calculateLongitudinalForce(float maxForce) {
        // Simplified tire model for longitudinal force
        float normalizedSlip = Math.abs(slipRatio);
        float force = maxForce * (float) Math.sin(Math.PI * normalizedSlip / 2.0);
        return force * Math.signum(slipRatio);
    }

    private float calculateLateralForce(float maxForce) {
        // Simplified tire model for lateral force
        float normalizedSlip = Math.abs(slipAngle);
        float force = maxForce * (float) Math.sin(Math.PI * normalizedSlip / 2.0);
        return force * Math.signum(slipAngle);
    }

    /**
     * Update suspension physics
     * @param deltaTime Time step in seconds
     * @param targetLength Target suspension length
     * @param velocity Suspension velocity
     */
    public void updateSuspension(float deltaTime, float targetLength, float velocity) {
        float compression = targetLength - suspensionLength;

        // Spring force: F = -k * x
        float springForce = suspensionStiffness * compression;

        // Damping force: F = -c * v
        float dampingForce = suspensionDamping * velocity;

        suspensionForce = springForce + dampingForce;
        suspensionForce = Math.max(-maxSuspensionForce, Math.min(maxSuspensionForce, suspensionForce));
    }
}
