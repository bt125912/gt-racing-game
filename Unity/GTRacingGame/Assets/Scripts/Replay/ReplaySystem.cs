using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using GTRacing.Telemetry;

namespace GTRacing.Replay
{
    /// <summary>
    /// Advanced replay and ghosting system for GT Racing Game
    /// Records telemetry data and replays ghost cars for time attack mode
    /// </summary>
    public class ReplaySystem : MonoBehaviour
    {
        [Header("Replay Configuration")]
        [SerializeField] private bool enableRecording = true;
        [SerializeField] private bool enableGhostCar = true;
        [SerializeField] private float recordingFrequency = 30f; // Hz
        [SerializeField] private int maxReplayLength = 600; // seconds
        [SerializeField] private bool compressReplayData = true;

        [Header("Ghost Car Settings")]
        [SerializeField] private GameObject ghostCarPrefab;
        [SerializeField] private Material ghostCarMaterial;
        [SerializeField] private bool showBestLapGhost = true;
        [SerializeField] private bool showPlayerGhost = false;
        [SerializeField] private float ghostCarAlpha = 0.5f;

        // Recording data
        private List<ReplayFrame> currentReplayFrames = new List<ReplayFrame>();
        private List<ReplayFrame> bestLapReplay = new List<ReplayFrame>();
        private bool isRecording = false;
        private float lastRecordTime = 0f;
        private float recordingStartTime = 0f;

        // Playback data
        private List<ReplayFrame> playbackFrames = new List<ReplayFrame>();
        private bool isPlayingBack = false;
        private int currentPlaybackFrame = 0;
        private float playbackStartTime = 0f;

        // Ghost cars
        private Dictionary<string, GhostCar> activeGhosts = new Dictionary<string, GhostCar>();
        private CarController playerCar;

        // File management
        private string replayDirectory;
        private const string REPLAY_FILE_EXTENSION = ".gtr"; // GT Racing replay

        public static ReplaySystem Instance { get; private set; }

        #region Initialization

