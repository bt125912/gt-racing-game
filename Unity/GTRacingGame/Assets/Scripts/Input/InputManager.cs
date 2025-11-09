using UnityEngine;
using System.Collections.Generic;

namespace GTRacing.Input
{
    /// <summary>
    /// Advanced input management system for GT Racing Game
    /// Handles keyboard, gamepad, steering wheel, and mobile touch controls
    /// </summary>
    public class InputManager : MonoBehaviour
    {
        public static InputManager Instance { get; private set; }

        [Header("Input Settings")]
        [SerializeField] private bool enableKeyboard = true;
        [SerializeField] private bool enableGamepad = true;
        [SerializeField] private bool enableSteeringWheel = true;
        [SerializeField] private bool enableTouchControls = false;

        [Header("Sensitivity Settings")]
        [SerializeField] private float steeringSensitivity = 1f;
        [SerializeField] private float throttleSensitivity = 1f;
        [SerializeField] private float brakeSensitivity = 1f;
        [SerializeField] private float deadZone = 0.1f;

        [Header("Steering Wheel Settings")]
        [SerializeField] private float steeringWheelRange = 900f; // degrees
        [SerializeField] private bool invertSteeringWheel = false;
        [SerializeField] private AnimationCurve steeringCurve = AnimationCurve.Linear(0, 0, 1, 1);

        [Header("Gamepad Settings")]
        [SerializeField] private bool invertGamepadSteering = false;
        [SerializeField] private float gamepadSteeringSensitivity = 1.5f;
        [SerializeField] private AnimationCurve gamepadThrottleCurve = AnimationCurve.Linear(0, 0, 1, 1);

        // Input state
        private float currentSteeringInput = 0f;
        private float currentThrottleInput = 0f;
        private float currentBrakeInput = 0f;
        private float currentClutchInput = 0f;
        private float currentHandbrakeInput = 0f;

        // Button states
        private Dictionary<string, bool> buttonStates = new Dictionary<string, bool>();
        private Dictionary<string, bool> buttonPressed = new Dictionary<string, bool>();
        private Dictionary<string, bool> buttonReleased = new Dictionary<string, bool>();

        // Touch controls (mobile)
        private Vector2 touchSteeringInput = Vector2.zero;
        private bool isTouchThrottlePressed = false;
        private bool isTouchBrakePressed = false;

        // Input smoothing
        private float steeringInputSmooth = 0f;
        private float throttleInputSmooth = 0f;
        private float brakeInputSmooth = 0f;

        // Control scheme
        public enum ControlScheme
        {
            KeyboardMouse,
            Gamepad,
            SteeringWheel,
            TouchControls
        }

        private ControlScheme currentControlScheme = ControlScheme.KeyboardMouse;

        #region Initialization

