package com.gt.models.physics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Car physics model with realistic vehicle dynamics and advanced systems
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Car {
    private RigidBody body;
    private Wheel[] wheels; // [FL, FR, RL, RR]
    private Engine engine;
    private Transmission transmission;

    // Advanced systems
    private BrakeSystem brakeSystem;
    private SuspensionSystem suspensionSystem;
    private ElectronicStabilityControl stabilityControl;

    private String carId;
    private String name;
    private float wheelbase; // Distance between front and rear axles
    private float trackWidth; // Distance between left and right wheels
    private float centerOfMassHeight;
    private float frontWeightDistribution; // 0.0 = all rear, 1.0 = all front
    private AerodynamicsData aero;
    private float steeringAngle; // Current steering input (-1.0 to 1.0)
    private float throttleInput; // 0.0 to 1.0
    private float brakeInput; // 0.0 to 1.0
    private float clutchInput; // 0.0 = engaged, 1.0 = disengaged
    private boolean handbrakeEngaged;
    private float rpm;
    private int currentGear; // 0 = neutral, -1 = reverse, 1+ = forward gears
    private float fuelLevel; // 0.0 to 1.0
    private float damageLevel; // 0.0 = pristine, 1.0 = totaled

    public Car() {
        this.body = new RigidBody(1500.0f); // Default car mass
        this.wheels = new Wheel[4];
        for (int i = 0; i < 4; i++) {
            this.wheels[i] = new Wheel();
        }
        this.engine = new Engine();
        this.transmission = new Transmission();

        // Initialize advanced systems
        this.brakeSystem = new BrakeSystem();
        this.suspensionSystem = new SuspensionSystem();
        this.stabilityControl = new ElectronicStabilityControl();

        this.wheelbase = 2.7f; // meters
        this.trackWidth = 1.5f; // meters
        this.centerOfMassHeight = 0.5f; // meters
        this.frontWeightDistribution = 0.55f; // 55% front, 45% rear
        this.aero = new AerodynamicsData();
        this.steeringAngle = 0.0f;
        this.throttleInput = 0.0f;
        this.brakeInput = 0.0f;
        this.clutchInput = 0.0f;
        this.handbrakeEngaged = false;
        this.rpm = 800.0f; // Idle RPM
        this.currentGear = 0; // Neutral
        this.fuelLevel = 1.0f; // Full tank
        this.damageLevel = 0.0f; // No damage
    }

    public Car(String carId, String name, float mass) {
        this();
        this.carId = carId;
        this.name = name;
        this.body.setMass(mass);
    }

    // Getters and Setters
    public RigidBody getBody() {
        return body;
    }

    public void setBody(RigidBody body) {
        this.body = body;
    }

    public Wheel[] getWheels() {
        return wheels;
    }

    public void setWheels(Wheel[] wheels) {
        this.wheels = wheels;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public void setTransmission(Transmission transmission) {
        this.transmission = transmission;
    }

    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getWheelbase() {
        return wheelbase;
    }

    public void setWheelbase(float wheelbase) {
        this.wheelbase = wheelbase;
    }

    public float getTrackWidth() {
        return trackWidth;
    }

    public void setTrackWidth(float trackWidth) {
        this.trackWidth = trackWidth;
    }

    public float getCenterOfMassHeight() {
        return centerOfMassHeight;
    }

    public void setCenterOfMassHeight(float centerOfMassHeight) {
        this.centerOfMassHeight = centerOfMassHeight;
    }

    public float getFrontWeightDistribution() {
        return frontWeightDistribution;
    }

    public void setFrontWeightDistribution(float frontWeightDistribution) {
        this.frontWeightDistribution = Math.max(0.3f, Math.min(0.7f, frontWeightDistribution));
    }

    public AerodynamicsData getAero() {
        return aero;
    }

    public void setAero(AerodynamicsData aero) {
        this.aero = aero;
    }

    public float getSteeringAngle() {
        return steeringAngle;
    }

    public void setSteeringAngle(float steeringAngle) {
        this.steeringAngle = Math.max(-1.0f, Math.min(1.0f, steeringAngle));
    }

    public float getThrottleInput() {
        return throttleInput;
    }

    public void setThrottleInput(float throttleInput) {
        this.throttleInput = Math.max(0.0f, Math.min(1.0f, throttleInput));
    }

    public float getBrakeInput() {
        return brakeInput;
    }

    public void setBrakeInput(float brakeInput) {
        this.brakeInput = Math.max(0.0f, Math.min(1.0f, brakeInput));
    }

    public float getClutchInput() {
        return clutchInput;
    }

    public void setClutchInput(float clutchInput) {
        this.clutchInput = Math.max(0.0f, Math.min(1.0f, clutchInput));
    }

    public boolean isHandbrakeEngaged() {
        return handbrakeEngaged;
    }

    public void setHandbrakeEngaged(boolean handbrakeEngaged) {
        this.handbrakeEngaged = handbrakeEngaged;
    }

    public float getRpm() {
        return rpm;
    }

    public void setRpm(float rpm) {
        this.rpm = Math.max(0.0f, Math.min(engine.getRedlineRpm(), rpm));
    }

    public int getCurrentGear() {
        return currentGear;
    }

    public void setCurrentGear(int currentGear) {
        this.currentGear = Math.max(-1, Math.min(transmission.getGearCount(), currentGear));
    }

    public float getFuelLevel() {
        return fuelLevel;
    }

    public void setFuelLevel(float fuelLevel) {
        this.fuelLevel = Math.max(0.0f, Math.min(1.0f, fuelLevel));
    }

    public float getDamageLevel() {
        return damageLevel;
    }

    public void setDamageLevel(float damageLevel) {
        this.damageLevel = Math.max(0.0f, Math.min(1.0f, damageLevel));
    }

    /**
     * Main physics update method - called every frame with advanced systems
     * @param deltaTime Time step in seconds
     */
    public void updatePhysics(float deltaTime) {
        if (fuelLevel <= 0.0f) {
            throttleInput = 0.0f; // Engine stops without fuel
        }

        // Update engine RPM and torque
        updateEngine(deltaTime);

        // Update wheel positions relative to car body
        updateWheelPositions();

        // Update advanced suspension system
        updateAdvancedSuspension(deltaTime);

        // Update advanced brake system with temperature and fade
        updateAdvancedBrakes(deltaTime);

        // Apply electronic stability control
        updateStabilityControl(deltaTime);

        // Calculate forces from each wheel
        updateWheelForces(deltaTime);

        // Apply aerodynamic forces
        applyAerodynamicForces();

        // Update transmission and drivetrain
        updateDrivetrain(deltaTime);

        // Integrate rigid body physics
        body.integrate(deltaTime);

        // Update fuel consumption
        updateFuelConsumption(deltaTime);

        // Update damage if crashed
        checkForDamage();

        // Update tire temperatures and wear
        for (Wheel wheel : wheels) {
            float wheelSpeed = calculateWheelSpeed(wheel);
            float carSpeed = body.getSpeed();
            wheel.updateSlip(wheelSpeed, carSpeed, getTrackTraction());
        }
    }

    /**
     * Update advanced suspension system
     */
    private void updateAdvancedSuspension(float deltaTime) {
        // Calculate wheel loads for each corner
        float[] wheelLoads = calculateWheelLoads();

        // Calculate wheel positions (simplified for suspension calculation)
        Vector3[] wheelPositions = new Vector3[4];
        Vector3 position = body.getPosition();
        Vector3 forward = body.getForwardDirection();
        Vector3 right = body.getRightDirection();

        float frontOffset = wheelbase * (1.0f - frontWeightDistribution);
        float rearOffset = -wheelbase * frontWeightDistribution;
        float sideOffset = trackWidth * 0.5f;

        wheelPositions[0] = position.add(forward.multiply(frontOffset)).add(right.multiply(-sideOffset)); // FL
        wheelPositions[1] = position.add(forward.multiply(frontOffset)).add(right.multiply(sideOffset));  // FR
        wheelPositions[2] = position.add(forward.multiply(rearOffset)).add(right.multiply(-sideOffset)); // RL
        wheelPositions[3] = position.add(forward.multiply(rearOffset)).add(right.multiply(sideOffset)); // RR

        // Update suspension and get forces
        float[] suspensionForces = suspensionSystem.updateSuspension(
            wheelLoads, wheelPositions, position, deltaTime
        );

        // Apply suspension forces to car body
        for (int i = 0; i < 4; i++) {
            Vector3 suspensionForce = Vector3.up().multiply(suspensionForces[i]);
            body.applyForceAtPoint(suspensionForce, wheelPositions[i]);

            // Update wheel camber and toe from suspension geometry
            wheels[i].setSlipAngle(wheels[i].getSlipAngle() +
                suspensionSystem.getCamberAngles()[i] * 0.1f); // Simplified camber effect
        }
    }

    /**
     * Update advanced brake system
     */
    private void updateAdvancedBrakes(float deltaTime) {
        // Update brake cooling based on vehicle speed
        brakeSystem.updateAirflowCooling(body.getSpeed());

        // Get wheel speeds for brake calculations
        float[] wheelSpeeds = new float[4];
        for (int i = 0; i < 4; i++) {
            wheelSpeeds[i] = calculateWheelSpeed(wheels[i]);
        }

        // Calculate brake forces with fade and ABS
        float[] brakeForces = brakeSystem.calculateBrakeForces(
            brakeInput, wheelSpeeds, deltaTime
        );

        // Apply brake forces to wheels
        for (int i = 0; i < 4; i++) {
            if (brakeForces[i] > 0) {
                Vector3 brakeDirection = body.getVelocity().normalize().multiply(-1);
                Vector3 brakingForce = brakeDirection.multiply(brakeForces[i]);
                body.applyForceAtPoint(brakingForce, wheels[i].getWorldPosition());
            }
        }
    }

    /**
     * Update electronic stability control
     */
    private void updateStabilityControl(float deltaTime) {
        // Get wheel speeds for stability control
        float[] wheelSpeeds = new float[4];
        for (int i = 0; i < 4; i++) {
            wheelSpeeds[i] = calculateWheelSpeed(wheels[i]);
        }

        // Calculate yaw rate (simplified)
        Vector3 angularVel = body.getAngularVelocity();
        float yawRate = angularVel.getY();

        // Update stability control system
        ElectronicStabilityControl.StabilityControlOutput escOutput =
            stabilityControl.update(
                body.getVelocity(),
                yawRate,
                steeringAngle,
                throttleInput,
                brakeInput,
                wheelSpeeds,
                deltaTime
            );

        // Apply stability control corrections
        if (escOutput.escActive || escOutput.tcsActive || escOutput.absActive) {
            // Reduce throttle if required
            float correctedThrottle = escOutput.throttleOutput;
            if (correctedThrottle < throttleInput) {
                // Apply throttle reduction through engine
                engine.setDamageMultiplier(correctedThrottle / throttleInput);
            }

            // Apply individual wheel brake corrections
            for (int i = 0; i < 4; i++) {
                if (escOutput.brakeAdjustments[i] > 0) {
                    Vector3 brakeDirection = body.getVelocity().normalize().multiply(-1);
                    Vector3 correctionForce = brakeDirection.multiply(
                        escOutput.brakeAdjustments[i] * 5000.0f // Convert to Newtons
                    );
                    body.applyForceAtPoint(correctionForce, wheels[i].getWorldPosition());
                }
            }
        }
    }

    /**
     * Calculate wheel loads considering weight transfer
     */
    private float[] calculateWheelLoads() {
        float totalMass = body.getMass();
        Vector3 acceleration = body.getAcceleration();

        float[] loads = new float[4];
        float baseLoad = (totalMass * 9.81f) / 4.0f; // Static load per wheel

        for (int i = 0; i < 4; i++) {
            loads[i] = baseLoad;

            // Add weight transfer effects
            float weightTransferForce = calculateWeightTransfer(i, acceleration);
            loads[i] += weightTransferForce;

            // Ensure positive load
            loads[i] = Math.max(100.0f, loads[i]); // Minimum 100N load
        }

        return loads;
    }

    /**
     * Main physics update method - called every frame
     * @param deltaTime Time step in seconds
     */
    public void updatePhysicsLegacy(float deltaTime) {
        if (fuelLevel <= 0.0f) {
            throttleInput = 0.0f; // Engine stops without fuel
        }

        // Update engine RPM and torque
        updateEngine(deltaTime);

        // Update wheel positions relative to car body
        updateWheelPositions();

        // Calculate forces from each wheel
        updateWheelForces(deltaTime);

        // Apply aerodynamic forces
        applyAerodynamicForces();

        // Update transmission and drivetrain
        updateDrivetrain(deltaTime);

        // Integrate rigid body physics
        body.integrate(deltaTime);

        // Update fuel consumption
        updateFuelConsumption(deltaTime);

        // Update damage if crashed
        checkForDamage();

        // Update tire temperatures and wear
        for (Wheel wheel : wheels) {
            float wheelSpeed = calculateWheelSpeed(wheel);
            float carSpeed = body.getSpeed();
            wheel.updateSlip(wheelSpeed, carSpeed, getTrackTraction());
        }
    }

    private void updateEngine(float deltaTime) {
        // Calculate target RPM based on throttle and load
        float engineLoad = calculateEngineLoad();
        float targetRpm = engine.calculateTargetRpm(throttleInput, engineLoad, currentGear);

        // Smooth RPM transition
        float rpmDelta = (targetRpm - rpm) * 5.0f * deltaTime;
        rpm = Math.max(engine.getIdleRpm(), Math.min(engine.getRedlineRpm(), rpm + rpmDelta));

        // Update engine state
        engine.update(deltaTime, rpm, throttleInput);
    }

    private void updateWheelPositions() {
        Vector3 position = body.getPosition();
        Vector3 forward = body.getForwardDirection();
        Vector3 right = body.getRightDirection();

        // Calculate wheel positions relative to center of mass
        float frontOffset = wheelbase * (1.0f - frontWeightDistribution);
        float rearOffset = -wheelbase * frontWeightDistribution;
        float sideOffset = trackWidth * 0.5f;

        // Front wheels
        wheels[0].setWorldPosition(position.add(forward.multiply(frontOffset)).add(right.multiply(-sideOffset))); // FL
        wheels[1].setWorldPosition(position.add(forward.multiply(frontOffset)).add(right.multiply(sideOffset)));  // FR

        // Rear wheels
        wheels[2].setWorldPosition(position.add(forward.multiply(rearOffset)).add(right.multiply(-sideOffset))); // RL
        wheels[3].setWorldPosition(position.add(forward.multiply(rearOffset)).add(right.multiply(sideOffset))); // RR
    }

    private void updateWheelForces(float deltaTime) {
        float totalMass = body.getMass();
        Vector3 acceleration = body.getAcceleration();

        for (int i = 0; i < 4; i++) {
            Wheel wheel = wheels[i];

            // Calculate normal force on this wheel
            float weightTransferForce = calculateWeightTransfer(i, acceleration);
            float baseLoad = (totalMass * 9.81f) / 4.0f; // Equal distribution base
            float normalForce = baseLoad + weightTransferForce;

            // Ground contact check
            wheel.setGrounded(normalForce > 0);

            if (wheel.isGrounded()) {
                // Calculate slip angles for front wheels (steering)
                if (i < 2) { // Front wheels
                    float maxSteerAngle = (float) Math.toRadians(30); // 30 degrees max
                    wheel.setSlipAngle(steeringAngle * maxSteerAngle);
                }

                // Calculate tire forces
                Vector3 tireForce = wheel.calculateTireForces(normalForce, getTrackTraction());

                // Apply braking forces
                if (brakeInput > 0 || (handbrakeEngaged && i >= 2)) {
                    float brakeForce = brakeInput * 8000.0f; // Maximum brake force
                    Vector3 brakeDirection = body.getVelocity().normalize().multiply(-1);
                    Vector3 brakingForce = brakeDirection.multiply(brakeForce);
                    body.applyForceAtPoint(brakingForce, wheel.getWorldPosition());
                }

                // Apply tire forces to car body
                body.applyForceAtPoint(tireForce, wheel.getWorldPosition());
            }
        }
    }

    private float calculateWeightTransfer(int wheelIndex, Vector3 acceleration) {
        float lateralTransfer = 0.0f;
        float longitudinalTransfer = 0.0f;

        // Lateral weight transfer (cornering)
        if (Math.abs(acceleration.getX()) > 0.1f) {
            float transferAmount = (acceleration.getX() * body.getMass() * centerOfMassHeight) / trackWidth;
            lateralTransfer = (wheelIndex % 2 == 0) ? -transferAmount : transferAmount; // Left vs right
        }

        // Longitudinal weight transfer (acceleration/braking)
        if (Math.abs(acceleration.getZ()) > 0.1f) {
            float transferAmount = (acceleration.getZ() * body.getMass() * centerOfMassHeight) / wheelbase;
            longitudinalTransfer = (wheelIndex < 2) ? transferAmount : -transferAmount; // Front vs rear
        }

        return lateralTransfer + longitudinalTransfer;
    }

    private void applyAerodynamicForces() {
        float speed = body.getSpeed();
        float speedSquared = speed * speed;

        // Drag force (opposite to velocity direction)
        Vector3 dragForce = body.getVelocity().normalize().multiply(-aero.getDragCoefficient() * speedSquared);
        body.applyForce(dragForce);

        // Downforce (increases tire grip at high speeds)
        Vector3 downforce = Vector3.up().multiply(-aero.getDownforceCoefficient() * speedSquared);
        body.applyForce(downforce);
    }

    private void updateDrivetrain(float deltaTime) {
        if (currentGear == 0 || clutchInput > 0.5f) {
            return; // Neutral or clutch disengaged
        }

        float engineTorque = engine.getTorque(rpm);
        float gearRatio = transmission.getGearRatio(currentGear);
        float finalDriveRatio = transmission.getFinalDriveRatio();

        float wheelTorque = engineTorque * gearRatio * finalDriveRatio * transmission.getEfficiency();

        // Apply torque to driven wheels (assuming RWD for simplicity)
        Vector3 driveForce = body.getForwardDirection().multiply(wheelTorque / wheels[2].getRadius());

        // Split force between rear wheels
        body.applyForceAtPoint(driveForce.multiply(0.5f), wheels[2].getWorldPosition()); // RL
        body.applyForceAtPoint(driveForce.multiply(0.5f), wheels[3].getWorldPosition()); // RR
    }

    private void updateFuelConsumption(float deltaTime) {
        if (throttleInput > 0 && rpm > engine.getIdleRpm()) {
            float consumptionRate = engine.getFuelConsumptionRate() * throttleInput * (rpm / engine.getRedlineRpm());
            fuelLevel -= consumptionRate * deltaTime;
            fuelLevel = Math.max(0.0f, fuelLevel);
        }
    }

    private void checkForDamage() {
        Vector3 velocity = body.getVelocity();
        float impactSpeed = velocity.magnitude();

        // Simple damage model based on impact speed
        if (impactSpeed > 15.0f) { // 15 m/s = ~54 km/h
            float damageAmount = (impactSpeed - 15.0f) * 0.01f;
            damageLevel = Math.min(1.0f, damageLevel + damageAmount);

            // Damage affects performance
            if (damageLevel > 0.5f) {
                engine.setDamageMultiplier(1.0f - (damageLevel - 0.5f));
            }
        }
    }

    private float calculateWheelSpeed(Wheel wheel) {
        // Simplified wheel speed calculation
        Vector3 wheelVelocity = body.getVelocity();
        return wheelVelocity.magnitude();
    }

    private float calculateEngineLoad() {
        // Engine load based on current speed vs optimal speed for gear
        float optimalSpeed = transmission.getOptimalSpeedForGear(currentGear, rpm);
        float currentSpeed = body.getSpeedKmh();
        return Math.abs(currentSpeed - optimalSpeed) / optimalSpeed;
    }

    private float getTrackTraction() {
        // This would be determined by track surface conditions
        // For now, return a default value
        return 1.0f; // Perfect traction
    }

    // Utility methods
    public float getSpeedKmh() {
        return body.getSpeedKmh();
    }

    public float getSpeedMph() {
        return body.getSpeedKmh() * 0.621371f;
    }

    public boolean isMoving() {
        return body.isMoving();
    }

    public void stop() {
        body.stop();
        rpm = engine.getIdleRpm();
    }

    public void shiftUp() {
        if (currentGear < transmission.getGearCount()) {
            currentGear++;
        }
    }

    public void shiftDown() {
        if (currentGear > -1) {
            currentGear--;
        }
    }

    public boolean canShiftUp() {
        return currentGear < transmission.getGearCount() && rpm > engine.getShiftUpRpm();
    }

    public boolean canShiftDown() {
        return currentGear > 1 && rpm < engine.getShiftDownRpm();
    }

    /**
     * Get comprehensive vehicle telemetry data
     */
    public VehicleTelemetry getTelemetry() {
        VehicleTelemetry telemetry = new VehicleTelemetry();

        // Basic data
        telemetry.speed = body.getSpeedKmh();
        telemetry.rpm = rpm;
        telemetry.gear = currentGear;
        telemetry.throttle = throttleInput;
        telemetry.brake = brakeInput;
        telemetry.steering = steeringAngle;

        // Advanced systems data
        telemetry.brakeTemperatureFront = brakeSystem.getFrontDiscTemperature();
        telemetry.brakeTemperatureRear = brakeSystem.getRearDiscTemperature();
        telemetry.brakeEfficiency = brakeSystem.getBrakeEfficiency();
        telemetry.suspensionTravel = suspensionSystem.getSuspensionTravelPercentage();
        telemetry.escActive = stabilityControl.isEscActive();
        telemetry.tcsActive = stabilityControl.isTcsActive();
        telemetry.absActive = stabilityControl.isAbsActive();

        // Tire data
        for (int i = 0; i < 4; i++) {
            telemetry.tireTemperatures[i] = wheels[i].getTemperature();
            telemetry.tireWear[i] = wheels[i].getWear();
            telemetry.tirePressures[i] = wheels[i].getPressure();
        }

        // Physics data
        telemetry.lateralG = body.getAcceleration().getX() / 9.81f;
        telemetry.longitudinalG = body.getAcceleration().getZ() / 9.81f;
        telemetry.yawRate = body.getAngularVelocity().getY();

        return telemetry;
    }

    // Getters and setters for new systems
    public BrakeSystem getBrakeSystem() { return brakeSystem; }
    public void setBrakeSystem(BrakeSystem brakeSystem) { this.brakeSystem = brakeSystem; }

    public SuspensionSystem getSuspensionSystem() { return suspensionSystem; }
    public void setSuspensionSystem(SuspensionSystem suspensionSystem) { this.suspensionSystem = suspensionSystem; }

    public ElectronicStabilityControl getStabilityControl() { return stabilityControl; }
    public void setStabilityControl(ElectronicStabilityControl stabilityControl) { this.stabilityControl = stabilityControl; }

    /**
     * Vehicle telemetry data class
     */
    public static class VehicleTelemetry {
        public float speed;
        public float rpm;
        public int gear;
        public float throttle;
        public float brake;
        public float steering;
        public float brakeTemperatureFront;
        public float brakeTemperatureRear;
        public float brakeEfficiency;
        public float[] suspensionTravel;
        public boolean escActive;
        public boolean tcsActive;
        public boolean absActive;
        public float[] tireTemperatures = new float[4];
        public float[] tireWear = new float[4];
        public float[] tirePressures = new float[4];
        public float lateralG;
        public float longitudinalG;
        public float yawRate;
    }
}
