using UnityEngine;
using UnityEngine.UI;
using TMPro;
using System.Collections;
using System.Collections.Generic;
using GTRacing.Network;
using GTRacing.Core;
using GTRacing.Multiplayer;

namespace GTRacing.UI
{
    /// <summary>
    /// Main UI Manager for GT Racing Game
    /// Handles HUD, menus, garage interface, and all UI interactions
    /// </summary>
    public class UIManager : MonoBehaviour
    {
        public static UIManager Instance { get; private set; }

        [Header("HUD Elements")]
        [SerializeField] private GameObject hudPanel;
        [SerializeField] private TextMeshProUGUI speedText;
        [SerializeField] private TextMeshProUGUI rpmText;
        [SerializeField] private TextMeshProUGUI gearText;
        [SerializeField] private Image rpmNeedle;
        [SerializeField] private Slider fuelGauge;
        [SerializeField] private TextMeshProUGUI lapTimeText;
        [SerializeField] private TextMeshProUGUI bestLapText;
        [SerializeField] private TextMeshProUGUI positionText;

        [Header("System Indicators")]
        [SerializeField] private GameObject escIndicator;
        [SerializeField] private GameObject tcsIndicator;
        [SerializeField] private GameObject absIndicator;
        [SerializeField] private Image[] tireTemperatureIndicators = new Image[4];
        [SerializeField] private Slider[] brakeTemperatureSliders = new Slider[4];

        [Header("Warning System")]
        [SerializeField] private GameObject warningPanel;
        [SerializeField] private TextMeshProUGUI warningText;
        [SerializeField] private Image warningIcon;
        [SerializeField] private Color[] warningColors = new Color[3]; // Info, Warning, Critical

        [Header("Menu Panels")]
        [SerializeField] private GameObject mainMenuPanel;
        [SerializeField] private GameObject garagePanel;
        [SerializeField] private GameObject multiplayerPanel;
        [SerializeField] private GameObject settingsPanel;
        [SerializeField] private GameObject raceResultsPanel;
        [SerializeField] private GameObject pauseMenuPanel;

        [Header("Garage UI")]
        [SerializeField] private Transform carListParent;
        [SerializeField] private GameObject carListItemPrefab;
        [SerializeField] private TextMeshProUGUI carNameText;
        [SerializeField] private TextMeshProUGUI carStatsText;
        [SerializeField] private Button buyCarButton;
        [SerializeField] private Button tuneCarButton;
        [SerializeField] private Button sellCarButton;
        [SerializeField] private TextMeshProUGUI playerCreditsText;

        [Header("Multiplayer UI")]
        [SerializeField] private Transform playerListParent;
        [SerializeField] private GameObject playerListItemPrefab;
        [SerializeField] private InputField chatInputField;
        [SerializeField] private ScrollRect chatScrollView;
        [SerializeField] private Transform chatContentParent;
        [SerializeField] private GameObject chatMessagePrefab;
        [SerializeField] private TextMeshProUGUI connectionStatusText;

        [Header("Race Results")]
        [SerializeField] private Transform raceResultsParent;
        [SerializeField] private GameObject raceResultItemPrefab;
        [SerializeField] private TextMeshProUGUI finalTimeText;
        [SerializeField] private TextMeshProUGUI finalPositionText;

        // Current state
        private bool isRaceActive = false;
        private bool isMenuOpen = false;
        private MenuPanel currentMenu = MenuPanel.None;
        private List<CarListItem> garageCarItems = new List<CarListItem>();
        private Queue<UIMessage> messageQueue = new Queue<UIMessage>();

        // Performance tracking
        private float currentSpeed;
        private float currentRPM;
        private int currentGear;
        private float currentLapTime;
        private float bestLapTime = 999f;
        private int currentPosition = 1;

        public enum MenuPanel
        {
            None, MainMenu, Garage, Multiplayer, Settings, RaceResults, Pause
        }

        #region Initialization

