using UnityEngine;
using System.Collections;
using System.Collections.Generic;
using System.Threading.Tasks;
using Newtonsoft.Json;
using WebSocketSharp;

namespace GTRacing.Multiplayer
{
    /// <summary>
    /// WebSocket client for real-time multiplayer racing
    /// Connects to AWS API Gateway WebSocket for live race sessions
    /// </summary>
    public class MultiplayerClient : MonoBehaviour
    {
        [Header("Multiplayer Configuration")]
        [SerializeField] private string websocketUrl = "wss://your-websocket.amazonaws.com/dev";
        [SerializeField] private float positionUpdateFrequency = 20f; // Hz
        [SerializeField] private float heartbeatInterval = 30f; // seconds
        [SerializeField] private int maxReconnectAttempts = 5;

        [Header("Player Info")]
        [SerializeField] private string playerId;
        [SerializeField] private string playerName;
        [SerializeField] private string carId;

        // WebSocket connection
        private WebSocket webSocket;
        private bool isConnected = false;
        private bool isInRace = false;
        private int reconnectAttempts = 0;
        private Coroutine heartbeatCoroutine;
        private Coroutine positionUpdateCoroutine;

        // Race session data
        private string currentRaceId;
        private string currentTrackId;
        private List<MultiplayerRacer> otherRacers = new List<MultiplayerRacer>();
        private Dictionary<string, GameObject> racerGameObjects = new Dictionary<string, GameObject>();

        // Local car reference
        private CarController localCar;
        private Transform localCarTransform;

        // Events
        public System.Action<string> OnPlayerJoined;
        public System.Action<string> OnPlayerLeft;
        public System.Action<MultiplayerRaceResults> OnRaceCompleted;
        public System.Action<string, string> OnChatMessage;
        public System.Action<string> OnConnectionStatus;

        public bool IsConnected => isConnected;
        public bool IsInRace => isInRace;
        public List<MultiplayerRacer> OtherRacers => otherRacers;

        #region Initialization

        void Awake()
        {
            // Get player info from GameManager or PlayerPrefs
            playerId = GameManager.Instance?.PlayerId ?? SystemInfo.deviceUniqueIdentifier;
            playerName = GameManager.Instance?.PlayerName ?? "Player";
            carId = GameManager.Instance?.SelectedCarId ?? "default_car";
        }

        void Start()
        {
            localCar = FindObjectOfType<CarController>();
            if (localCar != null)
            {
                localCarTransform = localCar.transform;
            }

            ConnectToMultiplayer();
        }

        public void ConnectToMultiplayer()
        {
            if (isConnected) return;

            try
            {
                webSocket = new WebSocket(websocketUrl);

                webSocket.OnOpen += OnWebSocketOpen;
                webSocket.OnMessage += OnWebSocketMessage;
                webSocket.OnError += OnWebSocketError;
                webSocket.OnClose += OnWebSocketClose;

                webSocket.Connect();

                Debug.Log("Attempting to connect to multiplayer server...");
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to initialize WebSocket: {e.Message}");
                OnConnectionStatus?.Invoke("Failed to connect");
            }
        }

        #endregion

        #region WebSocket Event Handlers

        private void OnWebSocketOpen(object sender, System.EventArgs e)
        {
            Debug.Log("Connected to multiplayer server");
            isConnected = true;
            reconnectAttempts = 0;

            OnConnectionStatus?.Invoke("Connected");

            // Start heartbeat
            if (heartbeatCoroutine != null)
                StopCoroutine(heartbeatCoroutine);
            heartbeatCoroutine = StartCoroutine(HeartbeatCoroutine());
        }

        private void OnWebSocketMessage(object sender, MessageEventArgs e)
        {
            try
            {
                var message = JsonConvert.DeserializeObject<WebSocketMessage>(e.Data);
                HandleMultiplayerMessage(message);
            }
            catch (System.Exception ex)
            {
                Debug.LogError($"Error parsing WebSocket message: {ex.Message}");
            }
        }

