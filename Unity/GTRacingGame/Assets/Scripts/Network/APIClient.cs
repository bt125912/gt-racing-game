using UnityEngine;
using UnityEngine.Networking;
using System.Collections;
using System.Collections.Generic;
using System.Threading.Tasks;
using Newtonsoft.Json;

namespace GTRacing.Network
{
    /// <summary>
    /// API Client for communicating with GT Racing Game backend
    /// Handles REST API calls, authentication, and error handling
    /// </summary>
    public class APIClient : MonoBehaviour
    {
        [Header("API Configuration")]
        [SerializeField] private string baseApiUrl = "https://your-api-gateway.amazonaws.com/dev";
        [SerializeField] private float requestTimeoutSeconds = 30f;
        [SerializeField] private int maxRetryAttempts = 3;
        [SerializeField] private bool enableRequestLogging = true;

        [Header("Authentication")]
        [SerializeField] private string authToken;
        [SerializeField] private bool autoRefreshToken = true;

        // Singleton instance
        public static APIClient Instance { get; private set; }

        // Request tracking
        private Dictionary<string, UnityWebRequest> activeRequests = new Dictionary<string, UnityWebRequest>();
        private Queue<APIRequest> requestQueue = new Queue<APIRequest>();
        private bool isProcessingQueue = false;

        public string BaseUrl => baseApiUrl;
        public bool IsAuthenticated => !string.IsNullOrEmpty(authToken);

        #region Initialization

