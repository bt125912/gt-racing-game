using UnityEngine;
using System.Collections.Generic;

namespace GTRacing.Car
{
    /// <summary>
    /// Car data model for GT Racing Game
    /// Contains all vehicle specifications and configuration data
    /// </summary>
    [CreateAssetMenu(fileName = "New Car Data", menuName = "GT Racing/Car Data")]
    public class CarData : ScriptableObject
    {
        [Header("Basic Information")]
        public string carId = "car_001";
        public string carName = "Nissan Skyline GT-R R34";
        public string manufacturer = "Nissan";
        public string model = "Skyline GT-R";
        public int year = 1999;
        public string category = "sport";
        public DriveType driveType = DriveType.AWD;

        [Header("Physical Properties")]
        public float mass = 1560f; // kg
        public float wheelbase = 2.67f; // meters
        public float trackWidth = 1.53f; // meters
        public float centerOfMassHeight = 0.54f; // meters
        public float frontWeightDistribution = 0.59f; // 59% front, 41% rear
        public float dragCoefficient = 0.34f;
        public float downforceCoefficient = 0.15f;
        public float frontalArea = 2.1f; // square meters

        [Header("Engine Specifications")]
        public EngineData engineData;

        [Header("Transmission")]
        public TransmissionData transmissionData;

        [Header("Suspension")]
        public SuspensionData suspensionData;

        [Header("Brakes")]
        public BrakeData brakeData;

        [Header("Wheels and Tires")]
        public WheelData wheelData;

        [Header("Performance Stats")]
        public float maxPower = 276f; // HP
        public float maxTorque = 392f; // Nm
        public float topSpeed = 250f; // km/h
        public float zeroToHundred = 5.2f; // seconds
        public float quarterMile = 13.8f; // seconds
        public int performanceRating = 500; // 0-1000

        [Header("Economic Data")]
        public long purchasePrice = 85000;
        public long currentValue = 85000;
        public float fuelCapacity = 65f; // liters
        public float fuelConsumption = 12f; // L/100km

        [Header("Visual Configuration")]
        public VisualData visualData;

        [Header("Tuning Configuration")]
        public TuningData tuningData;

        [Header("Game Balance")]
        public int difficultyRating = 3; // 1-5 (1=easy to drive, 5=expert only)
        public float stability = 0.7f; // 0-1 (how stable the car is)
        public float responsiveness = 0.8f; // 0-1 (how quickly it responds to input)

        // Runtime data
        [System.NonSerialized] public bool owned = false;
        [System.NonSerialized] public float damageLevel = 0f;
        [System.NonSerialized] public float totalDistance = 0f;
        [System.NonSerialized] public int raceCount = 0;
        [System.NonSerialized] public float fuelLevel = 1f;

        #region Calculated Properties

        public float PowerToWeightRatio => maxPower / mass;

        public float TorqueToWeightRatio => maxTorque / mass;

        public Vector3 CenterOfMass => new Vector3(0, -centerOfMassHeight,
            frontWeightDistribution * wheelbase - wheelbase * 0.5f);

        public float FrontTorqueDistribution =>
            driveType == DriveType.AWD ? 0.4f : 0f; // 40% front in AWD

        public float RearTorqueDistribution =>
            driveType == DriveType.FWD ? 0f : (driveType == DriveType.AWD ? 0.6f : 1f);

        #endregion

        #region Performance Calculations

        public void CalculatePerformanceStats()
        {
            // Calculate 0-100 km/h based on power, weight, and drivetrain
            float powerToWeight = PowerToWeightRatio;
            zeroToHundred = Mathf.Clamp(6.5f - (powerToWeight - 150f) / 50f, 3.0f, 15.0f);

            // Calculate top speed based on power and aerodynamics
            float aeroLimit = Mathf.Sqrt((maxPower * 1000f) / (0.5f * 1.225f * dragCoefficient * frontalArea));
            topSpeed = Mathf.Min(aeroLimit * 3.6f, 350f); // Convert m/s to km/h, max 350 km/h

            // Calculate quarter mile
            quarterMile = zeroToHundred * 4.2f; // Rough approximation

            // Calculate overall performance rating
            float powerScore = Mathf.Clamp01(maxPower / 800f) * 300f;
            float speedScore = Mathf.Clamp01(topSpeed / 350f) * 200f;
            float accelScore = Mathf.Clamp01((15f - zeroToHundred) / 12f) * 300f;
            float handlingScore = (1f - dragCoefficient) * 200f;

            performanceRating = Mathf.RoundToInt(powerScore + speedScore + accelScore + handlingScore);
            performanceRating = Mathf.Clamp(performanceRating, 100, 1000);
        }

