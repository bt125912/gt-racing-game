using UnityEngine;
using System.Collections.Generic;
using GTRacing.Physics;

namespace GTRacing.Physics
{
    /// <summary>
    /// Advanced Electronic Stability System for GT Racing Game
    /// Implements ESC, TCS, ABS and other electronic driving aids
    /// </summary>
    public class ElectronicStabilitySystem : MonoBehaviour
    {
        [Header("System Configuration")]
        [SerializeField] private bool escEnabled = true;
        [SerializeField] private bool tcsEnabled = true;
        [SerializeField] private bool absEnabled = true;
        [SerializeField] private bool launchControlEnabled = false;

        [Header("ESC Settings")]
        [SerializeField] private float escSensitivity = 0.5f;
        [SerializeField] private float escInterventionThreshold = 15f; // degrees
        [SerializeField] private float escMaxCorrection = 0.8f;

        [Header("TCS Settings")]
        [SerializeField] private float tcsSlipThreshold = 0.15f;
        [SerializeField] private float tcsMaxReduction = 0.7f;
        [SerializeField] private float tcsResponseTime = 0.1f;

        [Header("ABS Settings")]
        [SerializeField] private float absSlipThreshold = 0.12f;
        [SerializeField] private float absReleaseRate = 0.9f;
        [SerializeField] private float absPulseFrequency = 20f; // Hz

        [Header("Launch Control Settings")]
        [SerializeField] private float launchControlRPM = 4000f;
        [SerializeField] private float launchControlSpeed = 50f; // km/h

        // System states
        private bool escActive = false;
        private bool tcsActive = false;
        private bool absActive = false;
        private bool launchControlActive = false;

        // Internal calculations
        private Vector3 lastVelocity;
        private Vector3 lastAngularVelocity;
        private float[] wheelSlipRatios = new float[4];
        private float[] lastWheelSpeeds = new float[4];
        private float understeerAngle = 0f;
        private float oversteerAngle = 0f;

        // ABS pulse timing
        private float[] absPulseTimers = new float[4];
        private bool[] absReleased = new bool[4];

        // TCS intervention
        private float tcsInterventionAmount = 0f;
        private float tcsInterventionTimer = 0f;

        // ESC calculations
        private float yawRateDesired = 0f;
        private float yawRateActual = 0f;
        private float yawRateError = 0f;

        // Component references
        private CarController carController;
        private Rigidbody carRigidbody;
        private CarData carData;

        #region Initialization

        public void Initialize(CarData data, bool enableESC = true, bool enableTCS = true, bool enableABS = true)
        {
            carData = data;
            escEnabled = enableESC;
            tcsEnabled = enableTCS;
            absEnabled = enableABS;

            carController = GetComponent<CarController>();
            carRigidbody = GetComponent<Rigidbody>();

            Debug.Log($"Electronic Stability System initialized - ESC:{enableESC} TCS:{enableTCS} ABS:{enableABS}");
        }

        public void SetSystemsEnabled(bool esc, bool tcs, bool abs)
        {
            escEnabled = esc;
            tcsEnabled = tcs;
            absEnabled = abs;
        }

        #endregion

        #region Main Update Loop

        public StabilityCorrections CalculateCorrections(VehicleState vehicleState, float deltaTime)
        {
            var corrections = new StabilityCorrections();

            // Update vehicle state tracking
            UpdateVehicleStateTracking(vehicleState, deltaTime);

            // Calculate wheel slip ratios
            CalculateWheelSlipRatios(vehicleState);

            // Run electronic systems
            if (absEnabled)
            {
                ProcessABS(vehicleState, corrections, deltaTime);
            }

            if (tcsEnabled)
            {
                ProcessTCS(vehicleState, corrections, deltaTime);
            }

            if (escEnabled)
            {
                ProcessESC(vehicleState, corrections, deltaTime);
            }

            if (launchControlEnabled)
            {
                ProcessLaunchControl(vehicleState, corrections, deltaTime);
            }

            // Update system states
            UpdateSystemStates(corrections);

            return corrections;
        }

