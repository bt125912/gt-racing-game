using UnityEngine;
using System.Collections.Generic;

namespace GTRacing.Physics
{
    /// <summary>
    /// C# port of Java backend physics for offline use
    /// Provides advanced vehicle dynamics when backend is unavailable
    /// </summary>
    public class OfflinePhysicsEngine : MonoBehaviour
    {
        [Header("Physics Configuration")]
        [SerializeField] private bool enableAdvancedPhysics = true;
        [SerializeField] private float physicsUpdateRate = 60f;
        [SerializeField] private bool enableESC = true;
        [SerializeField] private bool enableTCS = true;
        [SerializeField] private bool enableABS = true;

        // Physics state
        private CarController carController;
        private Rigidbody carRigidbody;
        private CarData carData;

        // Advanced physics components
        private AdvancedSuspension suspensionSystem;
        private AdvancedBrakeSystem brakeSystem;
        private AdvancedEngine engineSystem;
        private ElectronicStabilitySystem stabilitySystem;
        private TirePhysicsModel tireModel;

        // Physics update timing
        private float lastPhysicsUpdate;
        private float physicsTimeStep;

        #region Initialization

        void Awake()
        {
            carController = GetComponent<CarController>();
            carRigidbody = GetComponent<Rigidbody>();

            physicsTimeStep = 1f / physicsUpdateRate;

            InitializePhysicsComponents();
        }

        void Start()
        {
            if (carController != null)
            {
                carData = carController.GetCarData();
            }

            ConfigurePhysicsComponents();
        }

        private void InitializePhysicsComponents()
        {
            // Initialize advanced physics components
            suspensionSystem = gameObject.AddComponent<AdvancedSuspension>();
            brakeSystem = gameObject.AddComponent<AdvancedBrakeSystem>();
            engineSystem = gameObject.AddComponent<AdvancedEngine>();
            stabilitySystem = gameObject.AddComponent<ElectronicStabilitySystem>();
            tireModel = gameObject.AddComponent<TirePhysicsModel>();
        }

        private void ConfigurePhysicsComponents()
        {
            if (carData != null)
            {
                suspensionSystem.Initialize(carData.suspensionData);
                brakeSystem.Initialize(carData.brakeData);
                engineSystem.Initialize(carData.engineData);
                stabilitySystem.Initialize(carData, enableESC, enableTCS, enableABS);
                tireModel.Initialize(carData.wheelData);
            }
        }

        #endregion

        #region Physics Update

        void FixedUpdate()
        {
            if (!enableAdvancedPhysics) return;

            float currentTime = Time.fixedTime;
            if (currentTime - lastPhysicsUpdate >= physicsTimeStep)
            {
                UpdateAdvancedPhysics(physicsTimeStep);
                lastPhysicsUpdate = currentTime;
            }
        }

        private void UpdateAdvancedPhysics(float deltaTime)
        {
            if (carController == null || carRigidbody == null) return;

            // Get current vehicle state
            var vehicleState = GetVehicleState();

            // Update physics systems
            engineSystem.UpdateEngine(vehicleState, deltaTime);
            suspensionSystem.UpdateSuspension(vehicleState, deltaTime);
            brakeSystem.UpdateBrakes(vehicleState, deltaTime);
            tireModel.UpdateTires(vehicleState, deltaTime);

            // Apply electronic stability systems
            if (stabilitySystem != null)
            {
                var corrections = stabilitySystem.CalculateCorrections(vehicleState, deltaTime);
                ApplyStabilityCorrections(corrections);
            }

            // Apply advanced forces
            ApplyAdvancedForces(vehicleState, deltaTime);
        }

        private VehicleState GetVehicleState()
        {
            return new VehicleState
            {
                position = transform.position,
                velocity = carRigidbody.velocity,
                angularVelocity = carRigidbody.angularVelocity,
                throttleInput = carController.GetThrottleInput(),
                brakeInput = carController.GetBrakeInput(),
                steerInput = carController.GetSteerInput(),
                clutchInput = carController.GetClutchInput(),
                currentGear = carController.GetCurrentGear(),
                rpm = carController.GetCurrentRPM(),
                speed = carController.GetCurrentSpeed(),
                wheelColliders = carController.GetWheelColliders(),
                mass = carRigidbody.mass,
                centerOfMass = carRigidbody.centerOfMass
            };
        }

        #endregion

        #region Stability System Integration

        private void ApplyStabilityCorrections(StabilityCorrections corrections)
        {
            if (corrections == null) return;

            // Apply brake corrections for ABS
            if (corrections.absActive)
            {
                var wheelColliders = carController.GetWheelColliders();
                for (int i = 0; i < 4 && i < wheelColliders.Length; i++)
                {
                    if (corrections.brakeAdjustments != null && i < corrections.brakeAdjustments.Length)
                    {
                        wheelColliders[i].brakeTorque *= corrections.brakeAdjustments[i];
                    }
                }
            }

            // Apply throttle reduction for TCS
            if (corrections.tcsActive)
            {
                var wheelColliders = carController.GetWheelColliders();
                for (int i = 0; i < wheelColliders.Length; i++)
                {
                    wheelColliders[i].motorTorque *= corrections.throttleReduction;
                }
            }

            // Apply steering corrections for ESC
            if (corrections.escActive && corrections.steeringCorrection != 0f)
            {
                Vector3 correctionForce = transform.right * corrections.steeringCorrection * carRigidbody.mass * 10f;
                carRigidbody.AddForce(correctionForce);
            }
        }