        public float GetGearRatio(int gear)
        {
            if (transmissionData.gearRatios == null || gear < 0 || gear >= transmissionData.gearRatios.Length)
                return 1f;

            return transmissionData.gearRatios[gear];
        }

        public float GetRedlineRPM()
        {
            return engineData.redlineRpm;
        }

        public float GetMaxRPM()
        {
            return engineData.maxRpm;
        }

        public float GetIdleRPM()
        {
            return engineData.idleRpm;
        }

        public float GetOptimalShiftRPM()
        {
            return engineData.redlineRpm * 0.85f; // Shift at 85% of redline
        }

        #endregion

        #region Tuning Methods

        public void ApplyTuning(TuningConfiguration tuning)
        {
            if (tuning.engineUpgrades != null)
            {
                ApplyEngineUpgrades(tuning.engineUpgrades);
            }

            if (tuning.suspensionUpgrades != null)
            {
                ApplySuspensionUpgrades(tuning.suspensionUpgrades);
            }

            if (tuning.brakeUpgrades != null)
            {
                ApplyBrakeUpgrades(tuning.brakeUpgrades);
            }

            if (tuning.aeroUpgrades != null)
            {
                ApplyAeroUpgrades(tuning.aeroUpgrades);
            }

            // Recalculate performance after tuning
            CalculatePerformanceStats();
        }

        private void ApplyEngineUpgrades(EngineUpgrades upgrades)
        {
            maxPower *= upgrades.powerMultiplier;
            maxTorque *= upgrades.torqueMultiplier;
            engineData.redlineRpm += upgrades.redlineIncrease;
            engineData.turboMultiplier *= upgrades.turboBoostMultiplier;
        }

        private void ApplySuspensionUpgrades(SuspensionUpgrades upgrades)
        {
            suspensionData.frontSpringRate *= upgrades.springRateMultiplier;
            suspensionData.rearSpringRate *= upgrades.springRateMultiplier;
            suspensionData.frontDamperRate *= upgrades.damperMultiplier;
            suspensionData.rearDamperRate *= upgrades.damperMultiplier;
        }

        private void ApplyBrakeUpgrades(BrakeUpgrades upgrades)
        {
            brakeData.maxBrakeForce *= upgrades.brakeForceMultiplier;
            brakeData.frontDiscDiameter += upgrades.discDiameterIncrease;
            brakeData.rearDiscDiameter += upgrades.discDiameterIncrease;
        }

        private void ApplyAeroUpgrades(AeroUpgrades upgrades)
        {
            downforceCoefficient *= upgrades.downforceMultiplier;
            dragCoefficient += upgrades.dragIncrease;
        }

        #endregion

        #region Damage System

        public void ApplyDamage(float damage)
        {
            damageLevel += damage;
            damageLevel = Mathf.Clamp01(damageLevel);

            // Damage affects performance
            float damageMultiplier = 1f - (damageLevel * 0.3f); // Max 30% performance loss
            // Apply damage effects to power, handling, etc.
        }

        public void RepairDamage(float repairAmount)
        {
            damageLevel -= repairAmount;
            damageLevel = Mathf.Clamp01(damageLevel);
        }

        public bool NeedsRepair()
        {
            return damageLevel > 0.2f; // Needs repair at 20% damage
        }

        #endregion

        #region Data Validation

        void OnValidate()
        {
            // Ensure values are within reasonable ranges
            mass = Mathf.Clamp(mass, 500f, 3000f);
            wheelbase = Mathf.Clamp(wheelbase, 2f, 4f);
            trackWidth = Mathf.Clamp(trackWidth, 1.2f, 2.2f);
            centerOfMassHeight = Mathf.Clamp(centerOfMassHeight, 0.3f, 0.8f);
            frontWeightDistribution = Mathf.Clamp01(frontWeightDistribution);
            dragCoefficient = Mathf.Clamp(dragCoefficient, 0.2f, 1.5f);
            downforceCoefficient = Mathf.Clamp(downforceCoefficient, 0f, 2f);

            // Validate engine data
            if (engineData != null)
            {
                engineData.Validate();
            }

            // Auto-calculate performance if needed
            if (Application.isPlaying)
            {
                CalculatePerformanceStats();
            }
        }

