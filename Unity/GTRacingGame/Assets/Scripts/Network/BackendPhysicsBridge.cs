using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.Threading.Tasks;
using Newtonsoft.Json;

namespace GTRacing.Network
{
    /// <summary>
    /// Bridge between Unity WheelCollider physics and Java backend advanced physics
    /// Implements hybrid approach: Unity for basic simulation, backend for advanced features
    /// </summary>
    public class BackendPhysicsBridge : MonoBehaviour
    {
        [Header("Backend Configuration")]
        [SerializeField] private string backendApiUrl = "https://your-api-gateway.amazonaws.com/dev";
        [SerializeField] private bool enableBackendPhysics = true;
        [SerializeField] private float syncFrequency = 30f; // Hz
        [SerializeField] private bool useBackendForESC = true;
        [SerializeField] private bool useBackendForTelemetry = true;

        [Header("Physics Sync Settings")]
        [SerializeField] private float positionTolerance = 0.1f;
        [SerializeField] private float velocityTolerance = 1f;
        [SerializeField] private float correctionSmoothingTime = 0.2f;

        // Connection status
        private bool isConnected = false;
        private bool isInitialized = false;
        private string authToken;
        private string sessionId;

        // Physics sync
        private CarData currentCarData;
        private Vector3 targetPosition;
        private Vector3 targetVelocity;
        private float lastSyncTime;

        // Backend response data
        private BackendPhysicsCorrections lastCorrections;
        private Queue<UnityPhysicsData> physicsDataQueue = new Queue<UnityPhysicsData>();

        // Network client
        private APIClient apiClient;
        private WebSocketClient wsClient;

        public bool IsConnected => isConnected;
        public bool IsInitialized => isInitialized;

        #region Initialization

        void Awake()
        {
            apiClient = new APIClient(backendApiUrl);
            InitializeWebSocket();
        }

        void Start()
        {
            StartCoroutine(InitializeConnection());
        }

        public IEnumerator InitializeConnection()
        {
            yield return StartCoroutine(AuthenticateWithBackend());

            if (isConnected)
            {
                yield return StartCoroutine(CreatePhysicsSession());
                InvokeRepeating(nameof(ProcessPhysicsSync), 1f / syncFrequency, 1f / syncFrequency);
            }
        }

        private IEnumerator AuthenticateWithBackend()
        {
            var authRequest = new AuthenticationRequest
            {
                deviceId = SystemInfo.deviceUniqueIdentifier,
                gameVersion = Application.version,
                platform = Application.platform.ToString()
            };

            string jsonData = JsonConvert.SerializeObject(authRequest);

            using (var request = apiClient.Post("/auth/device", jsonData))
            {
                yield return request.SendWebRequest();

                if (request.result == UnityWebRequest.Result.Success)
                {
                    var authResponse = JsonConvert.DeserializeObject<AuthenticationResponse>(request.downloadHandler.text);
                    authToken = authResponse.token;
                    isConnected = true;

                    Debug.Log("Backend authentication successful");
                }
                else
                {
                    Debug.LogError($"Backend authentication failed: {request.error}");
                    isConnected = false;
                }
            }
        }

        private IEnumerator CreatePhysicsSession()
        {
            if (!isConnected) yield break;

            var sessionRequest = new PhysicsSessionRequest
            {
                carId = currentCarData?.carId ?? "default_car",
                enableAdvancedPhysics = enableBackendPhysics,
                syncFrequency = syncFrequency
            };

            string jsonData = JsonConvert.SerializeObject(sessionRequest);

            using (var request = apiClient.Post("/physics/session", jsonData, authToken))
            {
                yield return request.SendWebRequest();

                if (request.result == UnityWebRequest.Result.Success)
                {
                    var sessionResponse = JsonConvert.DeserializeObject<PhysicsSessionResponse>(request.downloadHandler.text);
                    sessionId = sessionResponse.sessionId;
                    isInitialized = true;

                    Debug.Log($"Physics session created: {sessionId}");
                }
                else
                {
                    Debug.LogError($"Failed to create physics session: {request.error}");
                }
            }
        }

