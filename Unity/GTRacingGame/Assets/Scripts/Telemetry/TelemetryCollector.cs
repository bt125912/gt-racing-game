using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace GTRacing.Telemetry
{
    /// <summary>
    /// Professional telemetry collection system for GT Racing Game
    /// Collects detailed vehicle performance data and uploads to backend
    /// </summary>
    public class TelemetryCollector : MonoBehaviour
    {
        [Header("Collection Settings")]
        [SerializeField] private float collectionFrequency = 10f; // Hz
        [SerializeField] private int batchSize = 100;
        [SerializeField] private bool enableRealTimeUpload = true;
        [SerializeField] private bool enableLocalLogging = true;
        [SerializeField] private float uploadInterval = 5f; // seconds

        [Header("Data Filtering")]
        [SerializeField] private float minSpeedForCollection = 5f; // km/h
        [SerializeField] private bool collectOnlyDuringRace = false;
        [SerializeField] private bool enableDataCompression = true;

        // Data storage
        private List<TelemetryDataPoint> telemetryBuffer = new List<TelemetryDataPoint>();
        private Queue<TelemetryDataPoint> uploadQueue = new Queue<TelemetryDataPoint>();
        private string currentSessionId;

        // Component references
        private CarController carController;
        private Rigidbody carRigidbody;
        private APIClient apiClient;

        // Collection state
        private bool isCollecting = false;
        private bool isUploading = false;
        private float lastCollectionTime;
        private float lastUploadTime;
        private Vector3 lastVelocity;
        private Vector3 lastPosition;

        // Performance tracking
        private int totalDataPointsCollected = 0;
        private int totalDataPointsUploaded = 0;
        private float averageCollectionTime = 0f;
        private List<float> collectionTimes = new List<float>();

        public bool IsCollecting => isCollecting;
        public int BufferSize => telemetryBuffer.Count;
        public int QueueSize => uploadQueue.Count;
        public string CurrentSessionId => currentSessionId;

        #region Initialization

        void Awake()
        {
            carController = GetComponent<CarController>();
            carRigidbody = GetComponent<Rigidbody>();
            apiClient = APIClient.Instance;
        }

        void Start()
        {
            InitializeTelemetry();
        }

        private void InitializeTelemetry()
        {
            currentSessionId = GenerateSessionId();

            if (enableLocalLogging)
            {
                CreateLocalLogFile();
            }

            // Start background upload process
            if (enableRealTimeUpload)
            {
                InvokeRepeating(nameof(ProcessUploadQueue), uploadInterval, uploadInterval);
            }
        }

        private string GenerateSessionId()
        {
            return $"telemetry_{System.DateTime.UtcNow:yyyyMMdd_HHmmss}_{System.Guid.NewGuid().ToString("N")[..8]}";
        }

        #endregion

        #region Public Interface

        public void StartCollection()
        {
            if (!isCollecting)
            {
                isCollecting = true;
                lastCollectionTime = Time.time;
                InvokeRepeating(nameof(CollectTelemetryPoint), 0f, 1f / collectionFrequency);

                Debug.Log($"Telemetry collection started - Session: {currentSessionId}");
            }
        }

        public void StopCollection()
        {
            if (isCollecting)
            {
                isCollecting = false;
                CancelInvoke(nameof(CollectTelemetryPoint));

                // Upload remaining data
                FlushTelemetryData();

                Debug.Log("Telemetry collection stopped");
            }
        }

        public void CollectData(TelemetryDataPoint dataPoint)
        {
            if (!isCollecting) return;

            // Add timestamp and session info
            dataPoint.sessionId = currentSessionId;
            dataPoint.timestamp = System.DateTime.UtcNow;
            dataPoint.localTime = Time.time;

            // Add to buffer
            telemetryBuffer.Add(dataPoint);
            totalDataPointsCollected++;

            // Check for critical conditions requiring immediate upload
            if (ShouldUploadImmediately(dataPoint))
            {
                UploadCriticalData(dataPoint);
            }

            // Manage buffer size
            if (telemetryBuffer.Count >= batchSize)
            {
                MoveBatchToUploadQueue();
            }

            // Log locally if enabled
            if (enableLocalLogging)
            {
                LogDataPointLocally(dataPoint);
            }
        }

        public void FlushTelemetryData()
        {
            if (telemetryBuffer.Count > 0)
            {
                MoveBatchToUploadQueue();
            }

            if (uploadQueue.Count > 0 && enableRealTimeUpload)
            {
                ProcessUploadQueue();
            }
        }

        public void SetSessionId(string sessionId)
        {
            currentSessionId = sessionId;
        }

        public TelemetrySessionSummary GetSessionSummary()
        {
            return new TelemetrySessionSummary
            {
                sessionId = currentSessionId,
                totalDataPoints = totalDataPointsCollected,
                uploadedDataPoints = totalDataPointsUploaded,
                averageCollectionTime = averageCollectionTime,
                bufferSize = telemetryBuffer.Count,
                queueSize = uploadQueue.Count,
                sessionDuration = Time.time - lastCollectionTime,
                isActive = isCollecting
            };
        }

        #endregion

        #region Data Collection

        private void CollectTelemetryPoint()
        {
            if (!ShouldCollectData()) return;

            float startTime = Time.realtimeSinceStartup;

            var dataPoint = new TelemetryDataPoint();

            // Basic vehicle data
            CollectBasicVehicleData(ref dataPoint);

            // Engine and drivetrain
            CollectEngineData(ref dataPoint);

            // Suspension and handling
            CollectSuspensionData(ref dataPoint);

            // Braking system
            CollectBrakingData(ref dataPoint);

            // Tires
            CollectTireData(ref dataPoint);

            // Environmental data
            CollectEnvironmentalData(ref dataPoint);

            // Electronic systems
            CollectElectronicSystemsData(ref dataPoint);

            // Performance metrics
            CollectPerformanceMetrics(ref dataPoint);

            // Add to collection
            CollectData(dataPoint);

            // Track collection performance
            float collectionTime = Time.realtimeSinceStartup - startTime;
            UpdateCollectionPerformance(collectionTime);
        }

        private void CollectBasicVehicleData(ref TelemetryDataPoint dataPoint)
        {
            if (carRigidbody != null)
            {
                dataPoint.position = carRigidbody.position;
                dataPoint.velocity = carRigidbody.velocity;
                dataPoint.angularVelocity = carRigidbody.angularVelocity;
                dataPoint.speed = carRigidbody.velocity.magnitude * 3.6f; // km/h
            }

            if (carController != null)
            {
                dataPoint.throttlePosition = carController.GetThrottleInput();
                dataPoint.brakePosition = carController.GetBrakeInput();
                dataPoint.steeringAngle = carController.GetSteerInput();
                dataPoint.clutchPosition = carController.GetClutchInput();
            }
        }

        private void CollectEngineData(ref TelemetryDataPoint dataPoint)
        {
            if (carController != null)
            {
                dataPoint.rpm = carController.GetCurrentRPM();
                dataPoint.gear = carController.GetCurrentGear();
                dataPoint.engineTemperature = carController.GetEngineTemperature();
                dataPoint.fuelLevel = carController.GetFuelLevel();
                dataPoint.fuelConsumptionRate = CalculateFuelConsumptionRate();
                dataPoint.turboBoost = carController.GetTurboBoost();
                dataPoint.oilPressure = carController.GetOilPressure();
                dataPoint.oilTemperature = carController.GetOilTemperature();
            }
        }

        private void CollectSuspensionData(ref TelemetryDataPoint dataPoint)
        {
            if (carController != null)
            {
                var wheelColliders = carController.GetWheelColliders();
                var suspensionTravel = new float[4];
                var suspensionForce = new float[4];

                for (int i = 0; i < 4 && i < wheelColliders.Length; i++)
                {
                    if (wheelColliders[i] != null)
                    {
                        WheelHit hit;
                        if (wheelColliders[i].GetGroundHit(out hit))
                        {
                            suspensionTravel[i] = (-wheelColliders[i].transform.InverseTransformPoint(hit.point).y
                                                 - wheelColliders[i].radius) / wheelColliders[i].suspensionDistance;
                            suspensionForce[i] = hit.force;
                        }
                    }
                }

                dataPoint.suspensionTravel = suspensionTravel;
                dataPoint.suspensionForce = suspensionForce;
            }
        }

        private void CollectBrakingData(ref TelemetryDataPoint dataPoint)
        {
            if (carController != null)
            {
                dataPoint.brakeTemperatureFront = carController.GetBrakeTemperature(0); // Front average
                dataPoint.brakeTemperatureRear = carController.GetBrakeTemperature(1);  // Rear average
                dataPoint.brakeTemperatures = carController.GetAllBrakeTemperatures();
                dataPoint.brakePressure = carController.GetBrakePressure();
                dataPoint.brakeWear = carController.GetBrakeWear();
            }
        }

        private void CollectTireData(ref TelemetryDataPoint dataPoint)
        {
            if (carController != null)
            {
                dataPoint.tireTemperatures = carController.GetTireTemperatures();
                dataPoint.tireWear = carController.GetTireWear();
                dataPoint.tirePressures = carController.GetTirePressures();
                dataPoint.tireSlipRatios = GetTireSlipRatios();
                dataPoint.tireGripLevels = CalculateTireGripLevels();
            }
        }

        private void CollectEnvironmentalData(ref TelemetryDataPoint dataPoint)
        {
            dataPoint.trackTemperature = GetTrackTemperature();
            dataPoint.ambientTemperature = GetAmbientTemperature();
            dataPoint.humidity = GetHumidity();
            dataPoint.windSpeed = GetWindSpeed();
            dataPoint.windDirection = GetWindDirection();
            dataPoint.trackCondition = GetTrackCondition();
        }

        private void CollectElectronicSystemsData(ref TelemetryDataPoint dataPoint)
        {
            if (carController != null)
            {
                dataPoint.escActive = carController.IsESCActive();
                dataPoint.tcsActive = carController.IsTCSActive();
                dataPoint.absActive = carController.IsABSActive();
                dataPoint.launchControlActive = carController.IsLaunchControlActive();
                dataPoint.stabilityLevel = carController.GetStabilityLevel();
                dataPoint.tractionLevel = carController.GetTractionLevel();
            }
        }

        private void CollectPerformanceMetrics(ref TelemetryDataPoint dataPoint)
        {
            // Calculate G-forces
            Vector3 acceleration = (carRigidbody.velocity - lastVelocity) / Time.fixedDeltaTime;
            Vector3 localAccel = transform.InverseTransformDirection(acceleration);

            dataPoint.lateralAcceleration = localAccel.x / 9.81f;
            dataPoint.longitudinalAcceleration = localAccel.z / 9.81f;
            dataPoint.verticalAcceleration = localAccel.y / 9.81f;

            // Calculate yaw rate
            dataPoint.yawRate = carRigidbody.angularVelocity.y;

            // Calculate distance traveled
            if (lastPosition != Vector3.zero)
            {
                float distance = Vector3.Distance(transform.position, lastPosition);
                dataPoint.distanceTraveled = distance;
            }

            // Calculate lap progress if available
            dataPoint.lapProgress = CalculateLapProgress();

            // Update last frame data
            lastVelocity = carRigidbody.velocity;
            lastPosition = transform.position;
        }

        #endregion

        #region Data Processing and Upload

        private bool ShouldCollectData()
        {
            // Don't collect if speed is too low (unless explicitly enabled)
            if (carRigidbody != null && carRigidbody.velocity.magnitude * 3.6f < minSpeedForCollection && minSpeedForCollection > 0)
            {
                return false;
            }

            // Don't collect if only during race and not racing
            if (collectOnlyDuringRace && !GameManager.Instance.IsRaceActive)
            {
                return false;
            }

            return true;
        }

        private bool ShouldUploadImmediately(TelemetryDataPoint dataPoint)
        {
            // Upload immediately for critical conditions
            return dataPoint.engineTemperature > 110f ||
                   dataPoint.brakeTemperatureFront > 450f ||
                   dataPoint.brakeTemperatureRear > 450f ||
                   dataPoint.speed > 300f ||
                   dataPoint.fuelLevel < 0.05f ||
                   (dataPoint.tireTemperatures != null && dataPoint.tireTemperatures.Max() > 110f);
        }

        private void UploadCriticalData(TelemetryDataPoint dataPoint)
        {
            if (apiClient != null)
            {
                var criticalData = new TelemetryUploadRequest
                {
                    sessionId = currentSessionId,
                    telemetryData = new List<TelemetryDataPoint> { dataPoint },
                    priority = "critical"
                };

                StartCoroutine(apiClient.UploadTelemetryBatch(criticalData,
                    (response) => Debug.Log("Critical telemetry uploaded"),
                    (error) => Debug.LogError($"Failed to upload critical telemetry: {error}")));
            }
        }

        private void MoveBatchToUploadQueue()
        {
            foreach (var dataPoint in telemetryBuffer)
            {
                uploadQueue.Enqueue(dataPoint);
            }

            telemetryBuffer.Clear();
        }

        private void ProcessUploadQueue()
        {
            if (isUploading || uploadQueue.Count == 0 || apiClient == null) return;

            isUploading = true;

            var batchToUpload = new List<TelemetryDataPoint>();
            int itemsToUpload = Mathf.Min(batchSize, uploadQueue.Count);

            for (int i = 0; i < itemsToUpload; i++)
            {
                if (uploadQueue.Count > 0)
                {
                    batchToUpload.Add(uploadQueue.Dequeue());
                }
            }

            if (batchToUpload.Count > 0)
            {
                UploadTelemetryBatch(batchToUpload);
            }
            else
            {
                isUploading = false;
            }
        }

        private void UploadTelemetryBatch(List<TelemetryDataPoint> batch)
        {
            var uploadRequest = new TelemetryUploadRequest
            {
                sessionId = currentSessionId,
                telemetryData = batch,
                compressed = enableDataCompression
            };

            StartCoroutine(apiClient.UploadTelemetryBatch(uploadRequest,
                (response) => {
                    totalDataPointsUploaded += batch.Count;
                    isUploading = false;

                    if (response.analysisAvailable)
                    {
                        RequestTelemetryAnalysis();
                    }
                },
                (error) => {
                    Debug.LogError($"Failed to upload telemetry batch: {error}");

                    // Re-queue the failed batch for retry
                    foreach (var item in batch)
                    {
                        uploadQueue.Enqueue(item);
                    }

                    isUploading = false;
                }));
        }

        private void RequestTelemetryAnalysis()
        {
            if (apiClient != null)
            {
                StartCoroutine(apiClient.GetTelemetryAnalysis(currentSessionId,
                    (analysis) => ProcessTelemetryAnalysis(analysis),
                    (error) => Debug.LogError($"Failed to get telemetry analysis: {error}")));
            }
        }

        private void ProcessTelemetryAnalysis(TelemetryAnalysisResponse analysis)
        {
            // Send analysis to UI for display
            UIManager.Instance?.UpdateTelemetryAnalysis(analysis);

            // Send to car controller for performance optimization
            if (carController != null)
            {
                carController.ApplyTelemetryRecommendations(analysis.recommendations);
            }
        }

        #endregion

        #region Local Logging

        private void CreateLocalLogFile()
        {
            string logPath = GetLocalLogPath();
            System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(logPath));

            // Write CSV header
            string header = "timestamp,speed,rpm,gear,throttle,brake,steering,engineTemp,fuelLevel," +
                           "latG,longG,yawRate,escActive,tcsActive,absActive," +
                           "tireTemp_FL,tireTemp_FR,tireTemp_RL,tireTemp_RR," +
                           "brakeTemp_F,brakeTemp_R,lapProgress\n";

            System.IO.File.WriteAllText(logPath, header);
        }

        private void LogDataPointLocally(TelemetryDataPoint dataPoint)
        {
            string logPath = GetLocalLogPath();

            string csvLine = $"{dataPoint.timestamp:O},{dataPoint.speed},{dataPoint.rpm}," +
                            $"{dataPoint.gear},{dataPoint.throttlePosition},{dataPoint.brakePosition}," +
                            $"{dataPoint.steeringAngle},{dataPoint.engineTemperature},{dataPoint.fuelLevel}," +
                            $"{dataPoint.lateralAcceleration},{dataPoint.longitudinalAcceleration}," +
                            $"{dataPoint.yawRate},{dataPoint.escActive},{dataPoint.tcsActive},{dataPoint.absActive}";

            if (dataPoint.tireTemperatures != null && dataPoint.tireTemperatures.Length >= 4)
            {
                csvLine += $",{dataPoint.tireTemperatures[0]},{dataPoint.tireTemperatures[1]}," +
                          $"{dataPoint.tireTemperatures[2]},{dataPoint.tireTemperatures[3]}";
            }
            else
            {
                csvLine += ",0,0,0,0";
            }

            csvLine += $",{dataPoint.brakeTemperatureFront},{dataPoint.brakeTemperatureRear}," +
                      $"{dataPoint.lapProgress}\n";

            try
            {
                System.IO.File.AppendAllText(logPath, csvLine);
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to write telemetry log: {e.Message}");
            }
        }

        private string GetLocalLogPath()
        {
            return System.IO.Path.Combine(Application.persistentDataPath, "telemetry", $"{currentSessionId}.csv");
        }

        #endregion

        #region Helper Methods

        private float CalculateFuelConsumptionRate()
        {
            // Calculate fuel consumption based on engine load and RPM
            if (carController != null)
            {
                float throttle = carController.GetThrottleInput();
                float rpm = carController.GetCurrentRPM();
                float baseConsumption = 0.1f; // L/h at idle

                return baseConsumption * (1f + throttle * 2f) * (rpm / 1000f);
            }

            return 0f;
        }

        private float[] GetTireSlipRatios()
        {
            var slipRatios = new float[4];

            if (carController != null)
            {
                var wheelColliders = carController.GetWheelColliders();

                for (int i = 0; i < 4 && i < wheelColliders.Length; i++)
                {
                    if (wheelColliders[i] != null)
                    {
                        WheelHit hit;
                        if (wheelColliders[i].GetGroundHit(out hit))
                        {
                            slipRatios[i] = Mathf.Sqrt(hit.forwardSlip * hit.forwardSlip + hit.sidewaysSlip * hit.sidewaysSlip);
                        }
                    }
                }
            }

            return slipRatios;
        }

        private float[] CalculateTireGripLevels()
        {
            var gripLevels = new float[4];
            var slipRatios = GetTireSlipRatios();

            for (int i = 0; i < 4; i++)
            {
                // Simplified grip calculation based on slip ratio
                gripLevels[i] = Mathf.Clamp01(1f - (slipRatios[i] * 2f));
            }

            return gripLevels;
        }

        private void UpdateCollectionPerformance(float collectionTime)
        {
            collectionTimes.Add(collectionTime);

            if (collectionTimes.Count > 100)
            {
                collectionTimes.RemoveAt(0);
            }

            averageCollectionTime = 0f;
            foreach (float time in collectionTimes)
            {
                averageCollectionTime += time;
            }
            averageCollectionTime /= collectionTimes.Count;
        }

        private float CalculateLapProgress()
        {
            // This would integrate with your track system
            // Return 0-1 representing progress around the current lap
            return GameManager.Instance?.GetLapProgress() ?? 0f;
        }

        // Environmental data methods (would integrate with weather/track systems)
        private float GetTrackTemperature() => 25f;
        private float GetAmbientTemperature() => 22f;
        private float GetHumidity() => 60f;
        private float GetWindSpeed() => 5f;
        private float GetWindDirection() => 0f;
        private string GetTrackCondition() => "dry";

        #endregion

        #region Cleanup

        void OnDestroy()
        {
            StopCollection();
        }

        void OnApplicationPause(bool pauseStatus)
        {
            if (pauseStatus)
            {
                FlushTelemetryData();
            }
        }

        void OnApplicationFocus(bool hasFocus)
        {
            if (!hasFocus)
            {
                FlushTelemetryData();
            }
        }

        #endregion
    }

    #region Data Structures

    [System.Serializable]
    public class TelemetryDataPoint
    {
        // Session info
        public string sessionId;
        public System.DateTime timestamp;
        public float localTime;

        // Vehicle position and motion
        public Vector3 position;
        public Vector3 velocity;
        public Vector3 angularVelocity;
        public float speed;
        public float distanceTraveled;
        public float lapProgress;

        // Driver inputs
        public float throttlePosition;
        public float brakePosition;
        public float steeringAngle;
        public float clutchPosition;

        // Engine and drivetrain
        public float rpm;
        public int gear;
        public float engineTemperature;
        public float fuelLevel;
        public float fuelConsumptionRate;
        public float turboBoost;
        public float oilPressure;
        public float oilTemperature;

        // Braking system
        public float brakeTemperatureFront;
        public float brakeTemperatureRear;
        public float[] brakeTemperatures;
        public float brakePressure;
        public float[] brakeWear;

        // Tires
        public float[] tireTemperatures;
        public float[] tireWear;
        public float[] tirePressures;
        public float[] tireSlipRatios;
        public float[] tireGripLevels;

        // Suspension
        public float[] suspensionTravel;
        public float[] suspensionForce;

        // Performance metrics
        public float lateralAcceleration;
        public float longitudinalAcceleration;
        public float verticalAcceleration;
        public float yawRate;

        // Electronic systems
        public bool escActive;
        public bool tcsActive;
        public bool absActive;
        public bool launchControlActive;
        public float stabilityLevel;
        public float tractionLevel;

        // Environmental
        public float trackTemperature;
        public float ambientTemperature;
        public float humidity;
        public float windSpeed;
        public float windDirection;
        public string trackCondition;
    }

    [System.Serializable]
    public class TelemetryUploadRequest
    {
        public string sessionId;
        public List<TelemetryDataPoint> telemetryData;
        public string priority = "normal";
        public bool compressed = false;
    }

    [System.Serializable]
    public class TelemetryUploadResponse
    {
        public bool success;
        public int dataPointsProcessed;
        public bool analysisAvailable;
        public string message;
    }

    [System.Serializable]
    public class TelemetryAnalysisResponse
    {
        public string sessionId;
        public float overallPerformanceRating;
        public PerformanceMetrics performance;
        public List<string> recommendations;
        public List<TelemetryAlert> alerts;
        public ComponentHealth health;
    }

    [System.Serializable]
    public class PerformanceMetrics
    {
        public float averageSpeed;
        public float maxSpeed;
        public float accelerationEfficiency;
        public float brakingEfficiency;
        public float corneringEfficiency;
        public float fuelEfficiency;
        public float consistencyRating;
    }

    [System.Serializable]
    public class ComponentHealth
    {
        public float engineHealth;
        public float brakeHealth;
        public float tireHealth;
        public float suspensionHealth;
        public float overallHealth;
    }

    [System.Serializable]
    public class TelemetryAlert
    {
        public string message;
        public string severity;
        public string component;
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class TelemetrySessionSummary
    {
        public string sessionId;
        public int totalDataPoints;
        public int uploadedDataPoints;
        public float averageCollectionTime;
        public int bufferSize;
        public int queueSize;
        public float sessionDuration;
        public bool isActive;
    }

    #endregion
}