        #endregion
    }

    #region Data Structures

    public enum DriveType
    {
        FWD, // Front Wheel Drive
        RWD, // Rear Wheel Drive
        AWD  // All Wheel Drive
    }

    [System.Serializable]
    public class EngineData
    {
        public float displacement = 2.6f; // liters
        public int cylinders = 6;
        public string configuration = "I6"; // I4, I6, V6, V8, etc.
        public string aspiration = "turbocharged"; // naturally_aspirated, turbocharged, supercharged
        public float maxPower = 276f; // HP
        public float maxTorque = 392f; // Nm
        public float redlineRpm = 7000f;
        public float maxRpm = 7500f;
        public float idleRpm = 800f;
        public AnimationCurve powerCurve; // RPM vs Power curve
        public AnimationCurve torqueCurve; // RPM vs Torque curve
        public float turboMultiplier = 1.3f;
        public float fuelConsumptionRate = 0.00012f; // liters per second at full load

        public void Validate()
        {
            redlineRpm = Mathf.Clamp(redlineRpm, 3000f, 12000f);
            maxRpm = Mathf.Max(redlineRpm, maxRpm);
            idleRpm = Mathf.Clamp(idleRpm, 600f, 1200f);
        }
    }

    [System.Serializable]
    public class TransmissionData
    {
        public TransmissionType type = TransmissionType.Manual;
        public int gearCount = 6;
        public float[] gearRatios = new float[] { 3.83f, 2.36f, 1.69f, 1.31f, 1.00f, 0.79f };
        public float finalDriveRatio = 4.11f;
        public float efficiency = 0.95f;
        public float shiftTime = 0.2f; // seconds for manual
        public float clutchEngagementTime = 0.3f;
    }

    public enum TransmissionType
    {
        Manual,
        Automatic,
        SemiAutomatic,
        CVT,
        DualClutch
    }

    [System.Serializable]
    public class SuspensionData
    {
        public float frontSpringRate = 35000f; // N/m
        public float rearSpringRate = 40000f; // N/m
        public float frontDamperRate = 4500f; // Ns/m
        public float rearDamperRate = 5000f; // Ns/m
        public float frontAntiRollBarRate = 15000f; // Nm/rad
        public float rearAntiRollBarRate = 12000f; // Nm/rad
        public float rideHeight = 120f; // mm
        public float suspensionTravel = 150f; // mm
        public SuspensionType type = SuspensionType.MultiLink;
    }

    public enum SuspensionType
    {
        MacPherson,
        DoubleWishbone,
        MultiLink,
        SolidAxle
    }

    [System.Serializable]
    public class BrakeData
    {
        public float frontDiscDiameter = 324f; // mm
        public float rearDiscDiameter = 322f; // mm
        public float frontDiscThickness = 32f; // mm
        public float rearDiscThickness = 20f; // mm
        public float frontPadArea = 45f; // cm²
        public float rearPadArea = 35f; // cm²
        public float brakeBias = 0.65f; // Front brake distribution
        public float maxBrakeForce = 12000f; // N
        public bool absEnabled = true;
        public bool ebdEnabled = true; // Electronic Brake Distribution
        public bool baEnabled = false; // Brake Assist
    }

    [System.Serializable]
    public class WheelData
    {
        public float radius = 0.33f; // meters
        public float width = 0.245f; // meters (front)
        public float rearWidth = 0.275f; // meters (rear)
        public float mass = 20f; // kg per wheel
        public float grip = 1.0f; // grip multiplier
        public float optimalPressure = 2.2f; // bar (front)
        public float rearOptimalPressure = 2.0f; // bar (rear)
        public TireType tireType = TireType.Sport;
    }

    public enum TireType
    {
        Street,
        Sport,
        SemiSlick,
        Slick,
        Wet,
        Gravel,
        Snow
    }