        private void InitializeWebSocket()
        {
            if (!string.IsNullOrEmpty(backendApiUrl))
            {
                string wsUrl = backendApiUrl.Replace("https://", "wss://").Replace("http://", "ws://");
                wsClient = new WebSocketClient(wsUrl);
                wsClient.OnMessage += HandleWebSocketMessage;
                wsClient.OnError += (error) => Debug.LogError($"WebSocket error: {error}");
            }
        }

        #endregion

        #region Physics Integration

        public BackendPhysicsCorrections UpdatePhysics(UnityPhysicsData unityData, float deltaTime)
        {
            if (!isInitialized || !enableBackendPhysics)
                return null;

            // Queue physics data for batch processing
            physicsDataQueue.Enqueue(unityData);

            // Return last received corrections
            return lastCorrections;
        }

        private void ProcessPhysicsSync()
        {
            if (!isInitialized || physicsDataQueue.Count == 0)
                return;

            StartCoroutine(SendPhysicsDataBatch());
        }

        private IEnumerator SendPhysicsDataBatch()
        {
            if (physicsDataQueue.Count == 0) yield break;

            var batchData = new List<UnityPhysicsData>();
            int maxBatchSize = 10; // Limit batch size for performance

            for (int i = 0; i < maxBatchSize && physicsDataQueue.Count > 0; i++)
            {
                batchData.Add(physicsDataQueue.Dequeue());
            }

            var physicsRequest = new PhysicsSyncRequest
            {
                sessionId = sessionId,
                physicsData = batchData,
                timestamp = System.DateTime.UtcNow,
                carConfiguration = currentCarData
            };

            string jsonData = JsonConvert.SerializeObject(physicsRequest);

            using (var request = apiClient.Post("/physics/sync", jsonData, authToken))
            {
                yield return request.SendWebRequest();

                if (request.result == UnityWebRequest.Result.Success)
                {
                    var response = JsonConvert.DeserializeObject<PhysicsSyncResponse>(request.downloadHandler.text);
                    ProcessBackendResponse(response);
                }
                else
                {
                    Debug.LogWarning($"Physics sync failed: {request.error}");
                }
            }
        }

        private void ProcessBackendResponse(PhysicsSyncResponse response)
        {
            if (response.corrections != null)
            {
                lastCorrections = response.corrections;

                // Apply position/velocity corrections if significant drift
                if (response.positionCorrection != null)
                {
                    Vector3 currentPos = transform.position;
                    Vector3 backendPos = response.positionCorrection.ToVector3();

                    if (Vector3.Distance(currentPos, backendPos) > positionTolerance)
                    {
                        targetPosition = backendPos;
                        StartCoroutine(SmoothPositionCorrection());
                    }
                }

                if (response.velocityCorrection != null)
                {
                    Vector3 currentVel = GetComponent<Rigidbody>().velocity;
                    Vector3 backendVel = response.velocityCorrection.ToVector3();

                    if (Vector3.Distance(currentVel, backendVel) > velocityTolerance)
                    {
                        targetVelocity = backendVel;
                        StartCoroutine(SmoothVelocityCorrection());
                    }
                }
            }

            // Handle telemetry analysis
            if (response.telemetryAnalysis != null)
            {
                ProcessTelemetryAnalysis(response.telemetryAnalysis);
            }

            // Handle warnings
            if (response.warnings != null && response.warnings.Count > 0)
            {
                foreach (var warning in response.warnings)
                {
                    UIManager.Instance?.ShowWarning(warning);
                }
            }
        }

        #endregion

        #region Position/Velocity Correction

        private IEnumerator SmoothPositionCorrection()
        {
            Vector3 startPosition = transform.position;
            float elapsedTime = 0;

            while (elapsedTime < correctionSmoothingTime)
            {
                elapsedTime += Time.deltaTime;
                float t = elapsedTime / correctionSmoothingTime;

                transform.position = Vector3.Lerp(startPosition, targetPosition, t);
                yield return null;
            }
        }

