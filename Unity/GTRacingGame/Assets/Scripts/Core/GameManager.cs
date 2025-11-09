using UnityEngine;
using UnityEngine.SceneManagement;
using System.Collections;
using System.Collections.Generic;
using GTRacing.Car;
using GTRacing.UI;
using GTRacing.Network;
using GTRacing.Multiplayer;
using GTRacing.Telemetry;

namespace GTRacing.Core
{
    /// <summary>
    /// Central game manager for GT Racing Game
    /// Handles game state, scene management, and component coordination
    /// </summary>
    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance { get; private set; }

        [Header("Game Configuration")]
        [SerializeField] private string apiBaseUrl = "https://your-api-gateway.amazonaws.com/dev";
        [SerializeField] private string websocketUrl = "wss://your-websocket.amazonaws.com/dev";
        [SerializeField] private bool enableTelemetry = true;
        [SerializeField] private bool enableMultiplayer = true;

        [Header("Player Data")]
        [SerializeField] private string playerId;
        [SerializeField] private string playerName = "Player";
        [SerializeField] private long playerCredits = 100000;
        [SerializeField] private string selectedCarId = "GT_R34_001";

        [Header("Race Settings")]
        [SerializeField] private string currentTrackId = "suzuka_circuit";
        [SerializeField] private int totalLaps = 5;
        [SerializeField] private string weatherCondition = "clear";
        [SerializeField] private string trackCondition = "dry";

        // Game State
        private GameState currentState = GameState.MainMenu;
        private bool isRaceActive = false;
        private bool isMultiplayerRace = false;
        private string currentSessionId;
        private float raceStartTime;
        private int currentLap = 1;
        private List<float> lapTimes = new List<float>();
        private float bestLapTime = float.MaxValue;

        // Component References
        private CarController playerCar;
        private UIManager uiManager;
        private APIClient apiClient;
        private MultiplayerClient multiplayerClient;
        private TelemetryCollector telemetryCollector;
        private AudioSource audioSource;

        // Race tracking
        private Vector3 startLinePosition;
        private List<Vector3> checkpoints = new List<Vector3>();
        private int nextCheckpointIndex = 0;
        private float lapProgress = 0f;

        // Properties
        public string PlayerId => playerId;
        public string PlayerName => playerName;
        public long PlayerCredits => playerCredits;
        public string SelectedCarId => selectedCarId;
        public bool IsRaceActive => isRaceActive;
        public GameState CurrentState => currentState;
        public float LapProgress => lapProgress;
        public string CurrentSessionId => currentSessionId;

        public enum GameState
        {
            MainMenu,
            Garage,
            RaceSetup,
            Racing,
            RaceResults,
            Multiplayer,
            Settings
        }

        #region Initialization

