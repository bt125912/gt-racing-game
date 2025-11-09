package com.gt.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gt.models.physics.AerodynamicsData;
import com.gt.models.physics.Engine;
import com.gt.models.physics.Transmission;
import com.gt.models.physics.Wheel;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.util.Map;

/**
 * Complete car configuration including physics, visual, and performance data
 */
@DynamoDbBean
@JsonIgnoreProperties(ignoreUnknown = true)
public class CarConfiguration {
    private String carId;
    private String ownerId; // Player who owns/configured this car
    private String baseName; // Base car name (e.g., "Nissan Skyline GT-R R34")
    private String customName; // Player's custom name for this configuration
    private String manufacturer;
    private String model;
    private int year;
    private String category; // "street", "race", "drift", "rally", "formula", "prototype"
    private String driveType; // "fwd", "rwd", "awd"

    // Physics Configuration
    private float mass; // Total car mass in kg
    private float wheelbase;
    private float trackWidth;
    private float centerOfMassHeight;
    private float frontWeightDistribution;
    private Engine engine;
    private Transmission transmission;
    private AerodynamicsData aerodynamics;
    private TireConfiguration tires;
    private SuspensionConfiguration suspension;
    private BrakeConfiguration brakes;

    // Performance Stats
    private float powerToWeightRatio;
    private float zeroToHundredTime; // 0-100 km/h acceleration time
    private float topSpeed; // Maximum speed in km/h
    private float brakingDistance; // 100-0 km/h braking distance in meters
    private float skidpadGrip; // Lateral grip in G's
    private float nurburgringTime; // Theoretical Nürburgring lap time

    // Visual Configuration
    private String bodyColor;
    private String rimColor;
    private String interiorColor;
    private VisualModifications visualMods;
    private String liveryId; // Custom livery/decal configuration

    // Tuning Configuration
    private TuningSettings tuning;

    // Ownership and Usage
    private long purchasePrice;
    private long currentValue;
    private float damageLevel;
    private long totalDistance; // Total distance driven in meters
    private int raceCount; // Number of races with this car
    private Instant createdAt;
    private Instant lastUsed;
    private boolean isLocked; // Locked for tuning changes during events

    public CarConfiguration() {
        this.category = "street";
        this.driveType = "rwd";
        this.mass = 1500.0f;
        this.wheelbase = 2.7f;
        this.trackWidth = 1.5f;
        this.centerOfMassHeight = 0.5f;
        this.frontWeightDistribution = 0.55f;
        this.engine = new Engine();
        this.transmission = new Transmission();
        this.aerodynamics = new AerodynamicsData();
        this.tires = new TireConfiguration();
        this.suspension = new SuspensionConfiguration();
        this.brakes = new BrakeConfiguration();
        this.tuning = new TuningSettings();
        this.visualMods = new VisualModifications();
        this.damageLevel = 0.0f;
        this.totalDistance = 0L;
        this.raceCount = 0;
        this.createdAt = Instant.now();
        this.lastUsed = Instant.now();
        this.isLocked = false;

        calculatePerformanceStats();
    }

    // Getters and Setters
    @DynamoDbPartitionKey
    @JsonProperty("carId")
    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    @DynamoDbSortKey
    @JsonProperty("ownerId")
    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    // ...existing getters and setters...

    public float getDamageLevel() {
        return damageLevel;
    }

    public void setDamageLevel(float damageLevel) {
        this.damageLevel = Math.max(0.0f, Math.min(1.0f, damageLevel));
        updateCurrentValue();
    }