        private IEnumerator SmoothVelocityCorrection()
        {
            Rigidbody rb = GetComponent<Rigidbody>();
            Vector3 startVelocity = rb.velocity;
            float elapsedTime = 0;

            while (elapsedTime < correctionSmoothingTime)
            {
                elapsedTime += Time.deltaTime;
                float t = elapsedTime / correctionSmoothingTime;

                rb.velocity = Vector3.Lerp(startVelocity, targetVelocity, t);
                yield return null;
            }
        }

        #endregion

        #region Car Configuration

        public void SetCarConfiguration(CarData carData)
        {
            currentCarData = carData;

            if (isInitialized)
            {
                StartCoroutine(UpdateCarConfigurationOnBackend());
            }
        }

        private IEnumerator UpdateCarConfigurationOnBackend()
        {
            var configRequest = new UpdateCarConfigRequest
            {
                sessionId = sessionId,
                carConfiguration = currentCarData
            };

            string jsonData = JsonConvert.SerializeObject(configRequest);

            using (var request = apiClient.Put($"/physics/session/{sessionId}/car", jsonData, authToken))
            {
                yield return request.SendWebRequest();

                if (request.result == UnityWebRequest.Result.Success)
                {
                    Debug.Log("Car configuration updated on backend");
                }
                else
                {
                    Debug.LogWarning($"Failed to update car configuration: {request.error}");
                }
            }
        }

        #endregion

        #region WebSocket Integration

        private void HandleWebSocketMessage(string message)
        {
            try
            {
                var wsMessage = JsonConvert.DeserializeObject<WebSocketMessage>(message);

                switch (wsMessage.type)
                {
                    case "physics_correction":
                        var correction = JsonConvert.DeserializeObject<BackendPhysicsCorrections>(wsMessage.data.ToString());
                        lastCorrections = correction;
                        break;

                    case "telemetry_alert":
                        var alert = JsonConvert.DeserializeObject<TelemetryAlert>(wsMessage.data.ToString());
                        UIManager.Instance?.ShowAlert(alert.message, alert.severity);
                        break;

                    case "system_status":
                        var status = JsonConvert.DeserializeObject<SystemStatus>(wsMessage.data.ToString());
                        ProcessSystemStatus(status);
                        break;
                }
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Error processing WebSocket message: {e.Message}");
            }
        }

        #endregion

        #region Telemetry Integration

        private void ProcessTelemetryAnalysis(TelemetryAnalysis analysis)
        {
            // Update UI with performance metrics
            UIManager.Instance?.UpdatePerformanceMetrics(analysis);

            // Check for performance warnings
            if (analysis.engineHealth < 0.8f)
            {
                UIManager.Instance?.ShowWarning("Engine performance degraded - service recommended");
            }

            if (analysis.brakeHealth < 0.7f)
            {
                UIManager.Instance?.ShowWarning("Brake system overheating - reduce brake usage");
            }

            if (analysis.tireHealth < 0.6f)
            {
                UIManager.Instance?.ShowWarning("Excessive tire wear detected");
            }
        }

        private void ProcessSystemStatus(SystemStatus status)
        {
            // Update system indicators
            UIManager.Instance?.UpdateSystemIndicators(
                status.escStatus,
                status.tcsStatus,
                status.absStatus,
                status.engineStatus,
                status.transmissionStatus
            );
        }

        public async Task<bool> UploadTelemetryBatch(List<TelemetryDataPoint> telemetryData)
        {
            if (!isConnected || telemetryData == null || telemetryData.Count == 0)
                return false;

            var telemetryRequest = new TelemetryUploadRequest
            {
                sessionId = sessionId,
                telemetryData = telemetryData
            };

            try
            {
                string jsonData = JsonConvert.SerializeObject(telemetryRequest);
                var result = await apiClient.PostAsync("/telemetry/batch", jsonData, authToken);

                return result.IsSuccess;
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to upload telemetry: {e.Message}");
                return false;
            }
        }

        #endregion

        #region Cleanup