        #endregion

        #region Vehicle State Tracking

        private void UpdateVehicleStateTracking(VehicleState state, float deltaTime)
        {
            // Calculate actual yaw rate
            yawRateActual = state.angularVelocity.y;

            // Calculate desired yaw rate based on steering input and speed
            float steerAngle = state.steerInput * 30f; // Max 30 degrees
            float wheelbase = carData != null ? carData.wheelbase : 2.7f;

            if (state.speed > 5f) // Only calculate above 5 km/h
            {
                float speedMS = state.speed / 3.6f; // Convert km/h to m/s
                yawRateDesired = (speedMS / wheelbase) * Mathf.Tan(steerAngle * Mathf.Deg2Rad);
            }
            else
            {
                yawRateDesired = 0f;
            }

            // Calculate yaw rate error
            yawRateError = yawRateDesired - yawRateActual;

            // Calculate understeer/oversteer
            CalculateSteerCharacteristics(state);

            // Update previous frame data
            lastVelocity = state.velocity;
            lastAngularVelocity = state.angularVelocity;
        }

        private void CalculateSteerCharacteristics(VehicleState state)
        {
            if (state.speed < 10f) return; // Not meaningful at low speeds

            Vector3 velocityDirection = state.velocity.normalized;
            Vector3 carForward = transform.forward;

            // Calculate slip angle
            float slipAngle = Vector3.SignedAngle(carForward, velocityDirection, Vector3.up);

            // Determine understeer/oversteer based on steering input vs actual heading change
            if (Mathf.Abs(state.steerInput) > 0.1f)
            {
                float expectedYawRate = yawRateDesired;
                float actualYawRate = yawRateActual;

                if (Mathf.Abs(actualYawRate) < Mathf.Abs(expectedYawRate) * 0.8f)
                {
                    // Understeer - car not turning enough
                    understeerAngle = Mathf.Abs(slipAngle);
                    oversteerAngle = 0f;
                }
                else if (Mathf.Abs(actualYawRate) > Mathf.Abs(expectedYawRate) * 1.2f)
                {
                    // Oversteer - car turning too much
                    oversteerAngle = Mathf.Abs(slipAngle);
                    understeerAngle = 0f;
                }
                else
                {
                    // Neutral handling
                    understeerAngle = 0f;
                    oversteerAngle = 0f;
                }
            }
        }

        #endregion

        #region Wheel Slip Calculation

        private void CalculateWheelSlipRatios(VehicleState state)
        {
            for (int i = 0; i < 4 && i < state.wheelColliders.Length; i++)
            {
                if (state.wheelColliders[i] != null)
                {
                    WheelHit hit;
                    if (state.wheelColliders[i].GetGroundHit(out hit))
                    {
                        // Calculate longitudinal slip ratio
                        float wheelSpeed = state.wheelColliders[i].rpm * 2f * Mathf.PI * state.wheelColliders[i].radius / 60f;
                        float vehicleSpeed = Vector3.Dot(state.velocity, transform.forward);

                        if (Mathf.Abs(vehicleSpeed) > 0.1f)
                        {
                            wheelSlipRatios[i] = Mathf.Abs((wheelSpeed - vehicleSpeed) / vehicleSpeed);
                        }
                        else
                        {
                            wheelSlipRatios[i] = 0f;
                        }

                        // Store wheel speed for next frame
                        lastWheelSpeeds[i] = wheelSpeed;
                    }
                }
            }
        }

        #endregion

        #region ABS System