        void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
            }
            else
            {
                Destroy(gameObject);
            }
        }

        void Start()
        {
            InitializeUI();
            ShowMainMenu();
        }

        private void InitializeUI()
        {
            // Initialize all panels as inactive
            hudPanel?.SetActive(false);
            mainMenuPanel?.SetActive(true);
            garagePanel?.SetActive(false);
            multiplayerPanel?.SetActive(false);
            settingsPanel?.SetActive(false);
            raceResultsPanel?.SetActive(false);
            pauseMenuPanel?.SetActive(false);
            warningPanel?.SetActive(false);

            // Setup system indicators
            escIndicator?.SetActive(false);
            tcsIndicator?.SetActive(false);
            absIndicator?.SetActive(false);

            // Initialize tire temperature indicators
            for (int i = 0; i < tireTemperatureIndicators.Length; i++)
            {
                if (tireTemperatureIndicators[i] != null)
                    tireTemperatureIndicators[i].color = Color.green;
            }

            // Setup chat input
            if (chatInputField != null)
            {
                chatInputField.onEndEdit.AddListener(OnChatInputSubmit);
            }

            // Start message processing
            InvokeRepeating(nameof(ProcessMessageQueue), 0.1f, 0.1f);
        }

        #endregion

        #region HUD Updates

        public void UpdateSpeedometer(float speed)
        {
            currentSpeed = speed;
            if (speedText != null)
            {
                speedText.text = $"{speed:F0}";
            }
        }

        public void UpdateRPMGauge(float rpm)
        {
            currentRPM = rpm;
            if (rpmText != null)
            {
                rpmText.text = $"{rpm:F0}";
            }

            if (rpmNeedle != null)
            {
                float rpmPercent = Mathf.Clamp01(rpm / 7000f); // Assuming 7000 RPM redline
                rpmNeedle.fillAmount = rpmPercent;

                // Change color based on RPM
                if (rpmPercent > 0.9f)
                    rpmNeedle.color = Color.red;
                else if (rpmPercent > 0.8f)
                    rpmNeedle.color = Color.yellow;
                else
                    rpmNeedle.color = Color.green;
            }
        }

        public void UpdateGearDisplay(int gear)
        {
            currentGear = gear;
            if (gearText != null)
            {
                string gearDisplay = gear == 0 ? "N" : gear == -1 ? "R" : gear.ToString();
                gearText.text = gearDisplay;
            }
        }

        public void UpdateFuelGauge(float fuelLevel)
        {
            if (fuelGauge != null)
            {
                fuelGauge.value = fuelLevel;

                // Change color based on fuel level
                var fillImage = fuelGauge.fillRect?.GetComponent<Image>();
                if (fillImage != null)
                {
                    if (fuelLevel < 0.1f)
                        fillImage.color = Color.red;
                    else if (fuelLevel < 0.25f)
                        fillImage.color = Color.yellow;
                    else
                        fillImage.color = Color.green;
                }
            }
        }

        public void UpdateLapTime(float lapTime)
        {
            currentLapTime = lapTime;
            if (lapTimeText != null)
            {
                lapTimeText.text = FormatTime(lapTime);
            }
        }

        public void UpdateBestLapTime(float bestLap)
        {
            if (bestLap < bestLapTime)
            {
                bestLapTime = bestLap;
                if (bestLapText != null)
                {
                    bestLapText.text = $"Best: {FormatTime(bestLap)}";
                    bestLapText.color = Color.green; // Highlight new best lap
                    StartCoroutine(ResetBestLapColor());
                }
            }
        }

        public void UpdateRacePosition(int position, int totalRacers)
        {
            currentPosition = position;
            if (positionText != null)
            {
                positionText.text = $"{position}/{totalRacers}";
            }
        }

        public void UpdateSystemStatus(bool escActive, bool tcsActive, bool absActive)
        {
            if (escIndicator != null) escIndicator.SetActive(escActive);
            if (tcsIndicator != null) tcsIndicator.SetActive(tcsActive);
            if (absIndicator != null) absIndicator.SetActive(absActive);
        }

        public void UpdateTireTemperatures(float[] temperatures)
        {
            for (int i = 0; i < tireTemperatureIndicators.Length && i < temperatures.Length; i++)
            {
                if (tireTemperatureIndicators[i] != null)
                {
                    Color tempColor = GetTemperatureColor(temperatures[i]);
                    tireTemperatureIndicators[i].color = tempColor;
                }
            }
        }

        public void UpdateBrakeTemperatures(float[] temperatures)
        {
            for (int i = 0; i < brakeTemperatureSliders.Length && i < temperatures.Length; i++)
            {
                if (brakeTemperatureSliders[i] != null)
                {
                    float tempPercent = Mathf.Clamp01(temperatures[i] / 500f); // 500°C max
                    brakeTemperatureSliders[i].value = tempPercent;

                    var fillImage = brakeTemperatureSliders[i].fillRect?.GetComponent<Image>();
                    if (fillImage != null)
                    {
                        fillImage.color = GetTemperatureColor(temperatures[i]);
                    }
                }
            }
        }

        #endregion

        #region Menu Management

        public void ShowMainMenu()
        {
            SetActiveMenu(MenuPanel.MainMenu);
            isRaceActive = false;
            hudPanel?.SetActive(false);
            Time.timeScale = 1f;
        }

        public void ShowGarage()
        {
            SetActiveMenu(MenuPanel.Garage);
            LoadGarageData();
        }

        public void ShowMultiplayer()
        {
            SetActiveMenu(MenuPanel.Multiplayer);
            RefreshMultiplayerUI();
        }

        public void ShowSettings()
        {
            SetActiveMenu(MenuPanel.Settings);
        }

        public void ShowPauseMenu()
        {
            if (isRaceActive)
            {
                SetActiveMenu(MenuPanel.Pause);
                Time.timeScale = 0f;
            }
        }

        public void StartRace()
        {
            SetActiveMenu(MenuPanel.None);
            isRaceActive = true;
            hudPanel?.SetActive(true);
            Time.timeScale = 1f;

            // Reset race data
            currentLapTime = 0f;
            bestLapTime = 999f;
            currentPosition = 1;
        }

        public void EndRace()
        {
            isRaceActive = false;
            Time.timeScale = 1f;
        }

        private void SetActiveMenu(MenuPanel menu)
        {
            // Deactivate all menus
            mainMenuPanel?.SetActive(false);
            garagePanel?.SetActive(false);
            multiplayerPanel?.SetActive(false);
            settingsPanel?.SetActive(false);
            pauseMenuPanel?.SetActive(false);
            raceResultsPanel?.SetActive(false);

            currentMenu = menu;
            isMenuOpen = menu != MenuPanel.None;

            // Activate selected menu
            switch (menu)
            {
                case MenuPanel.MainMenu:
                    mainMenuPanel?.SetActive(true);
                    break;
                case MenuPanel.Garage:
                    garagePanel?.SetActive(true);
                    break;
                case MenuPanel.Multiplayer:
                    multiplayerPanel?.SetActive(true);
                    break;
                case MenuPanel.Settings:
                    settingsPanel?.SetActive(true);
                    break;
                case MenuPanel.Pause:
                    pauseMenuPanel?.SetActive(true);
                    break;
                case MenuPanel.RaceResults:
                    raceResultsPanel?.SetActive(true);
                    break;
            }
        }

        #endregion

        #region Garage UI

        private void LoadGarageData()
        {
            StartCoroutine(LoadGarageDataCoroutine());
        }

        private IEnumerator LoadGarageDataCoroutine()
        {
            // Get player garage data from backend
            var apiClient = APIClient.Instance;
            if (apiClient != null)
            {
                var playerId = GameManager.Instance?.PlayerId;
                if (!string.IsNullOrEmpty(playerId))
                {
                    yield return StartCoroutine(apiClient.GetPlayerCars(playerId, OnGarageDataLoaded,
                        (error) => Debug.LogError($"Failed to load garage: {error}")));
                }
            }

            // Update credits display
            UpdateCreditsDisplay();
        }

        private void OnGarageDataLoaded(GarageData garageData)
        {
            // Clear existing car list
            foreach (var item in garageCarItems)
            {
                if (item.gameObject != null)
                    Destroy(item.gameObject);
            }
            garageCarItems.Clear();

            // Populate car list
            foreach (var car in garageData.cars)
            {
                CreateCarListItem(car);
            }
        }

        private void CreateCarListItem(CarData car)
        {
            if (carListItemPrefab != null && carListParent != null)
            {
                GameObject itemGO = Instantiate(carListItemPrefab, carListParent);
                CarListItem item = itemGO.GetComponent<CarListItem>();

                if (item != null)
                {
                    item.Initialize(car, OnCarSelected);
                    garageCarItems.Add(item);
                }
            }
        }

        private void OnCarSelected(CarData car)
        {
            // Update car details panel
            if (carNameText != null)
                carNameText.text = car.name;

            if (carStatsText != null)
            {
                carStatsText.text = $"Power: {car.power} HP\n" +
                                   $"Weight: {car.mass} kg\n" +
                                   $"Top Speed: {car.topSpeed} km/h\n" +
                                   $"0-100: {car.acceleration} s";
            }

            // Update buttons
            if (buyCarButton != null)
                buyCarButton.gameObject.SetActive(!car.owned);
            if (tuneCarButton != null)
                tuneCarButton.gameObject.SetActive(car.owned);
            if (sellCarButton != null)
                sellCarButton.gameObject.SetActive(car.owned);
        }

        private void UpdateCreditsDisplay()
        {
            if (playerCreditsText != null)
            {
                var credits = GameManager.Instance?.PlayerCredits ?? 0;
                playerCreditsText.text = $"Credits: ${credits:N0}";
            }
        }

        #endregion

        #region Multiplayer UI

        private void RefreshMultiplayerUI()
        {
            // Update connection status
            var mpClient = FindObjectOfType<MultiplayerClient>();
            if (mpClient != null)
            {
                bool isConnected = mpClient.IsConnected;
                if (connectionStatusText != null)
                {
                    connectionStatusText.text = isConnected ? "Connected" : "Disconnected";
                    connectionStatusText.color = isConnected ? Color.green : Color.red;
                }
            }
        }

        public void UpdateMultiplayerPlayerList(List<MultiplayerRacer> racers)
        {
            // Clear existing player list
            if (playerListParent != null)
            {
                foreach (Transform child in playerListParent)
                {
                    Destroy(child.gameObject);
                }

                // Add players
                foreach (var racer in racers)
                {
                    CreatePlayerListItem(racer);
                }
            }
        }

        private void CreatePlayerListItem(MultiplayerRacer racer)
        {
            if (playerListItemPrefab != null && playerListParent != null)
            {
                GameObject itemGO = Instantiate(playerListItemPrefab, playerListParent);
                var nameText = itemGO.GetComponentInChildren<TextMeshProUGUI>();

                if (nameText != null)
                {
                    nameText.text = $"{racer.position_rank}. {racer.playerName}";
                }
            }
        }

        public void AddChatMessage(string playerName, string message)
        {
            if (chatMessagePrefab != null && chatContentParent != null)
            {
                GameObject msgGO = Instantiate(chatMessagePrefab, chatContentParent);
                var msgText = msgGO.GetComponentInChildren<TextMeshProUGUI>();

                if (msgText != null)
                {
                    msgText.text = $"<b>{playerName}:</b> {message}";
                }

                // Auto-scroll to bottom
                if (chatScrollView != null)
                {
                    Canvas.ForceUpdateCanvases();
                    chatScrollView.verticalNormalizedPosition = 0f;
                }
            }
        }

        private void OnChatInputSubmit(string input)
        {
            if (!string.IsNullOrEmpty(input))
            {
                var mpClient = FindObjectOfType<MultiplayerClient>();
                if (mpClient != null)
                {
                    mpClient.SendChatMessage(input);
                }

                chatInputField.text = "";
                chatInputField.ActivateInputField();
            }
        }

        #endregion

        #region Warnings and Messages

        public void ShowWarning(string message, WarningType type = WarningType.Warning)
        {
            if (warningPanel != null && warningText != null)
            {
                warningPanel.SetActive(true);
                warningText.text = message;

                if (warningIcon != null && warningColors.Length > (int)type)
                {
                    warningIcon.color = warningColors[(int)type];
                }

                // Auto-hide after 5 seconds
                StartCoroutine(HideWarningAfterDelay(5f));
            }
        }

        public void ShowMessage(string message)
        {
            var uiMessage = new UIMessage
            {
                text = message,
                duration = 3f,
                type = UIMessage.MessageType.Info
            };

            messageQueue.Enqueue(uiMessage);
        }

        public void ShowAlert(string message, string severity)
        {
            var type = WarningType.Info;
            switch (severity.ToLower())
            {
                case "warning": type = WarningType.Warning; break;
                case "critical": type = WarningType.Critical; break;
            }

            ShowWarning(message, type);
        }

        private IEnumerator HideWarningAfterDelay(float delay)
        {
            yield return new WaitForSeconds(delay);
            if (warningPanel != null)
                warningPanel.SetActive(false);
        }

        private void ProcessMessageQueue()
        {
            if (messageQueue.Count > 0)
            {
                var message = messageQueue.Dequeue();
                StartCoroutine(DisplayMessage(message));
            }
        }

        private IEnumerator DisplayMessage(UIMessage message)
        {
            // Display message in a temporary UI element
            // Implementation depends on your UI design
            yield return new WaitForSeconds(message.duration);
        }

        #endregion

        #region Race Results

        public void ShowRaceResults(object raceResults)
        {
            SetActiveMenu(MenuPanel.RaceResults);

            if (finalTimeText != null)
                finalTimeText.text = FormatTime(currentLapTime);
            if (finalPositionText != null)
                finalPositionText.text = $"Position: {currentPosition}";

            // Populate results list
            // Implementation depends on raceResults structure
        }

        public void ShowLapCompletion(string playerName, int lapNumber, float lapTime, int position)
        {
            string message = $"{playerName} completed lap {lapNumber} in {FormatTime(lapTime)} (P{position})";
            ShowMessage(message);
        }

        #endregion

        #region Input Handling

        void Update()
        {
            HandleInput();
        }

        private void HandleInput()
        {
            // Escape key for menu toggle
            if (Input.GetKeyDown(KeyCode.Escape))
            {
                if (isRaceActive)
                {
                    if (currentMenu == MenuPanel.Pause)
                        StartRace(); // Resume
                    else
                        ShowPauseMenu(); // Pause
                }
                else
                {
                    if (currentMenu != MenuPanel.MainMenu)
                        ShowMainMenu();
                }
            }

            // Tab key for HUD toggle during race
            if (Input.GetKeyDown(KeyCode.Tab) && isRaceActive)
            {
                bool hudActive = hudPanel != null ? hudPanel.activeSelf : false;
                hudPanel?.SetActive(!hudActive);
            }
        }

        #endregion

        #region Button Event Handlers

        public void OnStartRaceClicked()
        {
            GameManager.Instance?.StartSinglePlayerRace();
        }

        public void OnMultiplayerClicked()
        {
            ShowMultiplayer();
        }

        public void OnGarageClicked()
        {
            ShowGarage();
        }

        public void OnSettingsClicked()
        {
            ShowSettings();
        }

        public void OnQuitClicked()
        {
            Application.Quit();
        }

        public void OnResumeClicked()
        {
            StartRace();
        }

        public void OnRestartRaceClicked()
        {
            GameManager.Instance?.RestartRace();
        }

        public void OnMainMenuClicked()
        {
            GameManager.Instance?.ReturnToMainMenu();
        }

        #endregion

        #region Utility Methods

        private string FormatTime(float timeInSeconds)
        {
            int minutes = Mathf.FloorToInt(timeInSeconds / 60f);
            int seconds = Mathf.FloorToInt(timeInSeconds % 60f);
            int milliseconds = Mathf.FloorToInt((timeInSeconds * 1000f) % 1000f);

            return $"{minutes:00}:{seconds:00}.{milliseconds:000}";
        }

        private Color GetTemperatureColor(float temperature)
        {
            if (temperature < 60f) return Color.blue;      // Cold
            if (temperature < 85f) return Color.green;     // Optimal
            if (temperature < 100f) return Color.yellow;   // Hot
            return Color.red;                               // Overheating
        }

        private IEnumerator ResetBestLapColor()
        {
            yield return new WaitForSeconds(2f);
            if (bestLapText != null)
                bestLapText.color = Color.white;
        }

        #endregion
    }

    #region Data Structures

    public enum WarningType
    {
        Info = 0,
        Warning = 1,
        Critical = 2
    }

    [System.Serializable]
    public class UIMessage
    {
        public string text;
        public float duration;
        public MessageType type;

        public enum MessageType
        {
            Info, Warning, Error
        }
    }

    [System.Serializable]
    public class GarageData
    {
        public List<CarData> cars;
        public long totalValue;
        public int totalCars;
    }

    [System.Serializable]
    public class CarData
    {
        public string id;
        public string name;
        public int power;
        public float mass;
        public float topSpeed;
        public float acceleration;
        public bool owned;
        public long price;
    }

    #endregion
}