        void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
                InitializeClient();
            }
            else
            {
                Destroy(gameObject);
            }
        }

        private void InitializeClient()
        {
            // Load saved auth token
            authToken = PlayerPrefs.GetString("AuthToken", "");

            // Start request queue processor
            StartCoroutine(ProcessRequestQueue());
        }

        #endregion

        #region Authentication

        public IEnumerator AuthenticateDevice()
        {
            var authRequest = new DeviceAuthRequest
            {
                deviceId = SystemInfo.deviceUniqueIdentifier,
                deviceModel = SystemInfo.deviceModel,
                operatingSystem = SystemInfo.operatingSystem,
                gameVersion = Application.version,
                platform = Application.platform.ToString()
            };

            yield return StartCoroutine(PostCoroutine("/auth/device", authRequest, null, (response) =>
            {
                var authResponse = JsonConvert.DeserializeObject<AuthResponse>(response);
                authToken = authResponse.token;
                PlayerPrefs.SetString("AuthToken", authToken);
                PlayerPrefs.Save();

                Debug.Log("Device authentication successful");
            }));
        }

        public IEnumerator RefreshAuthToken()
        {
            if (string.IsNullOrEmpty(authToken)) yield break;

            var refreshRequest = new { refreshToken = authToken };

            yield return StartCoroutine(PostCoroutine("/auth/refresh", refreshRequest, authToken, (response) =>
            {
                var authResponse = JsonConvert.DeserializeObject<AuthResponse>(response);
                authToken = authResponse.token;
                PlayerPrefs.SetString("AuthToken", authToken);
                PlayerPrefs.Save();

                Debug.Log("Auth token refreshed");
            }));
        }

        #endregion

        #region Race Session API

        public IEnumerator StartRaceSession(RaceStartRequest request, System.Action<RaceSessionResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PostCoroutine("/race/start", request, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<RaceSessionResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator EndRaceSession(RaceEndRequest request, System.Action<RaceEndResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PostCoroutine("/race/end", request, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<RaceEndResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator GetRaceStatus(string raceId, System.Action<RaceStatusResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/race/{raceId}/status", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<RaceStatusResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        #endregion

        #region Player Data API

        public IEnumerator GetPlayerData(string playerId, System.Action<PlayerData> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/player/{playerId}", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<PlayerData>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator UpdatePlayerData(string playerId, PlayerUpdateRequest request, System.Action<PlayerData> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PutCoroutine($"/player/{playerId}", request, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<PlayerData>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator GetPlayerStats(string playerId, System.Action<PlayerStats> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/player/{playerId}/stats", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<PlayerStats>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        #endregion

        #region Garage API

        public IEnumerator GetPlayerCars(string playerId, System.Action<GarageResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/garage/{playerId}/cars", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<GarageResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator GetDealership(System.Action<DealershipResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine("/garage/dealership", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<DealershipResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator PurchaseCar(string playerId, CarPurchaseRequest request, System.Action<CarPurchaseResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PostCoroutine($"/garage/{playerId}/purchase", request, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<CarPurchaseResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator TuneCar(string playerId, string carId, CarTuningRequest request, System.Action<CarTuningResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PostCoroutine($"/garage/{playerId}/tune", request, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<CarTuningResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator SellCar(string playerId, string carId, System.Action<CarSaleResponse> onSuccess, System.Action<string> onError = null)
        {
            var sellRequest = new { carId = carId };

            yield return StartCoroutine(PostCoroutine($"/garage/{playerId}/sell", sellRequest, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<CarSaleResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        #endregion

        #region Leaderboard API

        public IEnumerator GetTrackLeaderboard(string trackId, System.Action<LeaderboardResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/leaderboard/track/{trackId}", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<LeaderboardResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator GetGlobalLeaderboard(string category, System.Action<LeaderboardResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/leaderboard/{category}", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<LeaderboardResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator SubmitLapTime(LapTimeSubmission submission, System.Action<SubmissionResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PostCoroutine("/leaderboard/submit", submission, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<SubmissionResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        #endregion

        #region Telemetry API

        public IEnumerator UploadTelemetryBatch(TelemetryBatchRequest request, System.Action<TelemetryResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(PostCoroutine("/telemetry/batch", request, authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<TelemetryResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        public IEnumerator GetTelemetryAnalysis(string sessionId, System.Action<TelemetryAnalysisResponse> onSuccess, System.Action<string> onError = null)
        {
            yield return StartCoroutine(GetCoroutine($"/telemetry/analysis/{sessionId}", authToken,
                (response) => {
                    var result = JsonConvert.DeserializeObject<TelemetryAnalysisResponse>(response);
                    onSuccess?.Invoke(result);
                },
                onError));
        }

        #endregion

        #region Core HTTP Methods

        private IEnumerator GetCoroutine(string endpoint, string token, System.Action<string> onSuccess, System.Action<string> onError = null)
        {
            string url = baseApiUrl + endpoint;
            string requestId = System.Guid.NewGuid().ToString();

            using (UnityWebRequest request = UnityWebRequest.Get(url))
            {
                yield return StartCoroutine(ExecuteRequest(request, requestId, token, onSuccess, onError));
            }
        }

        private IEnumerator PostCoroutine(string endpoint, object data, string token, System.Action<string> onSuccess, System.Action<string> onError = null)
        {
            string url = baseApiUrl + endpoint;
            string requestId = System.Guid.NewGuid().ToString();
            string jsonData = JsonConvert.SerializeObject(data);

            using (UnityWebRequest request = new UnityWebRequest(url, "POST"))
            {
                byte[] bodyRaw = System.Text.Encoding.UTF8.GetBytes(jsonData);
                request.uploadHandler = new UploadHandlerRaw(bodyRaw);
                request.downloadHandler = new DownloadHandlerBuffer();
                request.SetRequestHeader("Content-Type", "application/json");

                yield return StartCoroutine(ExecuteRequest(request, requestId, token, onSuccess, onError));
            }
        }

        private IEnumerator PutCoroutine(string endpoint, object data, string token, System.Action<string> onSuccess, System.Action<string> onError = null)
        {
            string url = baseApiUrl + endpoint;
            string requestId = System.Guid.NewGuid().ToString();
            string jsonData = JsonConvert.SerializeObject(data);

            using (UnityWebRequest request = new UnityWebRequest(url, "PUT"))
            {
                byte[] bodyRaw = System.Text.Encoding.UTF8.GetBytes(jsonData);
                request.uploadHandler = new UploadHandlerRaw(bodyRaw);
                request.downloadHandler = new DownloadHandlerBuffer();
                request.SetRequestHeader("Content-Type", "application/json");

                yield return StartCoroutine(ExecuteRequest(request, requestId, token, onSuccess, onError));
            }
        }

        private IEnumerator DeleteCoroutine(string endpoint, string token, System.Action<string> onSuccess, System.Action<string> onError = null)
        {
            string url = baseApiUrl + endpoint;
            string requestId = System.Guid.NewGuid().ToString();

            using (UnityWebRequest request = UnityWebRequest.Delete(url))
            {
                yield return StartCoroutine(ExecuteRequest(request, requestId, token, onSuccess, onError));
            }
        }

        private IEnumerator ExecuteRequest(UnityWebRequest request, string requestId, string token, System.Action<string> onSuccess, System.Action<string> onError)
        {
            // Set timeout
            request.timeout = (int)requestTimeoutSeconds;

            // Add authentication header
            if (!string.IsNullOrEmpty(token))
            {
                request.SetRequestHeader("Authorization", $"Bearer {token}");
            }

            // Add common headers
            request.SetRequestHeader("User-Agent", $"GTRacing-Unity/{Application.version}");
            request.SetRequestHeader("Accept", "application/json");

            // Track active request
            activeRequests[requestId] = request;

            if (enableRequestLogging)
            {
                Debug.Log($"API Request: {request.method} {request.url}");
            }

            // Send request
            yield return request.SendWebRequest();

            // Remove from active requests
            activeRequests.Remove(requestId);

            // Handle response
            if (request.result == UnityWebRequest.Result.Success)
            {
                if (enableRequestLogging)
                {
                    Debug.Log($"API Success: {request.responseCode} - {request.url}");
                }

                onSuccess?.Invoke(request.downloadHandler.text);
            }
            else
            {
                string error = $"API Error: {request.responseCode} - {request.error}";

                if (enableRequestLogging)
                {
                    Debug.LogError($"{error} - URL: {request.url}");
                }

                // Handle specific error cases
                if (request.responseCode == 401 && autoRefreshToken)
                {
                    // Token expired, try to refresh
                    yield return StartCoroutine(RefreshAuthToken());
                    // Could retry the original request here
                }

                onError?.Invoke(error);
            }
        }

        #endregion

        #region Request Queue Management

        public void EnqueueRequest(APIRequest request)
        {
            requestQueue.Enqueue(request);
        }

        private IEnumerator ProcessRequestQueue()
        {
            while (true)
            {
                if (!isProcessingQueue && requestQueue.Count > 0)
                {
                    isProcessingQueue = true;
                    var request = requestQueue.Dequeue();

                    yield return StartCoroutine(ExecuteAPIRequest(request));

                    isProcessingQueue = false;

                    // Small delay between requests to avoid rate limiting
                    yield return new WaitForSeconds(0.1f);
                }
                else
                {
                    yield return new WaitForSeconds(0.1f);
                }
            }
        }

        private IEnumerator ExecuteAPIRequest(APIRequest request)
        {
            switch (request.method)
            {
                case "GET":
                    yield return StartCoroutine(GetCoroutine(request.endpoint, request.authToken, request.onSuccess, request.onError));
                    break;
                case "POST":
                    yield return StartCoroutine(PostCoroutine(request.endpoint, request.data, request.authToken, request.onSuccess, request.onError));
                    break;
                case "PUT":
                    yield return StartCoroutine(PutCoroutine(request.endpoint, request.data, request.authToken, request.onSuccess, request.onError));
                    break;
                case "DELETE":
                    yield return StartCoroutine(DeleteCoroutine(request.endpoint, request.authToken, request.onSuccess, request.onError));
                    break;
            }
        }

        #endregion

        #region Async Methods (Alternative Implementation)

        public async Task<T> GetAsync<T>(string endpoint, string token = null)
        {
            return await ExecuteRequestAsync<T>("GET", endpoint, null, token);
        }

        public async Task<T> PostAsync<T>(string endpoint, object data, string token = null)
        {
            return await ExecuteRequestAsync<T>("POST", endpoint, data, token);
        }

        public async Task<T> PutAsync<T>(string endpoint, object data, string token = null)
        {
            return await ExecuteRequestAsync<T>("PUT", endpoint, data, token);
        }

        public async Task<T> DeleteAsync<T>(string endpoint, string token = null)
        {
            return await ExecuteRequestAsync<T>("DELETE", endpoint, null, token);
        }

        private async Task<T> ExecuteRequestAsync<T>(string method, string endpoint, object data, string token)
        {
            var tcs = new TaskCompletionSource<T>();

            System.Action<string> onSuccess = (response) =>
            {
                try
                {
                    var result = JsonConvert.DeserializeObject<T>(response);
                    tcs.SetResult(result);
                }
                catch (System.Exception e)
                {
                    tcs.SetException(e);
                }
            };

            System.Action<string> onError = (error) =>
            {
                tcs.SetException(new System.Exception(error));
            };

            var request = new APIRequest
            {
                method = method,
                endpoint = endpoint,
                data = data,
                authToken = token ?? authToken,
                onSuccess = onSuccess,
                onError = onError
            };

            EnqueueRequest(request);

            return await tcs.Task;
        }

        #endregion

        #region Utility Methods

        public void CancelAllRequests()
        {
            foreach (var request in activeRequests.Values)
            {
                request?.Abort();
            }
            activeRequests.Clear();
            requestQueue.Clear();
        }

        public void SetAuthToken(string token)
        {
            authToken = token;
            PlayerPrefs.SetString("AuthToken", token);
            PlayerPrefs.Save();
        }

        public void ClearAuthToken()
        {
            authToken = "";
            PlayerPrefs.DeleteKey("AuthToken");
            PlayerPrefs.Save();
        }

        public bool IsRequestActive(string endpoint)
        {
            foreach (var request in activeRequests.Values)
            {
                if (request.url.EndsWith(endpoint))
                    return true;
            }
            return false;
        }

        #endregion

        void OnDestroy()
        {
            CancelAllRequests();
        }
    }

    #region Data Structures

    [System.Serializable]
    public class APIRequest
    {
        public string method;
        public string endpoint;
        public object data;
        public string authToken;
        public System.Action<string> onSuccess;
        public System.Action<string> onError;
    }

    // Request/Response classes for each API endpoint...
    // (Shortened for brevity - these would match your backend API contracts)

    [System.Serializable]
    public class DeviceAuthRequest
    {
        public string deviceId;
        public string deviceModel;
        public string operatingSystem;
        public string gameVersion;
        public string platform;
    }

    [System.Serializable]
    public class AuthResponse
    {
        public string token;
        public string playerId;
        public int expiresIn;
        public bool success;
    }

    [System.Serializable]
    public class RaceStartRequest
    {
        public string playerId;
        public string trackId;
        public string carId;
        public string gameMode;
        public string weatherConditions;
        public string trackCondition;
    }

    [System.Serializable]
    public class RaceSessionResponse
    {
        public string sessionId;
        public string raceId;
        public bool success;
        public string message;
    }

    [System.Serializable]
    public class RaceEndRequest
    {
        public string sessionId;
        public string raceId;
        public float totalTime;
        public float bestLapTime;
        public int position;
        public List<float> lapTimes;
        public bool completed;
    }

    [System.Serializable]
    public class RaceEndResponse
    {
        public bool success;
        public int newCredits;
        public int experienceGained;
        public string newRank;
        public List<string> achievements;
    }

    [System.Serializable]
    public class PlayerData
    {
        public string playerId;
        public string name;
        public int level;
        public long experience;
        public long credits;
        public string rank;
        public PlayerStats stats;
        public PlayerSettings settings;
    }

    [System.Serializable]
    public class PlayerStats
    {
        public int totalRaces;
        public int wins;
        public int podiums;
        public float totalDistance;
        public float bestLapTime;
        public string favoriteTrack;
        public string favoriteCar;
    }

    [System.Serializable]
    public class PlayerSettings
    {
        public float masterVolume;
        public float musicVolume;
        public float sfxVolume;
        public string controlScheme;
        public bool assistsEnabled;
        public Dictionary<string, bool> drivingAssists;
    }

    [System.Serializable]
    public class PlayerUpdateRequest
    {
        public string name;
        public PlayerSettings settings;
    }

    [System.Serializable]
    public class GarageResponse
    {
        public string playerId;
        public int totalCars;
        public long totalValue;
        public List<CarConfiguration> cars;
        public GarageStats stats;
    }

    [System.Serializable]
    public class CarConfiguration
    {
        public string carId;
        public string name;
        public string manufacturer;
        public string category;
        public bool owned;
        public long purchasePrice;
        public long currentValue;
        public CarSpecs specs;
        public CarTuning tuning;
        public CarVisuals visuals;
    }

    [System.Serializable]
    public class CarSpecs
    {
        public int power;
        public float mass;
        public float topSpeed;
        public float acceleration;
        public float handling;
        public float braking;
    }

    [System.Serializable]
    public class CarTuning
    {
        public EngineUpgrades engine;
        public SuspensionUpgrades suspension;
        public BrakeUpgrades brakes;
        public AeroUpgrades aerodynamics;
        public TireUpgrades tires;
    }

    [System.Serializable]
    public class CarVisuals
    {
        public string bodyColor;
        public string rimColor;
        public string interiorColor;
        public string liveryId;
        public List<string> accessories;
    }

    [System.Serializable]
    public class GarageStats
    {
        public long totalValue;
        public float averagePerformance;
        public string mostValuableCar;
        public Dictionary<string, int> carsByManufacturer;
        public Dictionary<string, int> carsByCategory;
    }

    // Additional data structures would continue here...
    // (Abbreviated for space - full implementation would include all API contracts)

    #endregion
}