    public long getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(long totalDistance) {

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    // Nested configuration classes
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TireConfiguration {
        private String compound; // "soft", "medium", "hard", "wet", "slick"
        private float frontPressure; // Bar
        private float rearPressure; // Bar
        private float frontCamber; // Degrees
        private float rearCamber; // Degrees
        private float frontToe; // Degrees
        private float rearToe; // Degrees
        private String tireSize; // e.g., "225/45R17"
        private String rimSize; // e.g., "17x8"

        public TireConfiguration() {
            this.compound = "medium";
            this.frontPressure = 2.2f;
            this.rearPressure = 2.2f;
            this.frontCamber = -1.5f;
            this.rearCamber = -1.0f;
            this.frontToe = 0.0f;
            this.rearToe = 0.2f;
            this.tireSize = "225/45R17";
            this.rimSize = "17x8";
        }

        // Getters and setters
        public String getCompound() { return compound; }
        public void setCompound(String compound) { this.compound = compound; }

        public float getFrontPressure() { return frontPressure; }
        public void setFrontPressure(float frontPressure) { this.frontPressure = frontPressure; }

        public float getRearPressure() { return rearPressure; }
        public void setRearPressure(float rearPressure) { this.rearPressure = rearPressure; }

        public float getFrontCamber() { return frontCamber; }
        public void setFrontCamber(float frontCamber) { this.frontCamber = frontCamber; }

        public float getRearCamber() { return rearCamber; }
        public void setRearCamber(float rearCamber) { this.rearCamber = rearCamber; }

        public float getFrontToe() { return frontToe; }
        public void setFrontToe(float frontToe) { this.frontToe = frontToe; }

        public float getRearToe() { return rearToe; }
        public void setRearToe(float rearToe) { this.rearToe = rearToe; }

        public String getTireSize() { return tireSize; }
        public void setTireSize(String tireSize) { this.tireSize = tireSize; }

        public String getRimSize() { return rimSize; }
        public void setRimSize(String rimSize) { this.rimSize = rimSize; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SuspensionConfiguration {
        private float frontSpringRate; // N/mm
        private float rearSpringRate; // N/mm
        private float frontDamperCompression; // Clicks or %
        private float frontDamperRebound; // Clicks or %
        private float rearDamperCompression; // Clicks or %
        private float rearDamperRebound; // Clicks or %
        private float frontAntiRollBar; // Stiffness setting
        private float rearAntiRollBar; // Stiffness setting
        private float rideHeight; // mm from ground

        public SuspensionConfiguration() {
            this.frontSpringRate = 35.0f; // kN/m
            this.rearSpringRate = 40.0f; // kN/m
            this.frontDamperCompression = 50.0f; // %
            this.frontDamperRebound = 50.0f; // %
            this.rearDamperCompression = 50.0f; // %
            this.rearDamperRebound = 50.0f; // %
            this.frontAntiRollBar = 50.0f; // %
            this.rearAntiRollBar = 50.0f; // %
            this.rideHeight = 120.0f; // mm
        }

        // Getters and setters
        public float getFrontSpringRate() { return frontSpringRate; }
        public void setFrontSpringRate(float frontSpringRate) { this.frontSpringRate = frontSpringRate; }

        public float getRearSpringRate() { return rearSpringRate; }
        public void setRearSpringRate(float rearSpringRate) { this.rearSpringRate = rearSpringRate; }

        public float getFrontDamperCompression() { return frontDamperCompression; }
        public void setFrontDamperCompression(float frontDamperCompression) { this.frontDamperCompression = frontDamperCompression; }

        public float getFrontDamperRebound() { return frontDamperRebound; }
        public void setFrontDamperRebound(float frontDamperRebound) { this.frontDamperRebound = frontDamperRebound; }

        public float getRearDamperCompression() { return rearDamperCompression; }
        public void setRearDamperCompression(float rearDamperCompression) { this.rearDamperCompression = rearDamperCompression; }

        public float getRearDamperRebound() { return rearDamperRebound; }
        public void setRearDamperRebound(float rearDamperRebound) { this.rearDamperRebound = rearDamperRebound; }

        public float getFrontAntiRollBar() { return frontAntiRollBar; }
        public void setFrontAntiRollBar(float frontAntiRollBar) { this.frontAntiRollBar = frontAntiRollBar; }

        public float getRearAntiRollBar() { return rearAntiRollBar; }
        public void setRearAntiRollBar(float rearAntiRollBar) { this.rearAntiRollBar = rearAntiRollBar; }

        public float getRideHeight() { return rideHeight; }
        public void setRideHeight(float rideHeight) { this.rideHeight = rideHeight; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrakeConfiguration {
        private float frontBrakeBias; // 0.0-1.0 (1.0 = all front)
        private float frontBrakeForce; // Maximum brake force
        private float rearBrakeForce; // Maximum brake force
        private String frontPadCompound; // Brake pad compound
        private String rearPadCompound; // Brake pad compound
        private float frontDiscDiameter; // mm
        private float rearDiscDiameter; // mm
        private boolean absEnabled; // Anti-lock braking system

        public BrakeConfiguration() {
            this.frontBrakeBias = 0.6f; // 60% front bias
            this.frontBrakeForce = 8000.0f; // N
            this.rearBrakeForce = 5000.0f; // N
            this.frontPadCompound = "medium";
            this.rearPadCompound = "medium";
            this.frontDiscDiameter = 330.0f; // mm
            this.rearDiscDiameter = 310.0f; // mm
            this.absEnabled = true;
        }

        // Getters and setters
        public float getFrontBrakeBias() { return frontBrakeBias; }
        public void setFrontBrakeBias(float frontBrakeBias) { this.frontBrakeBias = frontBrakeBias; }

        public float getFrontBrakeForce() { return frontBrakeForce; }
        public void setFrontBrakeForce(float frontBrakeForce) { this.frontBrakeForce = frontBrakeForce; }

        public float getRearBrakeForce() { return rearBrakeForce; }
        public void setRearBrakeForce(float rearBrakeForce) { this.rearBrakeForce = rearBrakeForce; }

        public String getFrontPadCompound() { return frontPadCompound; }
        public void setFrontPadCompound(String frontPadCompound) { this.frontPadCompound = frontPadCompound; }

        public String getRearPadCompound() { return rearPadCompound; }
        public void setRearPadCompound(String rearPadCompound) { this.rearPadCompound = rearPadCompound; }

        public float getFrontDiscDiameter() { return frontDiscDiameter; }
        public void setFrontDiscDiameter(float frontDiscDiameter) { this.frontDiscDiameter = frontDiscDiameter; }

        public float getRearDiscDiameter() { return rearDiscDiameter; }
        public void setRearDiscDiameter(float rearDiscDiameter) { this.rearDiscDiameter = rearDiscDiameter; }
package com.gt.models;
        public boolean isAbsEnabled() { return absEnabled; }
        public void setAbsEnabled(boolean absEnabled) { this.absEnabled = absEnabled; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VisualModifications {
        private String frontBumper;
        private String rearBumper;
        private String sideSplitters;
        private String rearWing;
        private String hood;
        private String fenders;
        private String rollCage;
        private String windowTint; // Percentage
        private Map<String, Boolean> modifications; // Additional visual mods

        public VisualModifications() {
            this.frontBumper = "stock";
            this.rearBumper = "stock";
            this.sideSplitters = "stock";
            this.rearWing = "stock";
            this.hood = "stock";
            this.fenders = "stock";
            this.rollCage = "none";
            this.windowTint = "0%";
        }

        // Getters and setters
        public String getFrontBumper() { return frontBumper; }
        public void setFrontBumper(String frontBumper) { this.frontBumper = frontBumper; }

        public String getRearBumper() { return rearBumper; }
        public void setRearBumper(String rearBumper) { this.rearBumper = rearBumper; }

        public String getSideSplitters() { return sideSplitters; }
        public void setSideSplitters(String sideSplitters) { this.sideSplitters = sideSplitters; }

        public String getRearWing() { return rearWing; }
        public void setRearWing(String rearWing) { this.rearWing = rearWing; }

        public String getHood() { return hood; }
        public void setHood(String hood) { this.hood = hood; }

        public String getFenders() { return fenders; }
        public void setFenders(String fenders) { this.fenders = fenders; }

        public String getRollCage() { return rollCage; }
        public void setRollCage(String rollCage) { this.rollCage = rollCage; }

        public String getWindowTint() { return windowTint; }
        public void setWindowTint(String windowTint) { this.windowTint = windowTint; }

        public Map<String, Boolean> getModifications() { return modifications; }
        public void setModifications(Map<String, Boolean> modifications) { this.modifications = modifications; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TuningSettings {
        private Map<String, Float> engineTuning; // ECU, turbo, exhaust settings
        private Map<String, Float> chassisTuning; // Weight reduction, balance adjustments
        private int performancePoints; // Total performance points used
        private int maxPerformancePoints; // Maximum allowed for this car class

        public TuningSettings() {
            this.performancePoints = 0;
            this.maxPerformancePoints = 1000;
        }

        // Getters and setters
        public Map<String, Float> getEngineTuning() { return engineTuning; }
        public void setEngineTuning(Map<String, Float> engineTuning) { this.engineTuning = engineTuning; }

        public Map<String, Float> getChassisTuning() { return chassisTuning; }
        public void setChassisTuning(Map<String, Float> chassisTuning) { this.chassisTuning = chassisTuning; }

        public int getPerformancePoints() { return performancePoints; }
        public void setPerformancePoints(int performancePoints) { this.performancePoints = performancePoints; }

        public int getMaxPerformancePoints() { return maxPerformancePoints; }
        public void setMaxPerformancePoints(int maxPerformancePoints) { this.maxPerformancePoints = maxPerformancePoints; }
    }

    // Utility methods
    private void calculatePerformanceStats() {
        if (engine != null && mass > 0) {
            powerToWeightRatio = engine.getMaxPower() / (mass / 1000.0f); // HP per ton

            // Simplified performance calculations
            zeroToHundredTime = Math.max(2.0f, 10.0f - (powerToWeightRatio / 100.0f));
            topSpeed = Math.min(400.0f, engine.getMaxPower() * 0.5f + 100.0f);
            brakingDistance = Math.max(30.0f, 50.0f - (powerToWeightRatio / 20.0f));
            skidpadGrip = Math.min(2.0f, 0.8f + (powerToWeightRatio / 1000.0f));

            // Estimated Nürburgring lap time (very simplified)
            nurburgringTime = Math.max(360.0f, 500.0f - powerToWeightRatio);
        }
    }

    private void updateCurrentValue() {
        if (purchasePrice > 0) {
            // Depreciation based on damage and mileage
            float depreciationFactor = 1.0f - (damageLevel * 0.5f);
            float mileageDepreciation = Math.max(0.5f, 1.0f - (totalDistance / 1000000.0f)); // Depreciation per million meters
            currentValue = (long) (purchasePrice * depreciationFactor * mileageDepreciation);
        }
    }

    public void addRace(float distance) {
        this.raceCount++;
        this.totalDistance += distance;
        this.lastUsed = Instant.now();
        updateCurrentValue();
    }

    public boolean canAffordUpgrade(long upgradeCost) {
        // This would check against player's credits in a real implementation
        return upgradeCost <= currentValue;
    }

    public float getPerformanceRating() {
        // Overall performance rating from 0-100
        float powerRating = Math.min(100, powerToWeightRatio / 10.0f);
        float handlingRating = Math.min(100, skidpadGrip * 50.0f);
        float speedRating = Math.min(100, topSpeed / 4.0f);
        float brakingRating = Math.min(100, (100 - brakingDistance) * 2.0f);

        return (powerRating + handlingRating + speedRating + brakingRating) / 4.0f;
    }
}
    private String carId;
    private String ownerId; // Player who owns/configured this car
    private String baseName; // Base car name (e.g., "Nissan Skyline GT-R R34")
    private String customName; // Player's custom name for this configuration
    private String manufacturer;
    private String model;
    private int year;
    private String category; // "street", "race", "drift", "rally", "formula", "prototype"
    private String driveType; // "fwd", "rwd", "awd"

    // Physics Configuration
    private float mass; // Total car mass in kg
    private float wheelbase;
    private float trackWidth;
    private float centerOfMassHeight;
    private float frontWeightDistribution;
    private Engine engine;
    private Transmission transmission;
    private AerodynamicsData aerodynamics;
    private TireConfiguration tires;
    private SuspensionConfiguration suspension;
    private BrakeConfiguration brakes;

    // Performance Stats
    private float powerToWeightRatio;
    private float zeroToHundredTime; // 0-100 km/h acceleration time
    private float topSpeed; // Maximum speed in km/h
    private float brakingDistance; // 100-0 km/h braking distance in meters
    private float skidpadGrip; // Lateral grip in G's
    private float nurburgringTime; // Theoretical Nürburgring lap time

    // Visual Configuration
    private String bodyColor;
    private String rimColor;
    private String interiorColor;
    private VisualModifications visualMods;
    private String liveryId; // Custom livery/decal configuration

    // Tuning Configuration
    private TuningSettings tuning;

    // Ownership and Usage
    private long purchasePrice;
    private long currentValue;
    private float damageLevel;
    private long totalDistance; // Total distance driven in meters
    private int raceCount; // Number of races with this car
    private Instant createdAt;
    private Instant lastUsed;
    private boolean isLocked; // Locked for tuning changes during events

    public CarConfiguration() {
        this.category = "street";
        this.driveType = "rwd";
        this.mass = 1500.0f;
        this.wheelbase = 2.7f;
        this.trackWidth = 1.5f;
        this.centerOfMassHeight = 0.5f;
        this.frontWeightDistribution = 0.55f;
        this.engine = new Engine();
        this.transmission = new Transmission();
        this.aerodynamics = new AerodynamicsData();
        this.tires = new TireConfiguration();
        this.suspension = new SuspensionConfiguration();
        this.brakes = new BrakeConfiguration();
        this.tuning = new TuningSettings();
        this.visualMods = new VisualModifications();
        this.damageLevel = 0.0f;
        this.totalDistance = 0L;
        this.raceCount = 0;
        this.createdAt = Instant.now();
        this.lastUsed = Instant.now();
        this.isLocked = false;

        calculatePerformanceStats();
    }

    // Getters and Setters
    @DynamoDbPartitionKey
    @JsonProperty("carId")
    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    @DynamoDbSortKey
    @JsonProperty("ownerId")
    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDriveType() {
        return driveType;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
        calculatePerformanceStats();
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
        this.frontWeightDistribution = frontWeightDistribution;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
        calculatePerformanceStats();
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public void setTransmission(Transmission transmission) {
        this.transmission = transmission;
    }

    public AerodynamicsData getAerodynamics() {
        return aerodynamics;
    }

    public void setAerodynamics(AerodynamicsData aerodynamics) {
        this.aerodynamics = aerodynamics;
    }

    public TireConfiguration getTires() {
        return tires;
    }

    public void setTires(TireConfiguration tires) {
        this.tires = tires;
    }

    public SuspensionConfiguration getSuspension() {
        return suspension;
    }

    public void setSuspension(SuspensionConfiguration suspension) {
        this.suspension = suspension;
    }

    public BrakeConfiguration getBrakes() {
        return brakes;
    }

    public void setBrakes(BrakeConfiguration brakes) {
        this.brakes = brakes;
    }

    public float getPowerToWeightRatio() {
        return powerToWeightRatio;
    }

    public float getZeroToHundredTime() {
        return zeroToHundredTime;
    }

    public float getTopSpeed() {
        return topSpeed;
    }

    public float getBrakingDistance() {
        return brakingDistance;
    }

    public float getSkidpadGrip() {
        return skidpadGrip;
    }

    public float getNurburgringTime() {
        return nurburgringTime;
    }

    public String getBodyColor() {
        return bodyColor;
    }

    public void setBodyColor(String bodyColor) {
        this.bodyColor = bodyColor;
    }

    public String getRimColor() {
        return rimColor;
    }

    public void setRimColor(String rimColor) {
        this.rimColor = rimColor;
    }

    public String getInteriorColor() {
        return interiorColor;
    }

    public void setInteriorColor(String interiorColor) {
        this.interiorColor = interiorColor;
    }

    public VisualModifications getVisualMods() {
        return visualMods;
    }

    public void setVisualMods(VisualModifications visualMods) {
        this.visualMods = visualMods;
    }

    public String getLiveryId() {
        return liveryId;
    }

    public void setLiveryId(String liveryId) {
        this.liveryId = liveryId;
    }

    public TuningSettings getTuning() {
        return tuning;
    }

    public void setTuning(TuningSettings tuning) {
        this.tuning = tuning;
        calculatePerformanceStats();
    }

    public long getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(long purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(long currentValue) {
        this.currentValue = currentValue;
    }

    public float getDamageLevel() {
        return damageLevel;
    }

    public void setDamageLevel(float damageLevel) {
        this.damageLevel = Math.max(0.0f, Math.min(1.0f, damageLevel));
        updateCurrentValue();
    }

    public long getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(long totalDistance) {