        void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                DontDestroyOnLoad(gameObject);
                InitializeInput();
            }
            else
            {
                Destroy(gameObject);
            }
        }

        private void InitializeInput()
        {
            LoadInputSettings();
            DetectControlScheme();
            InitializeButtonStates();

            // Set up touch controls for mobile platforms
            if (Application.isMobilePlatform)
            {
                enableTouchControls = true;
                currentControlScheme = ControlScheme.TouchControls;
            }
        }

        private void LoadInputSettings()
        {
            steeringSensitivity = PlayerPrefs.GetFloat("SteeringSensitivity", 1f);
            throttleSensitivity = PlayerPrefs.GetFloat("ThrottleSensitivity", 1f);
            brakeSensitivity = PlayerPrefs.GetFloat("BrakeSensitivity", 1f);
            deadZone = PlayerPrefs.GetFloat("InputDeadZone", 0.1f);

            string savedScheme = PlayerPrefs.GetString("ControlScheme", "KeyboardMouse");
            System.Enum.TryParse(savedScheme, out currentControlScheme);
        }

        private void InitializeButtonStates()
        {
            string[] buttons = {
                "GearUp", "GearDown", "Handbrake", "Clutch", "Horn",
                "LookLeft", "LookRight", "LookBack", "ResetCar",
                "PauseMenu", "ToggleHUD", "ToggleESC", "ToggleTCS",
                "CameraNext", "CameraPrevious"
            };

            foreach (string button in buttons)
            {
                buttonStates[button] = false;
                buttonPressed[button] = false;
                buttonReleased[button] = false;
            }
        }

        private void DetectControlScheme()
        {
            // Check for connected gamepads
            if (enableGamepad && UnityEngine.Input.GetJoystickNames().Length > 0)
            {
                currentControlScheme = ControlScheme.Gamepad;
            }

            // Check for steering wheel (would need specific detection logic)
            // This is simplified - real implementation would detect specific devices
            if (enableSteeringWheel)
            {
                // Check for steering wheel devices
                // currentControlScheme = ControlScheme.SteeringWheel;
            }
        }

        #endregion

        #region Input Update

        void Update()
        {
            UpdateInputs();
            UpdateButtonStates();
            SmoothInputs();

            // Auto-detect control scheme changes
            DetectControlSchemeChange();
        }

        private void UpdateInputs()
        {
            switch (currentControlScheme)
            {
                case ControlScheme.KeyboardMouse:
                    UpdateKeyboardInput();
                    break;
                case ControlScheme.Gamepad:
                    UpdateGamepadInput();
                    break;
                case ControlScheme.SteeringWheel:
                    UpdateSteeringWheelInput();
                    break;
                case ControlScheme.TouchControls:
                    UpdateTouchInput();
                    break;
            }
        }

        private void UpdateKeyboardInput()
        {
            // Steering
            float steerInput = 0f;
            if (UnityEngine.Input.GetKey(KeyCode.A) || UnityEngine.Input.GetKey(KeyCode.LeftArrow))
                steerInput -= 1f;
            if (UnityEngine.Input.GetKey(KeyCode.D) || UnityEngine.Input.GetKey(KeyCode.RightArrow))
                steerInput += 1f;

            currentSteeringInput = steerInput * steeringSensitivity;

            // Throttle
            currentThrottleInput = 0f;
            if (UnityEngine.Input.GetKey(KeyCode.W) || UnityEngine.Input.GetKey(KeyCode.UpArrow))
                currentThrottleInput = 1f * throttleSensitivity;

            // Brake
            currentBrakeInput = 0f;
            if (UnityEngine.Input.GetKey(KeyCode.S) || UnityEngine.Input.GetKey(KeyCode.DownArrow))
                currentBrakeInput = 1f * brakeSensitivity;

            // Clutch
            currentClutchInput = UnityEngine.Input.GetKey(KeyCode.LeftShift) ? 1f : 0f;

            // Handbrake
            currentHandbrakeInput = UnityEngine.Input.GetKey(KeyCode.Space) ? 1f : 0f;
        }

        private void UpdateGamepadInput()
        {
            // Steering (left stick X-axis or right stick X-axis)
            float steerInput = UnityEngine.Input.GetAxis("Horizontal");
            if (Mathf.Abs(steerInput) < deadZone) steerInput = 0f;

            if (invertGamepadSteering) steerInput = -steerInput;
            currentSteeringInput = steerInput * gamepadSteeringSensitivity;

            // Throttle (right trigger or right shoulder)
            float throttleInput = Mathf.Max(
                UnityEngine.Input.GetAxis("Fire1"), // Right trigger
                UnityEngine.Input.GetKey(KeyCode.Joystick1Button5) ? 1f : 0f // Right shoulder
            );

            currentThrottleInput = gamepadThrottleCurve.Evaluate(throttleInput) * throttleSensitivity;

            // Brake (left trigger or left shoulder)
            float brakeInput = Mathf.Max(
                UnityEngine.Input.GetAxis("Fire2"), // Left trigger
                UnityEngine.Input.GetKey(KeyCode.Joystick1Button4) ? 1f : 0f // Left shoulder
            );

            currentBrakeInput = brakeInput * brakeSensitivity;

            // Clutch (left stick click)
            currentClutchInput = UnityEngine.Input.GetKey(KeyCode.Joystick1Button8) ? 1f : 0f;

            // Handbrake (A button or X button depending on gamepad)
            currentHandbrakeInput = (UnityEngine.Input.GetKey(KeyCode.Joystick1Button0) ||
                                   UnityEngine.Input.GetKey(KeyCode.Joystick1Button2)) ? 1f : 0f;
        }

        private void UpdateSteeringWheelInput()
        {
            // This would interface with specific steering wheel APIs
            // For now, using gamepad input as fallback

            // Steering wheel rotation (normalized -1 to 1)
            float wheelRotation = UnityEngine.Input.GetAxis("Horizontal");

            // Apply steering wheel range and curve
            if (invertSteeringWheel) wheelRotation = -wheelRotation;
            wheelRotation = steeringCurve.Evaluate(Mathf.Abs(wheelRotation)) * Mathf.Sign(wheelRotation);

            currentSteeringInput = wheelRotation * steeringSensitivity;

            // Pedals (assuming separate axes for throttle and brake)
            currentThrottleInput = Mathf.Clamp01(UnityEngine.Input.GetAxis("Throttle")) * throttleSensitivity;
            currentBrakeInput = Mathf.Clamp01(UnityEngine.Input.GetAxis("Brake")) * brakeSensitivity;
            currentClutchInput = Mathf.Clamp01(UnityEngine.Input.GetAxis("Clutch"));

            // Handbrake (button on steering wheel)
            currentHandbrakeInput = UnityEngine.Input.GetKey(KeyCode.Joystick1Button6) ? 1f : 0f;
        }

        private void UpdateTouchInput()
        {
            // Handle touch steering
            if (UnityEngine.Input.touchCount > 0)
            {
                for (int i = 0; i < UnityEngine.Input.touchCount; i++)
                {
                    Touch touch = UnityEngine.Input.GetTouch(i);
                    Vector2 touchPos = Camera.main.ScreenToViewportPoint(touch.position);

                    // Left side of screen = steering
                    if (touchPos.x < 0.5f)
                    {
                        float steerRange = 0.3f; // 30% of screen width for max steering
                        float normalizedX = (touchPos.x - 0.25f) / steerRange;
                        currentSteeringInput = Mathf.Clamp(normalizedX, -1f, 1f) * steeringSensitivity;
                    }

                    // Right side of screen = throttle/brake
                    if (touchPos.x > 0.5f)
                    {
                        if (touchPos.y > 0.5f)
                        {
                            // Upper right = throttle
                            currentThrottleInput = 1f * throttleSensitivity;
                            currentBrakeInput = 0f;
                        }
                        else
                        {
                            // Lower right = brake
                            currentBrakeInput = 1f * brakeSensitivity;
                            currentThrottleInput = 0f;
                        }
                    }
                }
            }
            else
            {
                // No touches = no input
                currentSteeringInput = 0f;
                currentThrottleInput = 0f;
                currentBrakeInput = 0f;
            }

            currentClutchInput = 0f; // Not supported on touch
            currentHandbrakeInput = 0f; // Could be added as screen button
        }

        private void UpdateButtonStates()
        {
            // Store previous frame states
            var previousStates = new Dictionary<string, bool>(buttonStates);

            // Clear pressed/released states
            foreach (string key in buttonPressed.Keys.ToArray())
            {
                buttonPressed[key] = false;
                buttonReleased[key] = false;
            }

            // Update current states based on control scheme
            switch (currentControlScheme)
            {
                case ControlScheme.KeyboardMouse:
                    UpdateKeyboardButtons();
                    break;
                case ControlScheme.Gamepad:
                    UpdateGamepadButtons();
                    break;
                case ControlScheme.SteeringWheel:
                    UpdateSteeringWheelButtons();
                    break;
                case ControlScheme.TouchControls:
                    UpdateTouchButtons();
                    break;
            }

            // Calculate pressed/released states
            foreach (string button in buttonStates.Keys)
            {
                bool current = buttonStates[button];
                bool previous = previousStates.ContainsKey(button) ? previousStates[button] : false;

                buttonPressed[button] = current && !previous;
                buttonReleased[button] = !current && previous;
            }
        }

        private void UpdateKeyboardButtons()
        {
            buttonStates["GearUp"] = UnityEngine.Input.GetKey(KeyCode.E);
            buttonStates["GearDown"] = UnityEngine.Input.GetKey(KeyCode.Q);
            buttonStates["Handbrake"] = UnityEngine.Input.GetKey(KeyCode.Space);
            buttonStates["Clutch"] = UnityEngine.Input.GetKey(KeyCode.LeftShift);
            buttonStates["Horn"] = UnityEngine.Input.GetKey(KeyCode.H);
            buttonStates["LookLeft"] = UnityEngine.Input.GetKey(KeyCode.Z);
            buttonStates["LookRight"] = UnityEngine.Input.GetKey(KeyCode.X);
            buttonStates["LookBack"] = UnityEngine.Input.GetKey(KeyCode.C);
            buttonStates["ResetCar"] = UnityEngine.Input.GetKey(KeyCode.R);
            buttonStates["PauseMenu"] = UnityEngine.Input.GetKey(KeyCode.Escape);
            buttonStates["ToggleHUD"] = UnityEngine.Input.GetKey(KeyCode.Tab);
            buttonStates["ToggleESC"] = UnityEngine.Input.GetKey(KeyCode.T);
            buttonStates["ToggleTCS"] = UnityEngine.Input.GetKey(KeyCode.Y);
            buttonStates["CameraNext"] = UnityEngine.Input.GetKey(KeyCode.V);
            buttonStates["CameraPrevious"] = UnityEngine.Input.GetKey(KeyCode.B);
        }

        private void UpdateGamepadButtons()
        {
            buttonStates["GearUp"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button3); // Y button
            buttonStates["GearDown"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button0); // A button
            buttonStates["Handbrake"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button1); // B button
            buttonStates["Clutch"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button8); // Left stick
            buttonStates["Horn"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button9); // Right stick
            buttonStates["LookLeft"] = UnityEngine.Input.GetAxis("Horizontal2") < -0.5f; // Right stick left
            buttonStates["LookRight"] = UnityEngine.Input.GetAxis("Horizontal2") > 0.5f; // Right stick right
            buttonStates["LookBack"] = UnityEngine.Input.GetAxis("Vertical2") < -0.5f; // Right stick down
            buttonStates["ResetCar"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button6); // Back/Select
            buttonStates["PauseMenu"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button7); // Start/Menu
            buttonStates["ToggleHUD"] = false; // Not mapped
            buttonStates["ToggleESC"] = false; // Not mapped
            buttonStates["ToggleTCS"] = false; // Not mapped
            buttonStates["CameraNext"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button4); // Left shoulder
            buttonStates["CameraPrevious"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button5); // Right shoulder
        }

        private void UpdateSteeringWheelButtons()
        {
            // Map steering wheel buttons
            buttonStates["GearUp"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button0);
            buttonStates["GearDown"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button1);
            buttonStates["Handbrake"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button2);
            buttonStates["Horn"] = UnityEngine.Input.GetKey(KeyCode.Joystick1Button3);

            // Use keyboard for other functions
            UpdateKeyboardButtons();
        }

        private void UpdateTouchButtons()
        {
            // Touch buttons would be implemented through UI
            // For now, all buttons are false
            foreach (string key in buttonStates.Keys.ToArray())
            {
                buttonStates[key] = false;
            }
        }

        private void SmoothInputs()
        {
            float smoothTime = Time.deltaTime * 8f;

            steeringInputSmooth = Mathf.Lerp(steeringInputSmooth, currentSteeringInput, smoothTime);
            throttleInputSmooth = Mathf.Lerp(throttleInputSmooth, currentThrottleInput, smoothTime);
            brakeInputSmooth = Mathf.Lerp(brakeInputSmooth, currentBrakeInput, smoothTime);
        }

        private void DetectControlSchemeChange()
        {
            // Auto-detect scheme changes based on input
            if (enableGamepad && (Mathf.Abs(UnityEngine.Input.GetAxis("Horizontal")) > 0.1f ||
                                 UnityEngine.Input.GetAxis("Fire1") > 0.1f))
            {
                if (currentControlScheme != ControlScheme.Gamepad)
                {
                    SetControlScheme(ControlScheme.Gamepad);
                }
            }
            else if (enableKeyboard && (UnityEngine.Input.inputString.Length > 0 ||
                                       UnityEngine.Input.GetMouseButton(0)))
            {
                if (currentControlScheme != ControlScheme.KeyboardMouse)
                {
                    SetControlScheme(ControlScheme.KeyboardMouse);
                }
            }
        }

        #endregion

        #region Public Interface

        public static float GetAxis(string axisName)
        {
            if (Instance == null) return 0f;

            switch (axisName)
            {
                case "Horizontal": return Instance.steeringInputSmooth;
                case "Vertical": return Instance.throttleInputSmooth;
                case "Brake": return Instance.brakeInputSmooth;
                case "Clutch": return Instance.currentClutchInput;
                case "Handbrake": return Instance.currentHandbrakeInput;
                default: return UnityEngine.Input.GetAxis(axisName);
            }
        }

        public static bool GetButton(string buttonName)
        {
            if (Instance == null) return false;

            if (Instance.buttonStates.ContainsKey(buttonName))
            {
                return Instance.buttonStates[buttonName];
            }

            return UnityEngine.Input.GetButton(buttonName);
        }

        public static bool GetButtonDown(string buttonName)
        {
            if (Instance == null) return false;

            if (Instance.buttonPressed.ContainsKey(buttonName))
            {
                return Instance.buttonPressed[buttonName];
            }

            return UnityEngine.Input.GetButtonDown(buttonName);
        }

        public static bool GetButtonUp(string buttonName)
        {
            if (Instance == null) return false;

            if (Instance.buttonReleased.ContainsKey(buttonName))
            {
                return Instance.buttonReleased[buttonName];
            }

            return UnityEngine.Input.GetButtonUp(buttonName);
        }

        public static bool GetKey(KeyCode key)
        {
            return UnityEngine.Input.GetKey(key);
        }

        public static bool GetKeyDown(KeyCode key)
        {
            return UnityEngine.Input.GetKeyDown(key);
        }

        public static bool GetKeyUp(KeyCode key)
        {
            return UnityEngine.Input.GetKeyUp(key);
        }

        #endregion

        #region Control Scheme Management

        public void SetControlScheme(ControlScheme scheme)
        {
            if (currentControlScheme != scheme)
            {
                currentControlScheme = scheme;
                Debug.Log($"Control scheme changed to: {scheme}");

                PlayerPrefs.SetString("ControlScheme", scheme.ToString());

                // Notify UI of control scheme change
                UIManager.Instance?.OnControlSchemeChanged(scheme);
            }
        }

        public ControlScheme GetCurrentControlScheme()
        {
            return currentControlScheme;
        }

        public void CalibateSteeringWheel()
        {
            // Implement steering wheel calibration
            Debug.Log("Steering wheel calibration started");
            // This would involve a calibration UI and process
        }

        #endregion

        #region Settings

        public void SetSteeringSensitivity(float sensitivity)
        {
            steeringSensitivity = Mathf.Clamp(sensitivity, 0.1f, 3f);
            PlayerPrefs.SetFloat("SteeringSensitivity", steeringSensitivity);
        }

        public void SetThrottleSensitivity(float sensitivity)
        {
            throttleSensitivity = Mathf.Clamp(sensitivity, 0.1f, 3f);
            PlayerPrefs.SetFloat("ThrottleSensitivity", throttleSensitivity);
        }

        public void SetBrakeSensitivity(float sensitivity)
        {
            brakeSensitivity = Mathf.Clamp(sensitivity, 0.1f, 3f);
            PlayerPrefs.SetFloat("BrakeSensitivity", brakeSensitivity);
        }

        public void SetDeadZone(float deadzone)
        {
            deadZone = Mathf.Clamp(deadzone, 0f, 0.5f);
            PlayerPrefs.SetFloat("InputDeadZone", deadZone);
        }

        public float GetSteeringSensitivity() => steeringSensitivity;
        public float GetThrottleSensitivity() => throttleSensitivity;
        public float GetBrakeSensitivity() => brakeSensitivity;
        public float GetDeadZone() => deadZone;

        #endregion

        #region Haptic Feedback

        public void TriggerHapticFeedback(float intensity, float duration)
        {
            if (currentControlScheme == ControlScheme.Gamepad)
            {
                // Implement gamepad rumble
                StartCoroutine(GamepadRumble(intensity, duration));
            }
        }

        private System.Collections.IEnumerator GamepadRumble(float intensity, float duration)
        {
            // This would use platform-specific APIs for controller rumble
            yield return new WaitForSeconds(duration);
        }

        #endregion

        void OnDestroy()
        {
            PlayerPrefs.Save();
        }
    }
}