        void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
                InitializeReplaySystem();
            }
            else
            {
                Destroy(gameObject);
            }
        }

        void Start()
        {
            playerCar = FindObjectOfType<CarController>();
            LoadBestLapReplay();
        }

        private void InitializeReplaySystem()
        {
            replayDirectory = Path.Combine(Application.persistentDataPath, "Replays");

            if (!Directory.Exists(replayDirectory))
            {
                Directory.CreateDirectory(replayDirectory);
            }

            Debug.Log($"Replay system initialized. Directory: {replayDirectory}");
        }

        #endregion

        #region Recording

        public void StartRecording()
        {
            if (isRecording) return;

            isRecording = true;
            currentReplayFrames.Clear();
            recordingStartTime = Time.time;
            lastRecordTime = 0f;

            Debug.Log("Replay recording started");
        }

        public void StopRecording()
        {
            if (!isRecording) return;

            isRecording = false;

            Debug.Log($"Replay recording stopped. Recorded {currentReplayFrames.Count} frames");
        }

        public void SaveCurrentReplay(string replayName, bool isBestLap = false)
        {
            if (currentReplayFrames.Count == 0)
            {
                Debug.LogWarning("No replay data to save");
                return;
            }

            var replayData = new ReplayData
            {
                replayName = replayName,
                recordingDate = System.DateTime.Now,
                trackId = GameManager.Instance?.CurrentTrackId ?? "unknown",
                carId = playerCar?.GetCarData()?.carId ?? "unknown",
                lapTime = Time.time - recordingStartTime,
                frames = new List<ReplayFrame>(currentReplayFrames)
            };

            // Save to file
            SaveReplayToFile(replayData);

            // Update best lap if this is a best lap
            if (isBestLap)
            {
                bestLapReplay = new List<ReplayFrame>(currentReplayFrames);
                SaveBestLapReplay(replayData);
            }
        }

        void Update()
        {
            if (isRecording && playerCar != null)
            {
                RecordFrame();
            }

            UpdateGhostCars();
        }

        private void RecordFrame()
        {
            float currentTime = Time.time;

            if (currentTime - lastRecordTime >= 1f / recordingFrequency)
            {
                var frame = new ReplayFrame
                {
                    timestamp = currentTime - recordingStartTime,
                    position = playerCar.transform.position,
                    rotation = playerCar.transform.rotation,
                    velocity = playerCar.GetComponent<Rigidbody>().velocity,
                    angularVelocity = playerCar.GetComponent<Rigidbody>().angularVelocity,

                    // Vehicle state
                    speed = playerCar.GetCurrentSpeed(),
                    rpm = playerCar.GetCurrentRPM(),
                    gear = playerCar.GetCurrentGear(),
                    throttleInput = playerCar.GetThrottleInput(),
                    brakeInput = playerCar.GetBrakeInput(),
                    steerInput = playerCar.GetSteerInput(),

                    // Wheel positions
                    wheelPositions = GetWheelPositions(),
                    wheelRotations = GetWheelRotations(),

                    // System states
                    escActive = playerCar.IsESCActive(),
                    tcsActive = playerCar.IsTCSActive(),
                    absActive = playerCar.IsABSActive()
                };

                currentReplayFrames.Add(frame);
                lastRecordTime = currentTime;

                // Limit replay length
                if (frame.timestamp > maxReplayLength)
                {
                    StopRecording();
                }
            }
        }

        private Vector3[] GetWheelPositions()
        {
            var positions = new Vector3[4];
            var wheelColliders = playerCar.GetWheelColliders();

            for (int i = 0; i < 4 && i < wheelColliders.Length; i++)
            {
                if (wheelColliders[i] != null)
                {
                    Vector3 pos;
                    Quaternion rot;
                    wheelColliders[i].GetWorldPose(out pos, out rot);
                    positions[i] = pos;
                }
            }

            return positions;
        }

        private Quaternion[] GetWheelRotations()
        {
            var rotations = new Quaternion[4];
            var wheelColliders = playerCar.GetWheelColliders();

            for (int i = 0; i < 4 && i < wheelColliders.Length; i++)
            {
                if (wheelColliders[i] != null)
                {
                    Vector3 pos;
                    Quaternion rot;
                    wheelColliders[i].GetWorldPose(out pos, out rot);
                    rotations[i] = rot;
                }
            }

            return rotations;
        }

        #endregion

        #region Playback

        public void StartPlayback(ReplayData replayData)
        {
            if (replayData?.frames == null || replayData.frames.Count == 0)
            {
                Debug.LogError("Invalid replay data for playback");
                return;
            }

            playbackFrames = replayData.frames;
            isPlayingBack = true;
            currentPlaybackFrame = 0;
            playbackStartTime = Time.time;

            Debug.Log($"Started playback of replay: {replayData.replayName}");
        }

        public void StopPlayback()
        {
            isPlayingBack = false;
            currentPlaybackFrame = 0;

            Debug.Log("Replay playback stopped");
        }

        public ReplayFrame GetCurrentPlaybackFrame()
        {
            if (!isPlayingBack || playbackFrames.Count == 0)
                return null;

            float playbackTime = Time.time - playbackStartTime;

            // Find the appropriate frame
            while (currentPlaybackFrame < playbackFrames.Count - 1)
            {
                if (playbackFrames[currentPlaybackFrame + 1].timestamp > playbackTime)
                    break;
                currentPlaybackFrame++;
            }

            if (currentPlaybackFrame >= playbackFrames.Count)
            {
                StopPlayback();
                return null;
            }

            return playbackFrames[currentPlaybackFrame];
        }

        #endregion

        #region Ghost Cars

        public void ShowGhostCar(string ghostId, ReplayData replayData)
        {
            if (!enableGhostCar || replayData?.frames == null)
                return;

            // Create ghost car if it doesn't exist
            if (!activeGhosts.ContainsKey(ghostId))
            {
                CreateGhostCar(ghostId, replayData);
            }

            activeGhosts[ghostId].SetReplayData(replayData);
            activeGhosts[ghostId].Show();
        }

        public void HideGhostCar(string ghostId)
        {
            if (activeGhosts.ContainsKey(ghostId))
            {
                activeGhosts[ghostId].Hide();
            }
        }

        private void CreateGhostCar(string ghostId, ReplayData replayData)
        {
            GameObject ghostCarObject;

            if (ghostCarPrefab != null)
            {
                ghostCarObject = Instantiate(ghostCarPrefab);
            }
            else
            {
                // Create a simplified ghost car from player car
                ghostCarObject = CreateSimplifiedGhostCar();
            }

            ghostCarObject.name = $"GhostCar_{ghostId}";

            // Configure ghost car appearance
            SetupGhostCarAppearance(ghostCarObject);

            // Add ghost car component
            var ghostCar = ghostCarObject.AddComponent<GhostCar>();
            ghostCar.Initialize(ghostId, replayData);

            activeGhosts[ghostId] = ghostCar;
        }

        private GameObject CreateSimplifiedGhostCar()
        {
            if (playerCar == null) return null;

            // Create a copy of the player car but remove physics components
            GameObject ghostCar = Instantiate(playerCar.gameObject);

            // Remove physics components
            var rigidbody = ghostCar.GetComponent<Rigidbody>();
            if (rigidbody != null) DestroyImmediate(rigidbody);

            var carController = ghostCar.GetComponent<CarController>();
            if (carController != null) DestroyImmediate(carController);

            var wheelColliders = ghostCar.GetComponentsInChildren<WheelCollider>();
            foreach (var collider in wheelColliders)
            {
                DestroyImmediate(collider);
            }

            return ghostCar;
        }

        private void SetupGhostCarAppearance(GameObject ghostCar)
        {
            // Make ghost car semi-transparent
            var renderers = ghostCar.GetComponentsInChildren<Renderer>();

            foreach (var renderer in renderers)
            {
                foreach (var material in renderer.materials)
                {
                    if (material.HasProperty("_Color"))
                    {
                        Color color = material.color;
                        color.a = ghostCarAlpha;
                        material.color = color;
                    }

                    // Enable transparency
                    if (material.HasProperty("_Mode"))
                    {
                        material.SetFloat("_Mode", 3); // Transparent mode
                    }
                }
            }
        }

        private void UpdateGhostCars()
        {
            foreach (var ghost in activeGhosts.Values)
            {
                ghost.UpdateGhost();
            }
        }

        public void ShowBestLapGhost()
        {
            if (showBestLapGhost && bestLapReplay.Count > 0)
            {
                var bestLapData = new ReplayData
                {
                    replayName = "Best Lap",
                    frames = bestLapReplay
                };

                ShowGhostCar("bestlap", bestLapData);
            }
        }

        public void HideBestLapGhost()
        {
            HideGhostCar("bestlap");
        }

        #endregion

        #region File Management

        private void SaveReplayToFile(ReplayData replayData)
        {
            try
            {
                string fileName = $"{replayData.replayName}_{replayData.recordingDate:yyyyMMdd_HHmmss}{REPLAY_FILE_EXTENSION}";
                string filePath = Path.Combine(replayDirectory, fileName);

                string jsonData = JsonUtility.ToJson(replayData, true);

                if (compressReplayData)
                {
                    // Implement compression if needed
                    jsonData = CompressReplayData(jsonData);
                }

                File.WriteAllText(filePath, jsonData);

                Debug.Log($"Replay saved: {filePath}");
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to save replay: {e.Message}");
            }
        }

        public ReplayData LoadReplayFromFile(string fileName)
        {
            try
            {
                string filePath = Path.Combine(replayDirectory, fileName);

                if (!File.Exists(filePath))
                {
                    Debug.LogError($"Replay file not found: {filePath}");
                    return null;
                }

                string jsonData = File.ReadAllText(filePath);

                if (compressReplayData)
                {
                    jsonData = DecompressReplayData(jsonData);
                }

                return JsonUtility.FromJson<ReplayData>(jsonData);
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to load replay: {e.Message}");
                return null;
            }
        }

        public List<string> GetAvailableReplays()
        {
            var replays = new List<string>();

            try
            {
                var files = Directory.GetFiles(replayDirectory, $"*{REPLAY_FILE_EXTENSION}");

                foreach (string file in files)
                {
                    replays.Add(Path.GetFileName(file));
                }
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to get replay list: {e.Message}");
            }

            return replays;
        }

        private void SaveBestLapReplay(ReplayData replayData)
        {
            string bestLapFile = Path.Combine(replayDirectory, $"bestlap_{replayData.trackId}{REPLAY_FILE_EXTENSION}");

            try
            {
                string jsonData = JsonUtility.ToJson(replayData, true);
                File.WriteAllText(bestLapFile, jsonData);
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to save best lap replay: {e.Message}");
            }
        }

        private void LoadBestLapReplay()
        {
            string trackId = GameManager.Instance?.CurrentTrackId ?? "unknown";
            string bestLapFile = Path.Combine(replayDirectory, $"bestlap_{trackId}{REPLAY_FILE_EXTENSION}");

            if (File.Exists(bestLapFile))
            {
                try
                {
                    string jsonData = File.ReadAllText(bestLapFile);
                    var replayData = JsonUtility.FromJson<ReplayData>(jsonData);

                    if (replayData?.frames != null)
                    {
                        bestLapReplay = replayData.frames;
                        Debug.Log($"Loaded best lap replay for track: {trackId}");
                    }
                }
                catch (System.Exception e)
                {
                    Debug.LogError($"Failed to load best lap replay: {e.Message}");
                }
            }
        }

        private string CompressReplayData(string data)
        {
            // Simple compression implementation
            // In production, would use proper compression like gzip
            return data;
        }

        private string DecompressReplayData(string data)
        {
            // Simple decompression implementation
            return data;
        }

        #endregion

        #region Public Interface

        public void StartLapRecording()
        {
            StartRecording();
        }

        public void CompleteLap(float lapTime, bool isBestLap)
        {
            StopRecording();

            string replayName = isBestLap ? "Best Lap" : $"Lap_{lapTime:F3}s";
            SaveCurrentReplay(replayName, isBestLap);

            if (isBestLap && enableGhostCar)
            {
                ShowBestLapGhost();
            }
        }

        public void EnableGhostCar(bool enable)
        {
            enableGhostCar = enable;

            if (!enable)
            {
                foreach (var ghost in activeGhosts.Values)
                {
                    ghost.Hide();
                }
            }
        }

        public ReplayStatistics GetReplayStatistics()
        {
            return new ReplayStatistics
            {
                totalReplays = GetAvailableReplays().Count,
                currentRecordingFrames = currentReplayFrames.Count,
                isRecording = isRecording,
                isPlayingBack = isPlayingBack,
                activeGhostCount = activeGhosts.Count
            };
        }

        #endregion
    }

    #region Data Structures

    [System.Serializable]
    public class ReplayData
    {
        public string replayName;
        public System.DateTime recordingDate;
        public string trackId;
        public string carId;
        public float lapTime;
        public List<ReplayFrame> frames;
    }

    [System.Serializable]
    public class ReplayFrame
    {
        public float timestamp;
        public Vector3 position;
        public Quaternion rotation;
        public Vector3 velocity;
        public Vector3 angularVelocity;

        // Vehicle state
        public float speed;
        public float rpm;
        public int gear;
        public float throttleInput;
        public float brakeInput;
        public float steerInput;

        // Wheel data
        public Vector3[] wheelPositions;
        public Quaternion[] wheelRotations;

        // System states
        public bool escActive;
        public bool tcsActive;
        public bool absActive;
    }

    [System.Serializable]
    public class ReplayStatistics
    {
        public int totalReplays;
        public int currentRecordingFrames;
        public bool isRecording;
        public bool isPlayingBack;
        public int activeGhostCount;
    }

    #endregion
}
