using UnityEngine;
using UnityEditor;

namespace GTRacing.Setup
{
    /// <summary>
    /// Unity Editor script to quickly setup GT Racing Game
    /// Run this after importing all scripts
    /// </summary>
    public class GTRacingGameSetup : EditorWindow
    {
        [MenuItem("GT Racing/Quick Setup")]
        public static void ShowWindow()
        {
            GetWindow<GTRacingGameSetup>("GT Racing Setup");
        }

        private void OnGUI()
        {
            GUILayout.Label("GT Racing Game Quick Setup", EditorStyles.boldLabel);
            GUILayout.Space(10);

            EditorGUILayout.HelpBox(
                "This tool will help you quickly setup the GT Racing Game in Unity.\n" +
                "Make sure you have copied all scripts to Assets/Scripts/ before proceeding.",
                MessageType.Info);

            GUILayout.Space(10);

            if (GUILayout.Button("Create Game Manager"))
            {
                CreateGameManager();
            }

            if (GUILayout.Button("Create UI Manager"))
            {
                CreateUIManager();
            }

            if (GUILayout.Button("Create Player Car"))
            {
                CreatePlayerCar();
            }

            if (GUILayout.Button("Setup Main Menu Scene"))
            {
                SetupMainMenuScene();
            }

            if (GUILayout.Button("Setup Race Scene"))
            {
                SetupRaceScene();
            }

            GUILayout.Space(10);

            if (GUILayout.Button("Complete Auto Setup"))
            {
                CompleteAutoSetup();
            }

            GUILayout.Space(10);

            EditorGUILayout.HelpBox(
                "After setup:\n" +
                "1. Configure API endpoints in GameManager\n" +
                "2. Assign UI elements in UIManager\n" +
                "3. Test in Play Mode",
                MessageType.Info);
        }

        private void CreateGameManager()
        {
            GameObject gameManager = new GameObject("GameManager");

            // Add required components
            gameManager.AddComponent<GTRacing.Core.GameManager>();
            gameManager.AddComponent<GTRacing.Network.APIClient>();
            gameManager.AddComponent<GTRacing.Input.InputManager>();
            gameManager.AddComponent<GTRacing.Audio.AudioController>();
            gameManager.AddComponent<GTRacing.Replay.ReplaySystem>();

            Debug.Log("GameManager created with all components");
        }

        private void CreateUIManager()
        {
            // Create Canvas
            GameObject canvas = new GameObject("UI Canvas");
            Canvas canvasComp = canvas.AddComponent<Canvas>();
            canvasComp.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.AddComponent<UnityEngine.UI.CanvasScaler>();
            canvas.AddComponent<UnityEngine.UI.GraphicRaycaster>();

            // Create UIManager
            GameObject uiManager = new GameObject("UIManager");
            uiManager.AddComponent<GTRacing.UI.UIManager>();

            // Create basic HUD panel
            GameObject hudPanel = new GameObject("HUD Panel", typeof(RectTransform));
            hudPanel.transform.SetParent(canvas.transform);
            hudPanel.AddComponent<UnityEngine.UI.Image>().color = new Color(0, 0, 0, 0); // Transparent

            RectTransform hudRect = hudPanel.GetComponent<RectTransform>();
            hudRect.anchorMin = Vector2.zero;
            hudRect.anchorMax = Vector2.one;
            hudRect.offsetMin = Vector2.zero;
            hudRect.offsetMax = Vector2.zero;

            Debug.Log("UIManager and basic Canvas created");
        }

        private void CreatePlayerCar()
        {
            // Create car body
            GameObject car = GameObject.CreatePrimitive(PrimitiveType.Cube);
            car.name = "PlayerCar";
            car.transform.localScale = new Vector3(4f, 1.5f, 2f);
            car.transform.position = Vector3.up * 1f;

            // Add Rigidbody
            Rigidbody rb = car.AddComponent<Rigidbody>();
            rb.mass = 1560f;
            rb.drag = 0.3f;
            rb.angularDrag = 3f;
            rb.centerOfMass = new Vector3(0, -0.5f, 0.3f);

            // Add car components
            car.AddComponent<GTRacing.Car.CarController>();
            car.AddComponent<GTRacing.Telemetry.TelemetryCollector>();
            car.AddComponent<GTRacing.Network.BackendPhysicsBridge>();
            car.AddComponent<GTRacing.Physics.OfflinePhysicsEngine>();

            // Create wheels
            CreateWheelColliders(car);

            Debug.Log("Player car created with physics components");
        }

        private void CreateWheelColliders(GameObject car)
        {
            Vector3[] wheelPositions = {
                new Vector3(-1.5f, -0.5f, 1f),   // FL
                new Vector3(1.5f, -0.5f, 1f),    // FR
                new Vector3(-1.5f, -0.5f, -1f),  // RL
                new Vector3(1.5f, -0.5f, -1f)    // RR
            };

            string[] wheelNames = { "Wheel_FL", "Wheel_FR", "Wheel_RL", "Wheel_RR" };

            for (int i = 0; i < 4; i++)
            {
                GameObject wheel = new GameObject(wheelNames[i]);
                wheel.transform.SetParent(car.transform);
                wheel.transform.localPosition = wheelPositions[i];

                WheelCollider wc = wheel.AddComponent<WheelCollider>();
                wc.mass = 20f;
                wc.radius = 0.33f;
                wc.wheelDampingRate = 0.25f;
                wc.suspensionDistance = 0.15f;

                JointSpring spring = wc.suspensionSpring;
                spring.spring = 35000f;
                spring.damper = 4500f;
                spring.targetPosition = 0.5f;
                wc.suspensionSpring = spring;
            }
        }

        private void SetupMainMenuScene()
        {
            // This would be called in the MainMenu scene
            EditorGUILayout.HelpBox("Switch to MainMenu scene first, then run this", MessageType.Warning);
        }

        private void SetupRaceScene()
        {
            // This would be called in the Race scene
            EditorGUILayout.HelpBox("Switch to Race scene first, then run this", MessageType.Warning);
        }

        private void CompleteAutoSetup()
        {
            CreateGameManager();
            CreateUIManager();
            CreatePlayerCar();

            Debug.Log("GT Racing Game auto setup complete!");
            Debug.Log("Next steps:");
            Debug.Log("1. Configure API endpoints in GameManager");
            Debug.Log("2. Assign UI elements in UIManager inspector");
            Debug.Log("3. Create MainMenu and Race scenes");
            Debug.Log("4. Test in Play Mode");
        }
    }
}