        private void OnWebSocketError(object sender, ErrorEventArgs e)
        {
            Debug.LogError($"WebSocket error: {e.Message}");
            OnConnectionStatus?.Invoke($"Error: {e.Message}");
        }

        private void OnWebSocketClose(object sender, CloseEventArgs e)
        {
            Debug.Log($"WebSocket connection closed: {e.Reason}");
            isConnected = false;
            isInRace = false;

            OnConnectionStatus?.Invoke("Disconnected");

            // Stop coroutines
            if (heartbeatCoroutine != null)
                StopCoroutine(heartbeatCoroutine);
            if (positionUpdateCoroutine != null)
                StopCoroutine(positionUpdateCoroutine);

            // Attempt reconnection
            if (reconnectAttempts < maxReconnectAttempts && !e.WasClean)
            {
                StartCoroutine(ReconnectCoroutine());
            }
        }

        #endregion

        #region Message Handling

        private void HandleMultiplayerMessage(WebSocketMessage message)
        {
            switch (message.type)
            {
                case "connected":
                    HandleConnectionConfirmation(message.data);
                    break;

                case "sessionJoined":
                    HandleSessionJoined(message.data);
                    break;

                case "playerJoined":
                    HandlePlayerJoined(message.data);
                    break;

                case "playerLeft":
                    HandlePlayerLeft(message.data);
                    break;

                case "positionUpdate":
                    HandlePositionUpdate(message.data);
                    break;

                case "lapCompleted":
                    HandleLapCompleted(message.data);
                    break;

                case "raceCompleted":
                    HandleRaceCompleted(message.data);
                    break;

                case "chatMessage":
                    HandleChatMessage(message.data);
                    break;

                case "error":
                    HandleError(message.data);
                    break;

                default:
                    Debug.LogWarning($"Unknown message type: {message.type}");
                    break;
            }
        }

        private void HandleConnectionConfirmation(object data)
        {
            var confirmData = JsonConvert.DeserializeObject<ConnectionConfirmation>(data.ToString());
            Debug.Log($"Connection confirmed with ID: {confirmData.connectionId}");
        }

        private void HandleSessionJoined(object data)
        {
            var sessionInfo = JsonConvert.DeserializeObject<SessionInfo>(data.ToString());

            currentRaceId = sessionInfo.raceId;
            currentTrackId = sessionInfo.trackId;
            isInRace = true;

            // Load existing participants
            otherRacers.Clear();
            foreach (var participant in sessionInfo.participants)
            {
                if (participant.playerId != playerId)
                {
                    var racer = new MultiplayerRacer
                    {
                        playerId = participant.playerId,
                        playerName = participant.playerName,
                        carId = participant.carId,
                        position = Vector3.zero,
                        rotation = Quaternion.identity,
                        speed = 0f
                    };

                    otherRacers.Add(racer);
                    CreateRacerGameObject(racer);
                }
            }

            Debug.Log($"Joined race session: {currentRaceId} on track: {currentTrackId}");

            // Start position updates
            if (positionUpdateCoroutine != null)
                StopCoroutine(positionUpdateCoroutine);
            positionUpdateCoroutine = StartCoroutine(PositionUpdateCoroutine());

            GameManager.Instance?.OnMultiplayerRaceJoined(sessionInfo);
        }

        private void HandlePlayerJoined(object data)
        {
            var participant = JsonConvert.DeserializeObject<RaceParticipant>(data.ToString());

            if (participant.playerId != playerId)
            {
                var racer = new MultiplayerRacer
                {
                    playerId = participant.playerId,
                    playerName = participant.playerName,
                    carId = participant.carId,
                    position = Vector3.zero,
                    rotation = Quaternion.identity,
                    speed = 0f
                };

                otherRacers.Add(racer);
                CreateRacerGameObject(racer);

                OnPlayerJoined?.Invoke(participant.playerName);
                UIManager.Instance?.ShowMessage($"{participant.playerName} joined the race");
            }
        }