        private void ProcessABS(VehicleState state, StabilityCorrections corrections, float deltaTime)
        {
            bool anyWheelLocked = false;

            for (int i = 0; i < 4; i++)
            {
                // Check if wheel is locked (high slip ratio during braking)
                if (state.brakeInput > 0.1f && wheelSlipRatios[i] > absSlipThreshold)
                {
                    anyWheelLocked = true;

                    // ABS pulse logic
                    absPulseTimers[i] += deltaTime;
                    float pulseInterval = 1f / absPulseFrequency;

                    if (absPulseTimers[i] >= pulseInterval)
                    {
                        absPulseTimers[i] = 0f;
                        absReleased[i] = !absReleased[i];
                    }

                    // Apply brake pressure reduction
                    if (absReleased[i])
                    {
                        corrections.brakeAdjustments[i] = absReleaseRate;
                    }
                    else
                    {
                        corrections.brakeAdjustments[i] = 1f;
                    }
                }
                else
                {
                    // Normal braking
                    corrections.brakeAdjustments[i] = 1f;
                    absReleased[i] = false;
                    absPulseTimers[i] = 0f;
                }
            }

            corrections.absActive = anyWheelLocked;
        }

        #endregion

        #region TCS System

        private void ProcessTCS(VehicleState state, StabilityCorrections corrections, float deltaTime)
        {
            bool tractionLoss = false;
            float maxSlip = 0f;

            // Check rear wheels for traction loss (drive wheels)
            for (int i = 2; i < 4; i++) // Rear wheels
            {
                if (wheelSlipRatios[i] > tcsSlipThreshold && state.throttleInput > 0.1f)
                {
                    tractionLoss = true;
                    maxSlip = Mathf.Max(maxSlip, wheelSlipRatios[i]);
                }
            }

            if (tractionLoss)
            {
                // Calculate throttle reduction based on slip amount
                float slipAmount = (maxSlip - tcsSlipThreshold) / (0.5f - tcsSlipThreshold);
                slipAmount = Mathf.Clamp01(slipAmount);

                tcsInterventionAmount = Mathf.Lerp(1f, tcsMaxReduction, slipAmount);
                tcsInterventionTimer = tcsResponseTime;
            }
            else
            {
                // Gradually restore throttle
                tcsInterventionTimer -= deltaTime;
                if (tcsInterventionTimer <= 0f)
                {
                    tcsInterventionAmount = Mathf.MoveTowards(tcsInterventionAmount, 1f, deltaTime * 2f);
                }
            }

            corrections.tcsActive = tcsInterventionAmount < 0.98f;
            corrections.throttleReduction = tcsInterventionAmount;
        }

        #endregion

        #region ESC System

        private void ProcessESC(VehicleState state, StabilityCorrections corrections, float deltaTime)
        {
            bool escIntervention = false;

            // Check for significant yaw rate error (loss of control)
            if (state.speed > 15f && Mathf.Abs(yawRateError) > escInterventionThreshold * Mathf.Deg2Rad)
            {
                escIntervention = true;

                // Calculate steering correction
                float correctionAmount = yawRateError * escSensitivity;
                correctionAmount = Mathf.Clamp(correctionAmount, -escMaxCorrection, escMaxCorrection);
                corrections.steeringCorrection = correctionAmount;

                // Apply individual wheel braking for stability
                ApplyESCBraking(state, corrections);

                // Reduce engine power during intervention
                corrections.throttleReduction *= 0.7f;
            }

            // Check for severe understeer
            if (understeerAngle > 10f && state.steerInput > 0.5f)
            {
                escIntervention = true;

                // Apply inside rear brake to help rotation
                if (state.steerInput > 0) // Right turn
                {
                    corrections.brakeAdjustments[3] = 0.6f; // Right rear
                }
                else // Left turn
                {
                    corrections.brakeAdjustments[2] = 0.6f; // Left rear
                }
            }

            // Check for severe oversteer
            if (oversteerAngle > 8f)
            {
                escIntervention = true;

                // Apply outside front brake to reduce rotation
                if (yawRateActual > 0) // Oversteering right
                {
                    corrections.brakeAdjustments[0] = 0.5f; // Left front
                }
                else // Oversteering left
                {
                    corrections.brakeAdjustments[1] = 0.5f; // Right front
                }
            }

            corrections.escActive = escIntervention;
        }