        void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
                InitializeGame();
            }
            else
            {
                Destroy(gameObject);
            }
        }

        void Start()
        {
            StartCoroutine(InitializeComponents());
        }

        private void InitializeGame()
        {
            // Load player data from PlayerPrefs
            LoadPlayerData();

            // Setup audio
            audioSource = GetComponent<AudioSource>();
            if (audioSource == null)
                audioSource = gameObject.AddComponent<AudioSource>();

            // Initialize current session
            currentSessionId = GenerateSessionId();

            Application.targetFrameRate = 60;
            QualitySettings.vSyncCount = 1;
        }

        private IEnumerator InitializeComponents()
        {
            // Find or create UI Manager
            uiManager = FindObjectOfType<UIManager>();
            if (uiManager == null)
            {
                Debug.LogError("UIManager not found! Make sure it exists in the scene.");
            }

            // Initialize API Client
            apiClient = APIClient.Instance;
            if (apiClient != null)
            {
                yield return StartCoroutine(apiClient.AuthenticateDevice());
            }

            // Initialize Multiplayer if enabled
            if (enableMultiplayer)
            {
                InitializeMultiplayer();
            }

            // Set initial game state
            SetGameState(GameState.MainMenu);
        }

        private void LoadPlayerData()
        {
            playerId = PlayerPrefs.GetString("PlayerId", SystemInfo.deviceUniqueIdentifier);
            playerName = PlayerPrefs.GetString("PlayerName", "Player");
            playerCredits = PlayerPrefs.GetInt("PlayerCredits", 100000);
            selectedCarId = PlayerPrefs.GetString("SelectedCarId", "GT_R34_001");
        }

        private void SavePlayerData()
        {
            PlayerPrefs.SetString("PlayerId", playerId);
            PlayerPrefs.SetString("PlayerName", playerName);
            PlayerPrefs.SetInt("PlayerCredits", (int)playerCredits);
            PlayerPrefs.SetString("SelectedCarId", selectedCarId);
            PlayerPrefs.Save();
        }

        #endregion

        #region Game State Management

        public void SetGameState(GameState newState)
        {
            GameState previousState = currentState;
            currentState = newState;

            Debug.Log($"Game state changed: {previousState} -> {newState}");

            switch (newState)
            {
                case GameState.MainMenu:
                    HandleMainMenuState();
                    break;
                case GameState.Garage:
                    HandleGarageState();
                    break;
                case GameState.Racing:
                    HandleRacingState();
                    break;
                case GameState.RaceResults:
                    HandleRaceResultsState();
                    break;
                case GameState.Multiplayer:
                    HandleMultiplayerState();
                    break;
            }

            // Notify UI of state change
            uiManager?.OnGameStateChanged(newState);
        }

        private void HandleMainMenuState()
        {
            Time.timeScale = 1f;
            isRaceActive = false;

            if (telemetryCollector != null)
                telemetryCollector.StopCollection();
        }

        private void HandleGarageState()
        {
            Time.timeScale = 1f;
            // Load garage data from backend
            StartCoroutine(LoadGarageData());
        }

        private void HandleRacingState()
        {
            Time.timeScale = 1f;
            isRaceActive = true;

            if (enableTelemetry && telemetryCollector != null)
                telemetryCollector.StartCollection();
        }

        private void HandleRaceResultsState()
        {
            Time.timeScale = 1f;
            isRaceActive = false;

            if (telemetryCollector != null)
                telemetryCollector.StopCollection();
        }

        private void HandleMultiplayerState()
        {
            if (multiplayerClient != null && !multiplayerClient.IsConnected)
            {
                multiplayerClient.ConnectToMultiplayer();
            }
        }

        #endregion

        #region Race Management

        public void StartSinglePlayerRace()
        {
            StartCoroutine(StartRaceCoroutine(false));
        }

        public void StartMultiplayerRace(string raceId)
        {
            isMultiplayerRace = true;
            StartCoroutine(StartRaceCoroutine(true, raceId));
        }

        private IEnumerator StartRaceCoroutine(bool isMultiplayer = false, string raceId = null)
        {
            // Load race scene
            yield return SceneManager.LoadSceneAsync("Race", LoadSceneMode.Single);

            // Initialize race components
            yield return StartCoroutine(InitializeRace(isMultiplayer, raceId));

            // Start race session with backend
            yield return StartCoroutine(CreateBackendRaceSession());

            // Set racing state
            SetGameState(GameState.Racing);

            // Start race countdown
            yield return StartCoroutine(RaceCountdown());

            // Begin race
            BeginRace();
        }

        private IEnumerator InitializeRace(bool isMultiplayer, string raceId)
        {
            // Find player car in scene
            playerCar = FindObjectOfType<CarController>();
            if (playerCar == null)
            {
                Debug.LogError("No CarController found in race scene!");
                yield break;
            }

            // Initialize telemetry
            telemetryCollector = playerCar.GetComponent<TelemetryCollector>();
            if (telemetryCollector == null)
                telemetryCollector = playerCar.gameObject.AddComponent<TelemetryCollector>();

            // Setup track checkpoints
            SetupTrackCheckpoints();

            // Join multiplayer race if needed
            if (isMultiplayer && multiplayerClient != null)
            {
                multiplayerClient.JoinRace(raceId ?? GenerateRaceId(), currentTrackId, totalLaps);
            }

            yield return null;
        }

        private IEnumerator CreateBackendRaceSession()
        {
            if (apiClient != null)
            {
                var raceRequest = new RaceStartRequest
                {
                    playerId = playerId,
                    trackId = currentTrackId,
                    carId = selectedCarId,
                    gameMode = isMultiplayerRace ? "multiplayer" : "time_trial",
                    weatherConditions = weatherCondition,
                    trackCondition = trackCondition
                };

                yield return StartCoroutine(apiClient.StartRaceSession(raceRequest,
                    (response) => {
                        currentSessionId = response.sessionId;
                        Debug.Log($"Race session created: {currentSessionId}");
                    },
                    (error) => Debug.LogError($"Failed to create race session: {error}")));
            }
        }

        private IEnumerator RaceCountdown()
        {
            // 3-2-1-GO countdown
            for (int i = 3; i > 0; i--)
            {
                uiManager?.ShowCountdown(i);
                PlayCountdownSound();
                yield return new WaitForSeconds(1f);
            }

            uiManager?.ShowCountdown(0); // Show "GO!"
            PlayStartSound();
            yield return new WaitForSeconds(0.5f);

            uiManager?.HideCountdown();
        }

        private void BeginRace()
        {
            raceStartTime = Time.time;
            currentLap = 1;
            lapTimes.Clear();
            nextCheckpointIndex = 0;
            lapProgress = 0f;

            Debug.Log("Race started!");
        }

        public void FinishRace()
        {
            isRaceActive = false;

            // Calculate final race time
            float totalRaceTime = Time.time - raceStartTime;

            // End race session with backend
            StartCoroutine(EndBackendRaceSession(totalRaceTime));

            // Leave multiplayer race
            if (isMultiplayerRace && multiplayerClient != null)
            {
                multiplayerClient.LeaveRace();
            }

            // Show race results
            SetGameState(GameState.RaceResults);
        }

        private IEnumerator EndBackendRaceSession(float totalTime)
        {
            if (apiClient != null && !string.IsNullOrEmpty(currentSessionId))
            {
                var raceEndRequest = new RaceEndRequest
                {
                    sessionId = currentSessionId,
                    raceId = currentSessionId,
                    totalTime = totalTime,
                    bestLapTime = bestLapTime,
                    position = 1, // Would be calculated in multiplayer
                    lapTimes = lapTimes,
                    completed = true
                };

                yield return StartCoroutine(apiClient.EndRaceSession(raceEndRequest,
                    (response) => {
                        Debug.Log($"Race session ended. Credits earned: {response.newCredits}");
                        playerCredits += response.newCredits;
                        SavePlayerData();
                    },
                    (error) => Debug.LogError($"Failed to end race session: {error}")));
            }
        }

        #endregion

        #region Lap and Checkpoint Management

        void Update()
        {
            if (isRaceActive && playerCar != null)
            {
                UpdateLapProgress();
                CheckForLapCompletion();
                UpdateUI();
            }
        }

        private void UpdateLapProgress()
        {
            if (checkpoints.Count == 0) return;

            Vector3 playerPos = playerCar.transform.position;
            Vector3 nextCheckpoint = checkpoints[nextCheckpointIndex];

            float distanceToNext = Vector3.Distance(playerPos, nextCheckpoint);

            // Check if reached next checkpoint
            if (distanceToNext < 20f) // 20m radius
            {
                nextCheckpointIndex = (nextCheckpointIndex + 1) % checkpoints.Count;
            }

            // Calculate lap progress (0-1)
            float checkpointProgress = (float)nextCheckpointIndex / checkpoints.Count;
            lapProgress = checkpointProgress;
        }

        private void CheckForLapCompletion()
        {
            // Check if completed a lap (passed start/finish line)
            if (nextCheckpointIndex == 0 && lapProgress > 0.9f && currentLap > 0)
            {
                CompleteLap();
            }
        }

        private void CompleteLap()
        {
            float lapTime = Time.time - raceStartTime - lapTimes.Sum();
            lapTimes.Add(lapTime);

            if (lapTime < bestLapTime)
            {
                bestLapTime = lapTime;
                uiManager?.UpdateBestLapTime(bestLapTime);
            }

            currentLap++;

            Debug.Log($"Lap {currentLap - 1} completed in {lapTime:F3}s");

            // Notify multiplayer
            if (isMultiplayerRace && multiplayerClient != null)
            {
                multiplayerClient.ReportLapCompletion(currentLap - 1, lapTime);
            }

            // Check race completion
            if (currentLap > totalLaps)
            {
                FinishRace();
            }

            // Update UI
            uiManager?.UpdateLapTime(lapTime);
        }

        private void SetupTrackCheckpoints()
        {
            // Find checkpoint objects in scene
            GameObject[] checkpointObjects = GameObject.FindGameObjectsWithTag("Checkpoint");

            checkpoints.Clear();
            foreach (GameObject cp in checkpointObjects)
            {
                checkpoints.Add(cp.transform.position);
            }

            // Sort checkpoints by their order (assuming they have an order component)
            checkpoints.Sort((a, b) => {
                // This would depend on your track setup
                return Vector3.Distance(Vector3.zero, a).CompareTo(Vector3.Distance(Vector3.zero, b));
            });

            // Find start line
            GameObject startLine = GameObject.FindGameObjectWithTag("StartLine");
            if (startLine != null)
            {
                startLinePosition = startLine.transform.position;
            }
        }

        #endregion

        #region Multiplayer Integration

        private void InitializeMultiplayer()
        {
            multiplayerClient = GetComponent<MultiplayerClient>();
            if (multiplayerClient == null)
                multiplayerClient = gameObject.AddComponent<MultiplayerClient>();

            // Subscribe to multiplayer events
            multiplayerClient.OnPlayerJoined += OnPlayerJoined;
            multiplayerClient.OnPlayerLeft += OnPlayerLeft;
            multiplayerClient.OnRaceCompleted += OnMultiplayerRaceCompleted;
            multiplayerClient.OnChatMessage += OnChatMessage;
            multiplayerClient.OnConnectionStatus += OnMultiplayerConnectionStatus;
        }

        public void OnMultiplayerRaceJoined(SessionInfo sessionInfo)
        {
            Debug.Log($"Joined multiplayer race: {sessionInfo.raceId}");
            uiManager?.UpdateMultiplayerPlayerList(new List<MultiplayerRacer>());
        }

        private void OnPlayerJoined(string playerName)
        {
            uiManager?.ShowMessage($"{playerName} joined the race");
        }

        private void OnPlayerLeft(string playerName)
        {
            uiManager?.ShowMessage($"{playerName} left the race");
        }

        private void OnMultiplayerRaceCompleted(MultiplayerRaceResults results)
        {
            Debug.Log("Multiplayer race completed");
            SetGameState(GameState.RaceResults);
        }

        private void OnChatMessage(string playerName, string message)
        {
            // Already handled by UIManager through MultiplayerClient
        }

        private void OnMultiplayerConnectionStatus(string status)
        {
            Debug.Log($"Multiplayer status: {status}");
        }

        #endregion

        #region UI Updates

        private void UpdateUI()
        {
            if (uiManager != null && playerCar != null)
            {
                var carTelemetry = playerCar.GetTelemetrySnapshot();

                uiManager.UpdateSpeedometer(carTelemetry.speed);
                uiManager.UpdateRPMGauge(carTelemetry.rpm);
                uiManager.UpdateGearDisplay(carTelemetry.gear);
                uiManager.UpdateFuelGauge(carTelemetry.fuelLevel);
                uiManager.UpdateLapTime(Time.time - raceStartTime - lapTimes.Sum());
                uiManager.UpdateRacePosition(1, 1); // Would be calculated properly in multiplayer
                uiManager.UpdateSystemStatus(
                    carTelemetry.isESCActive,
                    carTelemetry.isTCSActive,
                    carTelemetry.isABSActive
                );
                uiManager.UpdateTireTemperatures(carTelemetry.tireTemperatures);
                uiManager.UpdateBrakeTemperatures(carTelemetry.brakeTemperatures);
            }
        }

        #endregion

        #region Menu Actions

        public void ReturnToMainMenu()
        {
            if (isRaceActive)
            {
                FinishRace();
            }

            StartCoroutine(LoadMainMenu());
        }

        private IEnumerator LoadMainMenu()
        {
            yield return SceneManager.LoadSceneAsync("MainMenu", LoadSceneMode.Single);
            SetGameState(GameState.MainMenu);
        }

        public void RestartRace()
        {
            if (isMultiplayerRace)
            {
                // Can't restart multiplayer races
                ReturnToMainMenu();
            }
            else
            {
                StartSinglePlayerRace();
            }
        }

        public void SetSelectedCar(string carId)
        {
            selectedCarId = carId;
            SavePlayerData();
        }

        public void UpdatePlayerCredits(long newCredits)
        {
            playerCredits = newCredits;
            SavePlayerData();
        }

        #endregion

        #region Garage Integration

        private IEnumerator LoadGarageData()
        {
            if (apiClient != null)
            {
                yield return StartCoroutine(apiClient.GetPlayerCars(playerId,
                    (garageData) => {
                        Debug.Log($"Loaded garage data: {garageData.totalCars} cars");
                        // Process garage data
                    },
                    (error) => Debug.LogError($"Failed to load garage data: {error}")));
            }
        }

        #endregion

        #region Audio

        private void PlayCountdownSound()
        {
            // Play countdown beep sound
            if (audioSource != null)
            {
                // audioSource.PlayOneShot(countdownSound);
            }
        }

        private void PlayStartSound()
        {
            // Play race start sound
            if (audioSource != null)
            {
                // audioSource.PlayOneShot(startSound);
            }
        }

        #endregion

        #region Utility Methods

        private string GenerateSessionId()
        {
            return $"session_{System.DateTime.UtcNow:yyyyMMdd_HHmmss}_{System.Guid.NewGuid().ToString("N")[..8]}";
        }

        private string GenerateRaceId()
        {
            return $"race_{System.DateTime.UtcNow:yyyyMMdd_HHmmss}_{Random.Range(1000, 9999)}";
        }

        #endregion

        #region Application Lifecycle

        void OnApplicationPause(bool pauseStatus)
        {
            if (pauseStatus && isRaceActive)
            {
                Time.timeScale = 0f;
                uiManager?.ShowPauseMenu();
            }
        }

        void OnApplicationFocus(bool hasFocus)
        {
            if (!hasFocus && isRaceActive)
            {
                Time.timeScale = 0f;
                uiManager?.ShowPauseMenu();
            }
        }

        void OnDestroy()
        {
            SavePlayerData();

            if (telemetryCollector != null)
                telemetryCollector.StopCollection();
        }

        #endregion
    }

    #region Extension Methods

    public static class Extensions
    {
        public static float Sum(this List<float> list)
        {
            float sum = 0f;
            foreach (float value in list)
            {
                sum += value;
            }
            return sum;
        }
    }

    #endregion
}