        private void HandlePlayerLeft(object data)
        {
            var leftPlayer = JsonConvert.DeserializeObject<PlayerLeftData>(data.ToString());

            var racer = otherRacers.Find(r => r.playerId == leftPlayer.playerId);
            if (racer != null)
            {
                otherRacers.Remove(racer);
                RemoveRacerGameObject(racer.playerId);

                OnPlayerLeft?.Invoke(leftPlayer.playerName);
                UIManager.Instance?.ShowMessage($"{leftPlayer.playerName} left the race");
            }
        }

        private void HandlePositionUpdate(object data)
        {
            var posUpdate = JsonConvert.DeserializeObject<PositionBroadcast>(data.ToString());

            var racer = otherRacers.Find(r => r.playerId == posUpdate.playerId);
            if (racer != null)
            {
                // Update racer position with interpolation
                racer.targetPosition = new Vector3(
                    posUpdate.position.x,
                    posUpdate.position.y,
                    posUpdate.position.z
                );
                racer.targetRotation = Quaternion.Euler(0, posUpdate.position.rotationY, 0);
                racer.speed = posUpdate.position.speed;
                racer.lapProgress = posUpdate.position.lapProgress;

                // Update the game object
                UpdateRacerGameObject(racer);
            }
        }

        private void HandleLapCompleted(object data)
        {
            var lapData = JsonConvert.DeserializeObject<LapCompleteBroadcast>(data.ToString());

            UIManager.Instance?.ShowLapCompletion(
                lapData.playerName,
                lapData.lapNumber,
                lapData.lapTime,
                lapData.position
            );

            // Update race positions
            GameManager.Instance?.UpdateRacePosition(lapData.playerId, lapData.position);
        }

        private void HandleRaceCompleted(object data)
        {
            var raceResults = JsonConvert.DeserializeObject<MultiplayerRaceResults>(data.ToString());

            isInRace = false;

            // Stop position updates
            if (positionUpdateCoroutine != null)
                StopCoroutine(positionUpdateCoroutine);

            OnRaceCompleted?.Invoke(raceResults);
            UIManager.Instance?.ShowRaceResults(raceResults);
        }

        private void HandleChatMessage(object data)
        {
            var chatMessage = JsonConvert.DeserializeObject<ChatMessage>(data.ToString());

            OnChatMessage?.Invoke(chatMessage.playerName, chatMessage.message);
            UIManager.Instance?.AddChatMessage(chatMessage.playerName, chatMessage.message);
        }

        private void HandleError(object data)
        {
            var errorData = JsonConvert.DeserializeObject<ErrorMessage>(data.ToString());
            Debug.LogError($"Multiplayer error: {errorData.message}");
            UIManager.Instance?.ShowError(errorData.message);
        }

        #endregion

        #region Public Methods

        public void JoinRace(string raceId, string trackId, int totalLaps = 5)
        {
            if (!isConnected)
            {
                Debug.LogWarning("Not connected to multiplayer server");
                return;
            }

            var joinMessage = new JoinRaceMessage
            {
                action = "joinRace",
                playerId = playerId,
                playerName = playerName,
                raceId = raceId,
                trackId = trackId,
                carId = carId,
                totalLaps = totalLaps
            };

            SendMessage(joinMessage);
        }

        public void LeaveRace()
        {
            if (!isInRace) return;

            var leaveMessage = new LeaveRaceMessage
            {
                action = "leaveRace"
            };

            SendMessage(leaveMessage);

            // Clean up
            isInRace = false;
            currentRaceId = null;
            currentTrackId = null;

            foreach (var racer in otherRacers)
            {
                RemoveRacerGameObject(racer.playerId);
            }
            otherRacers.Clear();
        }