        private void ApplyESCBraking(VehicleState state, StabilityCorrections corrections)
        {
            // Determine which wheel to brake based on yaw error direction
            if (yawRateError > 0) // Need more left rotation
            {
                // Brake right front to increase yaw rate
                corrections.brakeAdjustments[1] = 0.7f;
            }
            else // Need more right rotation
            {
                // Brake left front to increase yaw rate
                corrections.brakeAdjustments[0] = 0.7f;
            }
        }

        #endregion

        #region Launch Control System

        private void ProcessLaunchControl(VehicleState state, StabilityCorrections corrections, float deltaTime)
        {
            // Launch control only active when stationary and high throttle input
            if (state.speed < launchControlSpeed && state.throttleInput > 0.8f && state.gear > 0)
            {
                launchControlActive = true;

                // Limit RPM to launch control setting
                if (state.rpm > launchControlRPM)
                {
                    float rpmReduction = (state.rpm - launchControlRPM) / 1000f;
                    corrections.throttleReduction *= Mathf.Clamp01(1f - rpmReduction);
                }

                // Apply gentle TCS to prevent wheel spin
                for (int i = 2; i < 4; i++) // Rear wheels
                {
                    if (wheelSlipRatios[i] > 0.05f)
                    {
                        corrections.throttleReduction *= 0.9f;
                    }
                }
            }
            else
            {
                launchControlActive = false;
            }
        }

        #endregion

        #region System State Updates

        private void UpdateSystemStates(StabilityCorrections corrections)
        {
            escActive = corrections.escActive;
            tcsActive = corrections.tcsActive;
            absActive = corrections.absActive;
        }

        #endregion

        #region Public Interface

        public bool IsESCActive() => escActive;
        public bool IsTCSActive() => tcsActive;
        public bool IsABSActive() => absActive;
        public bool IsLaunchControlActive() => launchControlActive;

        public float GetStabilityLevel() => 1f - (understeerAngle + oversteerAngle) / 20f;
        public float GetTractionLevel() => 1f - (wheelSlipRatios.Max() - tcsSlipThreshold);

        public void SetESCSensitivity(float sensitivity)
        {
            escSensitivity = Mathf.Clamp01(sensitivity);
        }

        public void SetTCSAggressiveness(float aggressiveness)
        {
            tcsSlipThreshold = Mathf.Lerp(0.05f, 0.3f, 1f - aggressiveness);
        }

        public void SetABSSettings(float threshold, float releaseRate)
        {
            absSlipThreshold = Mathf.Clamp(threshold, 0.05f, 0.3f);
            absReleaseRate = Mathf.Clamp01(releaseRate);
        }

        public void EnableLaunchControl(bool enable, float rpmLimit = 4000f)
        {
            launchControlEnabled = enable;
            launchControlRPM = rpmLimit;
        }

        public ElectronicSystemStatus GetSystemStatus()
        {
            return new ElectronicSystemStatus
            {
                escEnabled = escEnabled,
                tcsEnabled = tcsEnabled,
                absEnabled = absEnabled,
                launchControlEnabled = launchControlEnabled,
                escActive = escActive,
                tcsActive = tcsActive,
                absActive = absActive,
                launchControlActive = launchControlActive,
                understeerAngle = understeerAngle,
                oversteerAngle = oversteerAngle,
                yawRateError = yawRateError,
                maxWheelSlip = wheelSlipRatios.Max(),
                tcsInterventionAmount = tcsInterventionAmount
            };
        }

        #endregion
    }

    #region Data Structures

    [System.Serializable]
    public class ElectronicSystemStatus
    {
        public bool escEnabled;
        public bool tcsEnabled;
        public bool absEnabled;
        public bool launchControlEnabled;
        public bool escActive;
        public bool tcsActive;
        public bool absActive;
        public bool launchControlActive;
        public float understeerAngle;
        public float oversteerAngle;
        public float yawRateError;
        public float maxWheelSlip;
        public float tcsInterventionAmount;
    }

    #endregion
}
