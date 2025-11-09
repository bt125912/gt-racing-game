using UnityEngine;
using GTRacing.Car;

[CreateAssetMenu(fileName = "TestCarData", menuName = "GT Racing/Test Car Data")]
public class TestCarData : ScriptableObject
{
    [Header("Basic Car Configuration")]
    public string carId = "test_car_001";
    public string carName = "Test GT-R";
    public float mass = 1560f;
    public float maxPower = 276f;
    public float wheelbase = 2.67f;
    public float trackWidth = 1.53f;

    [Header("Wheel Settings")]
    public float wheelMass = 20f;
    public float wheelRadius = 0.33f;
    public float tireGrip = 1.0f;
    public float suspensionTravel = 0.15f;

    [Header("Physics Settings")]
    public float springRate = 35000f;
    public float damperRate = 4500f;
    public float dragCoefficient = 0.34f;
    public float downforceCoefficient = 0.15f;

    public CarData ToCarData()
    {
        var carData = ScriptableObject.CreateInstance<CarData>();

        // Basic info
        carData.carId = this.carId;
        carData.carName = this.carName;
        carData.mass = this.mass;
        carData.maxPower = this.maxPower;
        carData.wheelbase = this.wheelbase;
        carData.trackWidth = this.trackWidth;
        carData.dragCoefficient = this.dragCoefficient;
        carData.downforceCoefficient = this.downforceCoefficient;

        // Initialize engine data
        carData.engineData = new EngineData
        {
            maxPower = this.maxPower,
            maxTorque = 392f,
            redlineRpm = 7000f,
            idleRpm = 800f,
            displacement = 2.6f
        };

        // Initialize transmission data
        carData.transmissionData = new TransmissionData
        {
            type = TransmissionType.Manual,
            gearCount = 6,
            gearRatios = new float[] { 3.83f, 2.36f, 1.69f, 1.31f, 1.00f, 0.79f },
            finalDriveRatio = 4.11f,
            efficiency = 0.95f
        };

        // Initialize suspension data
        carData.suspensionData = new SuspensionData
        {
            frontSpringRate = this.springRate,
            rearSpringRate = this.springRate * 1.1f,
            frontDamperRate = this.damperRate,
            rearDamperRate = this.damperRate * 1.1f,
            rideHeight = 120f
        };

        // Initialize brake data
        carData.brakeData = new BrakeData
        {
            frontDiscDiameter = 324f,
            rearDiscDiameter = 322f,
            maxBrakeForce = 12000f,
            brakeBias = 0.65f,
            absEnabled = true
        };

        // Initialize wheel data
        carData.wheelData = new WheelData
        {
            radius = this.wheelRadius,
            mass = this.wheelMass,
            grip = this.tireGrip,
            optimalPressure = 2.2f
        };

        return carData;
    }
}