        void OnDestroy()
        {
            if (wsClient != null)
            {
                wsClient.Close();
            }

            if (isInitialized)
            {
                StartCoroutine(EndPhysicsSession());
            }
        }

        private IEnumerator EndPhysicsSession()
        {
            if (string.IsNullOrEmpty(sessionId)) yield break;

            using (var request = apiClient.Delete($"/physics/session/{sessionId}", authToken))
            {
                yield return request.SendWebRequest();

                if (request.result == UnityWebRequest.Result.Success)
                {
                    Debug.Log("Physics session ended successfully");
                }
            }
        }

        #endregion

        #region Offline Fallback

        public void EnableOfflineMode()
        {
            enableBackendPhysics = false;
            isConnected = false;

            // Switch to Unity-only physics
            var carController = GetComponent<CarController>();
            if (carController != null)
            {
                carController.SetPhysicsMode(PhysicsMode.UnityOnly);
            }

            UIManager.Instance?.ShowMessage("Switched to offline physics mode");
            Debug.Log("Backend physics disabled - using Unity-only mode");
        }

        public void RetryConnection()
        {
            if (!isConnected)
            {
                StartCoroutine(InitializeConnection());
            }
        }

        #endregion
    }

    #region Data Structures

    [System.Serializable]
    public class UnityPhysicsData
    {
        public Vector3 position;
        public Vector3 velocity;
        public Vector3 angularVelocity;
        public float throttleInput;
        public float steerInput;
        public float brakeInput;
        public float clutchInput;
        public int currentGear;
        public float rpm;
        public float speed;
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class BackendPhysicsCorrections
    {
        public bool escActive;
        public bool tcsActive;
        public bool absActive;
        public float throttleReduction;
        public float[] brakeAdjustments;
        public float[] brakeTemperatures;
        public float[] tireTemperatures;
        public float[] tireWear;
        public Vector3? positionCorrection;
        public Vector3? velocityCorrection;
    }

    [System.Serializable]
    public class AuthenticationRequest
    {
        public string deviceId;
        public string gameVersion;
        public string platform;
    }

    [System.Serializable]
    public class AuthenticationResponse
    {
        public string token;
        public string playerId;
        public int expiresIn;
    }

    [System.Serializable]
    public class PhysicsSessionRequest
    {
        public string carId;
        public bool enableAdvancedPhysics;
        public float syncFrequency;
    }

    [System.Serializable]
    public class PhysicsSessionResponse
    {
        public string sessionId;
        public bool success;
        public string message;
    }

    [System.Serializable]
    public class PhysicsSyncRequest
    {
        public string sessionId;
        public List<UnityPhysicsData> physicsData;
        public System.DateTime timestamp;
        public CarData carConfiguration;
    }

    [System.Serializable]
    public class PhysicsSyncResponse
    {
        public BackendPhysicsCorrections corrections;
        public Vector3? positionCorrection;
        public Vector3? velocityCorrection;
        public TelemetryAnalysis telemetryAnalysis;
        public List<string> warnings;
        public bool success;
    }

    [System.Serializable]
    public class TelemetryAnalysis
    {
        public float engineHealth;
        public float brakeHealth;
        public float tireHealth;
        public float fuelEfficiency;
        public float performanceRating;
        public string[] recommendations;
    }

    [System.Serializable]
    public class TelemetryAlert
    {
        public string message;
        public string severity; // "info", "warning", "critical"
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class SystemStatus
    {
        public string escStatus;
        public string tcsStatus;
        public string absStatus;
        public string engineStatus;
        public string transmissionStatus;
    }

    [System.Serializable]
    public class UpdateCarConfigRequest
    {
        public string sessionId;
        public CarData carConfiguration;
    }

    [System.Serializable]
    public class TelemetryUploadRequest
    {
        public string sessionId;
        public List<TelemetryDataPoint> telemetryData;
    }

    [System.Serializable]
    public class WebSocketMessage
    {
        public string type;
        public object data;
        public System.DateTime timestamp;
    }

    #endregion
}
