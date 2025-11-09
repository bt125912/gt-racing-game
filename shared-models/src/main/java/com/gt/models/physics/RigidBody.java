package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * RigidBody physics component for car physics simulation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RigidBody {
    private Vector3 position;
    private Vector3 velocity;
    private Vector3 acceleration;
    private Vector3 angularVelocity;
    private Vector3 rotation; // Euler angles in radians
    private float mass;
    private float drag; // Air resistance coefficient
    private float rollingResistance;
    private boolean isGrounded;
    private Vector3 centerOfMass;

    public RigidBody() {
        this.position = new Vector3();
        this.velocity = new Vector3();
        this.acceleration = new Vector3();
        this.angularVelocity = new Vector3();
        this.rotation = new Vector3();
        this.mass = 1500.0f; // Default car mass in kg
        this.drag = 0.3f;
        this.rollingResistance = 0.02f;
        this.isGrounded = true;
        this.centerOfMass = new Vector3(0, -0.5f, 0); // Slightly below center
    }

    public RigidBody(float mass) {
        this();
        this.mass = mass;
    }

    // Getters and Setters
    public Vector3 getPosition() {
        return position;
    }

    public void setPosition(Vector3 position) {
        this.position = position;
    }

    public Vector3 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector3 velocity) {
        this.velocity = velocity;
    }

    public Vector3 getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(Vector3 acceleration) {
        this.acceleration = acceleration;
    }

    public Vector3 getAngularVelocity() {
        return angularVelocity;
    }

    public void setAngularVelocity(Vector3 angularVelocity) {
        this.angularVelocity = angularVelocity;
    }

    public Vector3 getRotation() {
        return rotation;
    }

    public void setRotation(Vector3 rotation) {
        this.rotation = rotation;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getDrag() {
        return drag;
    }

    public void setDrag(float drag) {
        this.drag = drag;
    }

    public float getRollingResistance() {
        return rollingResistance;
    }

    public void setRollingResistance(float rollingResistance) {
        this.rollingResistance = rollingResistance;
    }

    public boolean isGrounded() {
        return isGrounded;
    }

    public void setGrounded(boolean grounded) {
        isGrounded = grounded;
    }

    public Vector3 getCenterOfMass() {
        return centerOfMass;
    }

    public void setCenterOfMass(Vector3 centerOfMass) {
        this.centerOfMass = centerOfMass;
    }

    /**
     * Apply a force to the rigid body
     * @param force Force vector in Newtons
     */
    public void applyForce(Vector3 force) {
        if (mass > 0) {
            // F = ma, so a = F/m
            Vector3 accel = force.divide(mass);
            this.acceleration = this.acceleration.add(accel);
        }
    }

    /**
     * Apply force at a specific point (for torque calculation)
     * @param force Force vector
     * @param point Point of application relative to center of mass
     */
    public void applyForceAtPoint(Vector3 force, Vector3 point) {
        // Apply linear force
        applyForce(force);

        // Calculate torque: τ = r × F
        Vector3 relativePoint = point.subtract(centerOfMass);
        Vector3 torque = relativePoint.cross(force);

        // Apply angular acceleration (simplified, assuming unit moment of inertia)
        this.angularVelocity = this.angularVelocity.add(torque.multiply(1.0f / mass));
    }

    /**
     * Integrate physics over time step
     * @param deltaTime Time step in seconds
     */
    public void integrate(float deltaTime) {
        // Apply gravity if not grounded
        if (!isGrounded) {
            Vector3 gravity = new Vector3(0, -9.81f * mass, 0);
            applyForce(gravity);
        }

        // Apply drag forces
        if (velocity.magnitude() > 0) {
            Vector3 dragForce = velocity.normalize().multiply(-0.5f * drag * velocity.magnitudeSquared());
            applyForce(dragForce);
        }

        // Apply rolling resistance if grounded
        if (isGrounded && velocity.magnitude() > 0) {
            Vector3 rollingForce = velocity.normalize().multiply(-rollingResistance * mass * 9.81f);
            applyForce(rollingForce);
        }

        // Integrate linear motion using Verlet integration
        Vector3 oldVelocity = velocity;
        velocity = velocity.add(acceleration.multiply(deltaTime));
        position = position.add(oldVelocity.add(velocity).multiply(0.5f * deltaTime));

        // Integrate angular motion
        rotation = rotation.add(angularVelocity.multiply(deltaTime));

        // Apply angular damping
        angularVelocity = angularVelocity.multiply(0.95f);

        // Reset acceleration for next frame
        acceleration = new Vector3();

        // Keep rotation values in reasonable range
        normalizeRotation();
    }

    /**
     * Get the forward direction based on current rotation
     */
    public Vector3 getForwardDirection() {
        float yaw = rotation.getY();
        return new Vector3(
            (float) Math.sin(yaw),
            0,
            (float) Math.cos(yaw)
        ).normalize();
    }

    /**
     * Get the right direction based on current rotation
     */
    public Vector3 getRightDirection() {
        return getForwardDirection().cross(Vector3.up()).normalize();
    }

    /**
     * Get current speed in m/s
     */
    public float getSpeed() {
        return velocity.magnitude();
    }

    /**
     * Get current speed in km/h
     */
    public float getSpeedKmh() {
        return getSpeed() * 3.6f;
    }

    /**
     * Set velocity in a specific direction
     * @param direction Normalized direction vector
     * @param speed Speed in m/s
     */
    public void setVelocityInDirection(Vector3 direction, float speed) {
        this.velocity = direction.normalize().multiply(speed);
    }

    private void normalizeRotation() {
        float twoPi = (float) (2 * Math.PI);
        rotation.setX(normalizeAngle(rotation.getX(), twoPi));
        rotation.setY(normalizeAngle(rotation.getY(), twoPi));
        rotation.setZ(normalizeAngle(rotation.getZ(), twoPi));
    }

    private float normalizeAngle(float angle, float twoPi) {
        while (angle > Math.PI) angle -= twoPi;
        while (angle < -Math.PI) angle += twoPi;
        return angle;
    }

    /**
     * Check if the rigid body is moving
     */
    public boolean isMoving() {
        return velocity.magnitude() > 0.01f; // Threshold to account for floating point precision
    }

    /**
     * Stop the rigid body completely
     */
    public void stop() {
        this.velocity = new Vector3();
        this.acceleration = new Vector3();
        this.angularVelocity = new Vector3();
    }
}