        public void SendChatMessage(string message)
        {
            if (!isInRace || string.IsNullOrEmpty(message)) return;

            var chatMessage = new SendChatMessage
            {
                action = "chatMessage",
                message = message
            };

            SendMessage(chatMessage);
        }

        public void ReportLapCompletion(int lapNumber, float lapTime)
        {
            if (!isInRace) return;

            var lapMessage = new LapFinishMessage
            {
                action = "finishLap",
                lapNumber = lapNumber,
                lapTime = lapTime
            };

            SendMessage(lapMessage);
        }

        #endregion

        #region Position Updates

        private IEnumerator PositionUpdateCoroutine()
        {
            while (isInRace && isConnected)
            {
                if (localCarTransform != null)
                {
                    SendPositionUpdate();
                }

                yield return new WaitForSeconds(1f / positionUpdateFrequency);
            }
        }

        private void SendPositionUpdate()
        {
            if (localCarTransform == null) return;

            var positionMessage = new PositionUpdateMessage
            {
                action = "updatePosition",
                position = new PlayerPosition
                {
                    x = localCarTransform.position.x,
                    y = localCarTransform.position.y,
                    z = localCarTransform.position.z,
                    rotationY = localCarTransform.eulerAngles.y,
                    speed = localCar != null ? localCar.GetCurrentSpeed() : 0f,
                    lapProgress = GameManager.Instance?.GetLapProgress() ?? 0f
                }
            };

            SendMessage(positionMessage);
        }

        #endregion

        #region Racer GameObject Management

        private void CreateRacerGameObject(MultiplayerRacer racer)
        {
            if (racerGameObjects.ContainsKey(racer.playerId))
                return;

            // Load car prefab based on carId
            GameObject carPrefab = Resources.Load<GameObject>($"Cars/{racer.carId}");
            if (carPrefab == null)
                carPrefab = Resources.Load<GameObject>("Cars/DefaultMultiplayerCar");

            if (carPrefab != null)
            {
                GameObject racerGO = Instantiate(carPrefab);
                racerGO.name = $"Multiplayer_{racer.playerName}";

                // Disable physics components for multiplayer cars
                var rb = racerGO.GetComponent<Rigidbody>();
                if (rb != null) rb.isKinematic = true;

                var carController = racerGO.GetComponent<CarController>();
                if (carController != null) carController.enabled = false;

                // Add multiplayer car component
                var mpCar = racerGO.AddComponent<MultiplayerCar>();
                mpCar.Initialize(racer);

                racerGameObjects[racer.playerId] = racerGO;
            }
        }

        private void UpdateRacerGameObject(MultiplayerRacer racer)
        {
            if (racerGameObjects.TryGetValue(racer.playerId, out GameObject racerGO))
            {
                var mpCar = racerGO.GetComponent<MultiplayerCar>();
                if (mpCar != null)
                {
                    mpCar.UpdatePosition(racer.targetPosition, racer.targetRotation, racer.speed);
                }
            }
        }

        private void RemoveRacerGameObject(string playerId)
        {
            if (racerGameObjects.TryGetValue(playerId, out GameObject racerGO))
            {
                Destroy(racerGO);
                racerGameObjects.Remove(playerId);
            }
        }

        #endregion

        #region Connection Management

        private IEnumerator HeartbeatCoroutine()
        {
            while (isConnected)
            {
                yield return new WaitForSeconds(heartbeatInterval);

                if (isConnected)
                {
                    var heartbeat = new { action = "heartbeat", timestamp = System.DateTime.UtcNow };
                    SendMessage(heartbeat);
                }
            }
        }

        private IEnumerator ReconnectCoroutine()
        {
            reconnectAttempts++;

            Debug.Log($"Attempting to reconnect... (Attempt {reconnectAttempts}/{maxReconnectAttempts})");
            OnConnectionStatus?.Invoke($"Reconnecting... ({reconnectAttempts}/{maxReconnectAttempts})");

            yield return new WaitForSeconds(2f * reconnectAttempts); // Exponential backoff

            ConnectToMultiplayer();
        }