    [System.Serializable]
    public class VisualData
    {
        public string bodyColor = "Bayside Blue";
        public string rimColor = "Silver";
        public string interiorColor = "Black";
        public string liveryId = "stock";
        public List<string> accessories = new List<string>();
        public bool hasCustomLivery = false;
    }

    [System.Serializable]
    public class TuningData
    {
        public bool engineTunable = true;
        public bool suspensionTunable = true;
        public bool brakeTunable = true;
        public bool aeroTunable = true;
        public bool gearboxTunable = true;
        public bool differentialTunable = true;

        public float maxPowerIncrease = 1.5f; // 50% increase maximum
        public float maxWeightReduction = 0.9f; // 10% reduction maximum
    }

    #endregion

    #region Tuning Configuration Classes

    [System.Serializable]
    public class TuningConfiguration
    {
        public EngineUpgrades engineUpgrades;
        public SuspensionUpgrades suspensionUpgrades;
        public BrakeUpgrades brakeUpgrades;
        public AeroUpgrades aeroUpgrades;
        public WeightReduction weightReduction;
        public TireUpgrades tireUpgrades;
    }

    [System.Serializable]
    public class EngineUpgrades
    {
        public float powerMultiplier = 1f;
        public float torqueMultiplier = 1f;
        public float redlineIncrease = 0f;
        public float turboBoostMultiplier = 1f;
        public float fuelConsumptionMultiplier = 1f;
    }

    [System.Serializable]
    public class SuspensionUpgrades
    {
        public float springRateMultiplier = 1f;
        public float damperMultiplier = 1f;
        public float rideHeightChange = 0f;
        public float antiRollBarMultiplier = 1f;
    }

    [System.Serializable]
    public class BrakeUpgrades
    {
        public float brakeForceMultiplier = 1f;
        public float discDiameterIncrease = 0f;
        public bool carbonCeramicDiscs = false;
        public bool improvedPads = false;
    }

    [System.Serializable]
    public class AeroUpgrades
    {
        public float downforceMultiplier = 1f;
        public float dragIncrease = 0f;
        public bool frontSplitter = false;
        public bool rearWing = false;
        public bool diffuser = false;
    }

    [System.Serializable]
    public class WeightReduction
    {
        public float massReduction = 0f; // kg reduced
        public bool rollCage = false;
        public bool carbonFiberParts = false;
        public bool removeInterior = false;
    }

    [System.Serializable]
    public class TireUpgrades
    {
        public TireType tireCompound = TireType.Street;
        public float gripMultiplier = 1f;
        public float wearRateMultiplier = 1f;
        public float temperatureRange = 1f;
    }

    #endregion

    #region Interface

    public interface ICarController
    {
        float GetThrottleInput();
        float GetBrakeInput();
        float GetSteerInput();
        float GetClutchInput();
        float GetCurrentRPM();
        int GetCurrentGear();
        float GetCurrentSpeed();
        float GetEngineTemperature();
        float GetFuelLevel();
        float GetTurboBoost();
        float GetOilPressure();
        float GetOilTemperature();
        float GetBrakeTemperature(int axle);
        float[] GetAllBrakeTemperatures();
        float GetBrakePressure();
        float[] GetBrakeWear();
        float[] GetTireTemperatures();
        float[] GetTireWear();
        float[] GetTirePressures();
        WheelCollider[] GetWheelColliders();
        bool IsESCActive();
        bool IsTCSActive();
        bool IsABSActive();
        bool IsLaunchControlActive();
        float GetStabilityLevel();
        float GetTractionLevel();
        CarTelemetryData GetTelemetrySnapshot();
        void SetPhysicsMode(PhysicsMode mode);
        void ApplyTelemetryRecommendations(List<string> recommendations);
    }

    public enum PhysicsMode
    {
        UnityOnly,
        Hybrid,
        BackendOnly
    }

    [System.Serializable]
    public class CarTelemetryData
    {
        public float speed;
        public float rpm;
        public int gear;
        public float[] brakeTemperatures;
        public float[] tireTemperatures;
        public float[] tireWear;
        public float fuelLevel;
        public float engineTemperature;
        public bool isESCActive;
        public bool isTCSActive;
        public bool isABSActive;
    }

    #endregion
}
