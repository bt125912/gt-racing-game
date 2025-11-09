using UnityEngine;
using System.Collections;
using System.Collections.Generic;

namespace GTRacing.Car
{
    /// <summary>
    /// Professional GT-style car controller with WheelColliders and backend physics integration
    /// Implements Phase 1: Hybrid Start approach with Unity physics + backend integration
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    public class CarController : MonoBehaviour, ICarController
    {
        [Header("Car Configuration")]
        [SerializeField] private CarData carData;

        [Header("Wheel Setup")]
        [SerializeField] private Transform[] wheelMeshes = new Transform[4]; // FL, FR, RL, RR
        [SerializeField] private WheelCollider[] wheelColliders = new WheelCollider[4];

        [Header("Physics Settings")]
        [SerializeField] private float maxSteerAngle = 30f;
        [SerializeField] private float maxMotorTorque = 1500f;
        [SerializeField] private float maxBrakeTorque = 3000f;
        [SerializeField] private float downforceMultiplier = 100f;
        [SerializeField] private float antiRollForce = 5000f;

        [Header("Input Settings")]
        [SerializeField] private bool useBackendPhysics = true;
        [SerializeField] private bool enableStabilityControl = true;
        [SerializeField] private bool enableTelemetryCollection = true;

        // Core Components
        private Rigidbody carRigidbody;
        private BackendPhysicsBridge physicsBridge;
        private TelemetryCollector telemetryCollector;
        private AudioController audioController;

        // Input variables
        private float motorInput;
        private float steerInput;
        private float brakeInput;
        private bool handbrakeInput;
        private bool clutchInput;

        // Performance data
        private float currentSpeed;
        private float currentRPM;
        private int currentGear = 1;
        private float engineTemperature = 90f;
        private float fuelLevel = 1f;

        // Advanced systems state
        private bool isESCActive;
        private bool isTCSActive;
        private bool isABSActive;
        private float[] brakeTemperatures = new float[4]; // FL, FR, RL, RR
        private float[] tireTemperatures = new float[4];
        private float[] tireWear = new float[4];

        // Physics simulation
        private Vector3 lastVelocity;
        private bool isGrounded;
        private float downforceAmount;

        #region Unity Lifecycle

        void Awake()
        {
            InitializeComponents();
            ConfigureWheelColliders();
            SetupPhysicsProperties();
        }

        void Start()
        {
            StartCoroutine(InitializeBackendConnection());
            if (enableTelemetryCollection)
            {
                InvokeRepeating(nameof(CollectTelemetryData), 1f, 0.1f); // 10Hz telemetry
            }
        }

        void Update()
        {
            HandleInput();
            UpdateUI();
            UpdateAudio();

            if (useBackendPhysics)
            {
                SyncWithBackend();
            }
        }

        void FixedUpdate()
        {
            // Unity physics simulation
            ApplyMotorTorque();
            ApplySteering();
            ApplyBraking();
            ApplyDownforce();
            ApplyAntiRollBars();
            UpdateWheelVisuals();

            // Advanced systems
            if (enableStabilityControl)
            {
                UpdateStabilityControl();
            }

            // Update performance metrics
            UpdatePerformanceMetrics();
        }

        void OnDestroy()
        {
            if (telemetryCollector != null)
            {
                telemetryCollector.FlushTelemetryData();
            }
        }

        #endregion

        #region Initialization

        private void InitializeComponents()
        {
            carRigidbody = GetComponent<Rigidbody>();
            physicsBridge = GetComponent<BackendPhysicsBridge>();
            telemetryCollector = GetComponent<TelemetryCollector>();
            audioController = GetComponent<AudioController>();

            // Add components if missing
            if (physicsBridge == null)
                physicsBridge = gameObject.AddComponent<BackendPhysicsBridge>();

            if (telemetryCollector == null)
                telemetryCollector = gameObject.AddComponent<TelemetryCollector>();

            if (audioController == null)
                audioController = gameObject.AddComponent<AudioController>();
        }

        private void ConfigureWheelColliders()
        {
            for (int i = 0; i < 4; i++)
            {
                if (wheelColliders[i] != null)
                {
                    var wheel = wheelColliders[i];

                    // Configure wheel collider settings
                    wheel.mass = carData != null ? carData.wheelMass : 20f;
                    wheel.radius = carData != null ? carData.wheelRadius : 0.33f;
                    wheel.wheelDampingRate = 0.25f;
                    wheel.suspensionDistance = carData != null ? carData.suspensionTravel : 0.15f;

                    // Configure suspension spring
                    var spring = wheel.suspensionSpring;
                    spring.spring = carData != null ? carData.springRate : 35000f;
                    spring.damper = carData != null ? carData.damperRate : 4500f;
                    spring.targetPosition = 0.5f;
                    wheel.suspensionSpring = spring;

                    // Configure wheel friction
                    ConfigureWheelFriction(wheel, i);
                }
            }
        }

        private void ConfigureWheelFriction(WheelCollider wheel, int wheelIndex)
        {
            // Forward friction (acceleration/braking)
            var forwardFriction = wheel.forwardFriction;
            forwardFriction.extremumSlip = 0.4f;
            forwardFriction.extremumValue = 1.0f;
            forwardFriction.asymptoteSlip = 0.8f;
            forwardFriction.asymptoteValue = 0.5f;
            forwardFriction.stiffness = carData != null ? carData.tireGrip : 1.0f;
            wheel.forwardFriction = forwardFriction;

            // Sideways friction (steering)
            var sidewaysFriction = wheel.sidewaysFriction;
            sidewaysFriction.extremumSlip = 0.25f;
            sidewaysFriction.extremumValue = 1.0f;
            sidewaysFriction.asymptoteSlip = 0.5f;
            sidewaysFriction.asymptoteValue = 0.75f;
            sidewaysFriction.stiffness = carData != null ? carData.tireGrip : 1.0f;
            wheel.sidewaysFriction = sidewaysFriction;
        }

        private void SetupPhysicsProperties()
        {
            if (carRigidbody != null)
            {
                carRigidbody.mass = carData != null ? carData.mass : 1560f;
                carRigidbody.centerOfMass = new Vector3(0, -0.5f, 0.3f);
                carRigidbody.interpolation = RigidbodyInterpolation.Interpolate;
                carRigidbody.collisionDetectionMode = CollisionDetectionMode.Continuous;
            }
        }

        private IEnumerator InitializeBackendConnection()
        {
            if (physicsBridge != null && useBackendPhysics)
            {
                yield return physicsBridge.InitializeConnection();

                if (carData != null)
                {
                    physicsBridge.SetCarConfiguration(carData);
                }
            }
        }

        #endregion

        #region Input Handling

        private void HandleInput()
        {
            // Get input from multiple sources (keyboard, gamepad, steering wheel)
            motorInput = InputManager.GetAxis("Vertical");
            steerInput = InputManager.GetAxis("Horizontal");
            brakeInput = InputManager.GetAxis("Brake");
            handbrakeInput = InputManager.GetButton("Handbrake");
            clutchInput = InputManager.GetButton("Clutch");

            // Gear shifting
            if (InputManager.GetKeyDown(KeyCode.Q))
                ShiftGear(-1);
            if (InputManager.GetKeyDown(KeyCode.E))
                ShiftGear(1);

            // Stability control toggle
            if (InputManager.GetKeyDown(KeyCode.T))
                ToggleStabilityControl();
        }

        #endregion

        #region Physics Application

        private void ApplyMotorTorque()
        {
            float motor = maxMotorTorque * motorInput;

            // Apply torque based on drive type (RWD for GT-R simulation)
            if (carData != null && carData.driveType == DriveType.AWD)
            {
                // All-wheel drive
                float frontTorque = motor * carData.frontTorqueDistribution;
                float rearTorque = motor * (1f - carData.frontTorqueDistribution);

                wheelColliders[0].motorTorque = frontTorque * 0.5f; // FL
                wheelColliders[1].motorTorque = frontTorque * 0.5f; // FR
                wheelColliders[2].motorTorque = rearTorque * 0.5f;  // RL
                wheelColliders[3].motorTorque = rearTorque * 0.5f;  // RR
            }
            else
            {
                // Rear-wheel drive (default)
                wheelColliders[0].motorTorque = 0; // FL
                wheelColliders[1].motorTorque = 0; // FR
                wheelColliders[2].motorTorque = motor * 0.5f; // RL
                wheelColliders[3].motorTorque = motor * 0.5f; // RR
            }

            currentRPM = CalculateEngineRPM();
        }

        private void ApplySteering()
        {
            float steer = maxSteerAngle * steerInput;

            // Ackermann steering geometry
            if (Mathf.Abs(steerInput) > 0.1f)
            {
                float ackermanAngleLeft, ackermanAngleRight;
                CalculateAckermannSteering(steer, out ackermanAngleLeft, out ackermanAngleRight);

                wheelColliders[0].steerAngle = ackermanAngleLeft;  // FL
                wheelColliders[1].steerAngle = ackermanAngleRight; // FR
            }
            else
            {
                wheelColliders[0].steerAngle = steer; // FL
                wheelColliders[1].steerAngle = steer; // FR
            }
        }

        private void ApplyBraking()
        {
            float brake = maxBrakeTorque * brakeInput;

            // Distribute braking force (60% front, 40% rear)
            float frontBrake = brake * 0.6f;
            float rearBrake = brake * 0.4f;

            wheelColliders[0].brakeTorque = frontBrake; // FL
            wheelColliders[1].brakeTorque = frontBrake; // FR
            wheelColliders[2].brakeTorque = rearBrake;  // RL
            wheelColliders[3].brakeTorque = rearBrake;  // RR

            // Handbrake (rear wheels only)
            if (handbrakeInput)
            {
                wheelColliders[2].brakeTorque = maxBrakeTorque * 0.8f;
                wheelColliders[3].brakeTorque = maxBrakeTorque * 0.8f;
            }
        }

        private void ApplyDownforce()
        {
            float speedSq = carRigidbody.velocity.sqrMagnitude;
            downforceAmount = downforceMultiplier * speedSq;

            // Apply downforce to improve high-speed stability
            Vector3 downforce = -transform.up * downforceAmount * Time.fixedDeltaTime;
            carRigidbody.AddForce(downforce);
        }

        private void ApplyAntiRollBars()
        {
            // Front anti-roll bar
            ApplyAntiRollBar(wheelColliders[0], wheelColliders[1], antiRollForce);

            // Rear anti-roll bar
            ApplyAntiRollBar(wheelColliders[2], wheelColliders[3], antiRollForce);
        }

        private void ApplyAntiRollBar(WheelCollider leftWheel, WheelCollider rightWheel, float antiRollForce)
        {
            WheelHit leftHit, rightHit;
            bool leftGrounded = leftWheel.GetGroundHit(out leftHit);
            bool rightGrounded = rightWheel.GetGroundHit(out rightHit);

            if (leftGrounded && rightGrounded)
            {
                float travelL = (-leftWheel.transform.InverseTransformPoint(leftHit.point).y - leftWheel.radius) / leftWheel.suspensionDistance;
                float travelR = (-rightWheel.transform.InverseTransformPoint(rightHit.point).y - rightWheel.radius) / rightWheel.suspensionDistance;

                float antiRollForceDiff = (travelL - travelR) * antiRollForce;

                if (leftGrounded)
                    carRigidbody.AddForceAtPosition(leftWheel.transform.up * -antiRollForceDiff, leftWheel.transform.position);
                if (rightGrounded)
                    carRigidbody.AddForceAtPosition(rightWheel.transform.up * antiRollForceDiff, rightWheel.transform.position);
            }
        }

        #endregion

        #region Advanced Systems

        private void UpdateStabilityControl()
        {
            // Simple stability control implementation
            Vector3 velocity = carRigidbody.velocity;
            float speed = velocity.magnitude;

            if (speed > 5f) // Only apply at meaningful speeds
            {
                Vector3 steerDirection = transform.TransformDirection(Vector3.forward);
                float angle = Vector3.Angle(steerDirection, velocity);

                // Detect oversteer/understeer
                if (angle > 15f)
                {
                    isESCActive = true;

                    // Apply corrective forces
                    Vector3 correctionForce = steerDirection * speed * 0.1f;
                    carRigidbody.AddForce(correctionForce);

                    // Reduce engine power
                    for (int i = 0; i < 4; i++)
                    {
                        wheelColliders[i].motorTorque *= 0.8f;
                    }
                }
                else
                {
                    isESCActive = false;
                }
            }
        }

        #endregion

        #region Visual Updates

        private void UpdateWheelVisuals()
        {
            for (int i = 0; i < 4; i++)
            {
                if (wheelMeshes[i] != null && wheelColliders[i] != null)
                {
                    Vector3 pos;
                    Quaternion rot;
                    wheelColliders[i].GetWorldPose(out pos, out rot);

                    wheelMeshes[i].position = pos;
                    wheelMeshes[i].rotation = rot;
                }
            }
        }

        #endregion

        #region Backend Integration

        private void SyncWithBackend()
        {
            if (physicsBridge != null && physicsBridge.IsConnected)
            {
                // Prepare Unity physics data
                var unityData = new UnityPhysicsData
                {
                    position = transform.position,
                    velocity = carRigidbody.velocity,
                    angularVelocity = carRigidbody.angularVelocity,
                    throttleInput = motorInput,
                    steerInput = steerInput,
                    brakeInput = brakeInput,
                    clutchInput = clutchInput ? 1f : 0f,
                    currentGear = currentGear,
                    rpm = currentRPM,
                    speed = currentSpeed
                };

                // Send to backend and get corrections
                var corrections = physicsBridge.UpdatePhysics(unityData, Time.fixedDeltaTime);

                if (corrections != null)
                {
                    ApplyBackendCorrections(corrections);
                }
            }
        }

        private void ApplyBackendCorrections(BackendPhysicsCorrections corrections)
        {
            // Apply ESC/TCS/ABS corrections from backend
            if (corrections.escActive)
            {
                isESCActive = true;

                // Apply brake corrections to individual wheels
                for (int i = 0; i < 4; i++)
                {
                    if (corrections.brakeAdjustments != null && i < corrections.brakeAdjustments.Length)
                    {
                        wheelColliders[i].brakeTorque += corrections.brakeAdjustments[i] * maxBrakeTorque;
                    }
                }
            }

            if (corrections.tcsActive)
            {
                isTCSActive = true;

                // Reduce motor torque
                for (int i = 0; i < 4; i++)
                {
                    wheelColliders[i].motorTorque *= corrections.throttleReduction;
                }
            }

            // Update system states
            isABSActive = corrections.absActive;

            // Update temperatures from backend
            if (corrections.brakeTemperatures != null)
            {
                brakeTemperatures = corrections.brakeTemperatures;
            }

            if (corrections.tireTemperatures != null)
            {
                tireTemperatures = corrections.tireTemperatures;
            }
        }

        #endregion

        #region Telemetry Collection

        private void CollectTelemetryData()
        {
            if (telemetryCollector != null)
            {
                var telemetryData = new TelemetryDataPoint
                {
                    timestamp = System.DateTime.UtcNow,
                    speed = currentSpeed,
                    rpm = currentRPM,
                    gear = currentGear,
                    throttlePosition = motorInput,
                    brakePosition = brakeInput,
                    steeringAngle = steerInput,
                    engineTemperature = engineTemperature,
                    brakeTemperatureFront = (brakeTemperatures[0] + brakeTemperatures[1]) * 0.5f,
                    brakeTemperatureRear = (brakeTemperatures[2] + brakeTemperatures[3]) * 0.5f,
                    tireTemperatures = tireTemperatures,
                    tireWear = tireWear,
                    fuelLevel = fuelLevel,
                    lateralAcceleration = GetLateralG(),
                    longitudinalAcceleration = GetLongitudinalG(),
                    yawRate = carRigidbody.angularVelocity.y,
                    escActive = isESCActive,
                    tcsActive = isTCSActive,
                    absActive = isABSActive
                };

                telemetryCollector.CollectData(telemetryData);
            }
        }

        #endregion

        #region Utility Methods

        private float CalculateEngineRPM()
        {
            if (wheelColliders[2] != null && wheelColliders[3] != null)
            {
                float avgWheelRPM = (wheelColliders[2].rpm + wheelColliders[3].rpm) * 0.5f;
                float gearRatio = carData != null ? carData.GetGearRatio(currentGear) : 2.0f;
                return Mathf.Abs(avgWheelRPM * gearRatio);
            }
            return 800f; // Idle RPM
        }

        private void CalculateAckermannSteering(float steerAngle, out float leftAngle, out float rightAngle)
        {
            float wheelbase = carData != null ? carData.wheelbase : 2.7f;
            float trackWidth = carData != null ? carData.trackWidth : 1.53f;

            if (steerAngle > 0) // Turning right
            {
                leftAngle = Mathf.Rad2Deg * Mathf.Atan(wheelbase / (wheelbase / Mathf.Tan(steerAngle * Mathf.Deg2Rad) + trackWidth * 0.5f));
                rightAngle = Mathf.Rad2Deg * Mathf.Atan(wheelbase / (wheelbase / Mathf.Tan(steerAngle * Mathf.Deg2Rad) - trackWidth * 0.5f));
            }
            else // Turning left
            {
                leftAngle = Mathf.Rad2Deg * Mathf.Atan(wheelbase / (wheelbase / Mathf.Tan(steerAngle * Mathf.Deg2Rad) + trackWidth * 0.5f));
                rightAngle = Mathf.Rad2Deg * Mathf.Atan(wheelbase / (wheelbase / Mathf.Tan(steerAngle * Mathf.Deg2Rad) - trackWidth * 0.5f));
            }
        }

        private float GetLateralG()
        {
            Vector3 localVelocity = transform.InverseTransformDirection(carRigidbody.velocity);
            return localVelocity.x / 9.81f;
        }

        private float GetLongitudinalG()
        {
            Vector3 acceleration = (carRigidbody.velocity - lastVelocity) / Time.fixedDeltaTime;
            Vector3 localAccel = transform.InverseTransformDirection(acceleration);
            lastVelocity = carRigidbody.velocity;
            return localAccel.z / 9.81f;
        }

        private void UpdatePerformanceMetrics()
        {
            currentSpeed = carRigidbody.velocity.magnitude * 3.6f; // Convert to km/h
            isGrounded = CheckIfGrounded();

            // Update temperatures (simplified)
            if (brakeInput > 0.1f)
            {
                for (int i = 0; i < 4; i++)
                {
                    brakeTemperatures[i] += brakeInput * 10f * Time.fixedDeltaTime;
                    brakeTemperatures[i] = Mathf.Min(brakeTemperatures[i], 500f);
                }
            }
            else
            {
                for (int i = 0; i < 4; i++)
                {
                    brakeTemperatures[i] = Mathf.Max(25f, brakeTemperatures[i] - 5f * Time.fixedDeltaTime);
                }
            }

            // Update tire temperatures based on slip
            for (int i = 0; i < 4; i++)
            {
                WheelHit hit;
                if (wheelColliders[i].GetGroundHit(out hit))
                {
                    float slip = Mathf.Abs(hit.forwardSlip) + Mathf.Abs(hit.sidewaysSlip);
                    tireTemperatures[i] += slip * 20f * Time.fixedDeltaTime;
                    tireTemperatures[i] = Mathf.Min(tireTemperatures[i], 120f);
                }
                else
                {
                    tireTemperatures[i] = Mathf.Max(25f, tireTemperatures[i] - 2f * Time.fixedDeltaTime);
                }
            }
        }

        private bool CheckIfGrounded()
        {
            int groundedWheels = 0;
            for (int i = 0; i < 4; i++)
            {
                if (wheelColliders[i].isGrounded)
                    groundedWheels++;
            }
            return groundedWheels >= 2;
        }

        #endregion

        #region Public Interface

        public void ShiftGear(int direction)
        {
            int newGear = currentGear + direction;
            newGear = Mathf.Clamp(newGear, -1, carData != null ? carData.maxGears : 6);

            if (newGear != currentGear)
            {
                currentGear = newGear;
                if (audioController != null)
                {
                    audioController.PlayGearShift();
                }
            }
        }

        public void ToggleStabilityControl()
        {
            enableStabilityControl = !enableStabilityControl;
            UIManager.Instance?.ShowMessage($"Stability Control: {(enableStabilityControl ? "ON" : "OFF")}");
        }

        public void ResetCar()
        {
            transform.position += Vector3.up * 2f;
            transform.rotation = Quaternion.LookRotation(Vector3.forward);
            carRigidbody.velocity = Vector3.zero;
            carRigidbody.angularVelocity = Vector3.zero;
        }

        public CarTelemetryData GetTelemetrySnapshot()
        {
            return new CarTelemetryData
            {
                speed = currentSpeed,
                rpm = currentRPM,
                gear = currentGear,
                brakeTemperatures = brakeTemperatures,
                tireTemperatures = tireTemperatures,
                tireWear = tireWear,
                fuelLevel = fuelLevel,
                engineTemperature = engineTemperature,
                isESCActive = isESCActive,
                isTCSActive = isTCSActive,
                isABSActive = isABSActive
            };
        }

        #endregion

        #region Audio and UI Updates

        private void UpdateAudio()
        {
            if (audioController != null)
            {
                audioController.UpdateEngineSound(currentRPM, motorInput);
                audioController.UpdateTireSound(GetMaxTireSlip());
                audioController.UpdateWindSound(currentSpeed);
            }
        }

        private void UpdateUI()
        {
            if (UIManager.Instance != null)
            {
                UIManager.Instance.UpdateSpeedometer(currentSpeed);
                UIManager.Instance.UpdateRPMGauge(currentRPM);
                UIManager.Instance.UpdateGearDisplay(currentGear);
                UIManager.Instance.UpdateFuelGauge(fuelLevel);
                UIManager.Instance.UpdateSystemStatus(isESCActive, isTCSActive, isABSActive);
            }
        }

        private float GetMaxTireSlip()
        {
            float maxSlip = 0f;
            for (int i = 0; i < 4; i++)
            {
                WheelHit hit;
                if (wheelColliders[i].GetGroundHit(out hit))
                {
                    float slip = Mathf.Abs(hit.forwardSlip) + Mathf.Abs(hit.sidewaysSlip);
                    maxSlip = Mathf.Max(maxSlip, slip);
                }
            }
            return maxSlip;
        }

        #endregion
    }
}