        private void SendMessage(object message)
        {
            if (!isConnected || webSocket.ReadyState != WebSocketState.Open) return;

            try
            {
                string jsonMessage = JsonConvert.SerializeObject(message);
                webSocket.Send(jsonMessage);
            }
            catch (System.Exception e)
            {
                Debug.LogError($"Failed to send WebSocket message: {e.Message}");
            }
        }

        #endregion

        #region Cleanup

        void OnDestroy()
        {
            if (isInRace)
            {
                LeaveRace();
            }

            if (webSocket != null)
            {
                webSocket.Close();
            }
        }

        void OnApplicationPause(bool pauseStatus)
        {
            if (pauseStatus && isConnected)
            {
                webSocket?.Close();
            }
            else if (!pauseStatus && !isConnected)
            {
                ConnectToMultiplayer();
            }
        }

        #endregion
    }

    #region Data Structures

    [System.Serializable]
    public class MultiplayerRacer
    {
        public string playerId;
        public string playerName;
        public string carId;
        public Vector3 position;
        public Quaternion rotation;
        public Vector3 targetPosition;
        public Quaternion targetRotation;
        public float speed;
        public float lapProgress;
        public int currentLap;
        public float bestLapTime;
        public int position_rank;
    }

    [System.Serializable]
    public class WebSocketMessage
    {
        public string type;
        public object data;
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class PlayerPosition
    {
        public float x, y, z;
        public float rotationY;
        public float speed;
        public float lapProgress;
    }

    [System.Serializable]
    public class JoinRaceMessage
    {
        public string action;
        public string playerId;
        public string playerName;
        public string raceId;
        public string trackId;
        public string carId;
        public int totalLaps;
    }

    [System.Serializable]
    public class PositionUpdateMessage
    {
        public string action;
        public PlayerPosition position;
    }

    [System.Serializable]
    public class LapFinishMessage
    {
        public string action;
        public int lapNumber;
        public float lapTime;
    }

    [System.Serializable]
    public class SendChatMessage
    {
        public string action;
        public string message;
    }

    [System.Serializable]
    public class LeaveRaceMessage
    {
        public string action;
    }

    // Response message classes
    [System.Serializable]
    public class ConnectionConfirmation
    {
        public string connectionId;
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class SessionInfo
    {
        public string raceId;
        public string trackId;
        public int participantCount;
        public List<RaceParticipant> participants;
        public string sessionStatus;
    }

    [System.Serializable]
    public class RaceParticipant
    {
        public string playerId;
        public string playerName;
        public string carId;
        public int currentLap;
        public float bestLapTime;
    }

    [System.Serializable]
    public class PositionBroadcast
    {
        public string playerId;
        public PlayerPosition position;
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class LapCompleteBroadcast
    {
        public string playerId;
        public string playerName;
        public int lapNumber;
        public float lapTime;
        public float bestLap;
        public int position;
    }

    [System.Serializable]
    public class ChatMessage
    {
        public string playerId;
        public string playerName;
        public string message;
        public System.DateTime timestamp;
    }

    [System.Serializable]
    public class PlayerLeftData
    {
        public string playerId;
        public string playerName;
    }

    [System.Serializable]
    public class MultiplayerRaceResults
    {
        public string raceId;
        public List<RaceResultEntry> results;
        public System.DateTime completedAt;
    }

    [System.Serializable]
    public class RaceResultEntry
    {
        public int position;
        public string playerId;
        public string playerName;
        public float bestLapTime;
        public float totalTime;
        public int lapsCompleted;
    }

    [System.Serializable]
    public class ErrorMessage
    {
        public string message;
        public string code;
    }

    #endregion
}