        private void ApplyAdvancedForces(VehicleState state, float deltaTime)
        {
            // Apply aerodynamic forces
            ApplyAerodynamics(state);

            // Apply suspension forces
            ApplySuspensionForces(state);

            // Apply brake heating effects
            ApplyBrakeHeatEffects(state);

            // Apply tire wear and temperature effects
            ApplyTireEffects(state);
        }

        private void ApplyAerodynamics(VehicleState state)
        {
            if (carData == null) return;

            float speedSqr = state.velocity.sqrMagnitude;

            // Drag force
            Vector3 dragForce = -state.velocity.normalized * carData.dragCoefficient *
                               carData.frontalArea * 0.5f * 1.225f * speedSqr;

            // Downforce
            Vector3 downforce = -transform.up * carData.downforceCoefficient *
                               carData.frontalArea * 0.5f * 1.225f * speedSqr;

            carRigidbody.AddForce(dragForce + downforce);
        }

        private void ApplySuspensionForces(VehicleState state)
        {
            // Advanced suspension simulation with multi-link geometry
            if (suspensionSystem != null)
            {
                var suspensionForces = suspensionSystem.CalculateSuspensionForces(state);

                for (int i = 0; i < suspensionForces.Length && i < 4; i++)
                {
                    Vector3 wheelPosition = state.wheelColliders[i].transform.position;
                    carRigidbody.AddForceAtPosition(suspensionForces[i], wheelPosition);
                }
            }
        }

        private void ApplyBrakeHeatEffects(VehicleState state)
        {
            if (brakeSystem != null)
            {
                var brakeTemps = brakeSystem.GetBrakeTemperatures();

                // Reduce brake effectiveness with heat
                for (int i = 0; i < brakeTemps.Length && i < 4; i++)
                {
                    if (brakeTemps[i] > 400f) // Brake fade starts at 400°C
                    {
                        float fadeMultiplier = Mathf.Lerp(1f, 0.6f, (brakeTemps[i] - 400f) / 200f);
                        state.wheelColliders[i].brakeTorque *= fadeMultiplier;
                    }
                }
            }
        }

        private void ApplyTireEffects(VehicleState state)
        {
            if (tireModel != null)
            {
                var tireData = tireModel.GetTireData();

                for (int i = 0; i < tireData.Length && i < 4; i++)
                {
                    var tire = tireData[i];
                    var wheelCollider = state.wheelColliders[i];

                    // Modify friction based on temperature and wear
                    var forwardFriction = wheelCollider.forwardFriction;
                    var sidewaysFriction = wheelCollider.sidewaysFriction;

                    float tempMultiplier = tire.GetTemperatureGripMultiplier();
                    float wearMultiplier = tire.GetWearGripMultiplier();

                    forwardFriction.stiffness *= tempMultiplier * wearMultiplier;
                    sidewaysFriction.stiffness *= tempMultiplier * wearMultiplier;

                    wheelCollider.forwardFriction = forwardFriction;
                    wheelCollider.sidewaysFriction = sidewaysFriction;
                }
            }
        }

        #endregion

        #region Public Interface

        public void EnableOfflineMode(bool enable)
        {
            enableAdvancedPhysics = enable;

            if (enable)
            {
                Debug.Log("Advanced offline physics enabled");
            }
            else
            {
                Debug.Log("Advanced offline physics disabled - using basic Unity physics");
            }
        }

        public void SetStabilitySystemsEnabled(bool esc, bool tcs, bool abs)
        {
            enableESC = esc;
            enableTCS = tcs;
            enableABS = abs;

            if (stabilitySystem != null)
            {
                stabilitySystem.SetSystemsEnabled(esc, tcs, abs);
            }
        }

        public PhysicsPerformanceData GetPerformanceData()
        {
            return new PhysicsPerformanceData
            {
                physicsUpdateRate = physicsUpdateRate,
                lastUpdateTime = lastPhysicsUpdate,
                systemsActive = new Dictionary<string, bool>
                {
                    {"ESC", enableESC && stabilitySystem?.IsESCActive() == true},
                    {"TCS", enableTCS && stabilitySystem?.IsTCSActive() == true},
                    {"ABS", enableABS && stabilitySystem?.IsABSActive() == true}
                }
            };
        }

        #endregion
    }

    #region Data Structures

    [System.Serializable]
    public class VehicleState
    {
        public Vector3 position;
        public Vector3 velocity;
        public Vector3 angularVelocity;
        public float throttleInput;
        public float brakeInput;
        public float steerInput;
        public float clutchInput;
        public int currentGear;
        public float rpm;
        public float speed;
        public WheelCollider[] wheelColliders;
        public float mass;
        public Vector3 centerOfMass;
    }

    [System.Serializable]
    public class StabilityCorrections
    {
        public bool escActive;
        public bool tcsActive;
        public bool absActive;
        public float throttleReduction = 1f;
        public float[] brakeAdjustments = new float[4];
        public float steeringCorrection = 0f;
        public Vector3 stabilityForce = Vector3.zero;
    }

    [System.Serializable]
    public class PhysicsPerformanceData
    {
        public float physicsUpdateRate;
        public float lastUpdateTime;
        public Dictionary<string, bool> systemsActive;
    }

    #endregion
}
