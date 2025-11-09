using UnityEngine;
using System.Collections.Generic;
using GTRacing.Replay;

namespace GTRacing.Replay
{
    /// <summary>
    /// Ghost car component for replaying recorded laps
    /// Provides smooth interpolation and realistic ghost car behavior
    /// </summary>
    public class GhostCar : MonoBehaviour
    {
        [Header("Ghost Car Configuration")]
        [SerializeField] private bool smoothInterpolation = true;
        [SerializeField] private float interpolationSpeed = 10f;
        [SerializeField] private bool showGhostTrail = true;
        [SerializeField] private bool enableGhostAudio = false;

        [Header("Visual Settings")]
        [SerializeField] private Color ghostColor = new Color(1f, 1f, 1f, 0.5f);
        [SerializeField] private bool showSystemIndicators = true;
        [SerializeField] private GameObject escIndicator;
        [SerializeField] private GameObject tcsIndicator;
        [SerializeField] private GameObject absIndicator;

        // Ghost data
        private string ghostId;
        private ReplayData replayData;
        private bool isVisible = false;
        private bool isPlaying = false;
        private float playbackStartTime;
        private int currentFrameIndex = 0;

        // Interpolation
        private Vector3 targetPosition;
        private Quaternion targetRotation;
        private Vector3[] targetWheelPositions = new Vector3[4];
        private Quaternion[] targetWheelRotations = new Quaternion[4];

        // Components
        private Transform[] wheelTransforms = new Transform[4];
        private TrailRenderer trailRenderer;
        private AudioSource ghostAudioSource;
        private ParticleSystem ghostParticles;

        // Performance tracking
        private float currentGhostSpeed;
        private float currentGhostRPM;
        private int currentGhostGear;

        #region Initialization

        public void Initialize(string id, ReplayData data)
        {
            ghostId = id;
            replayData = data;

            SetupGhostComponents();
            ConfigureGhostAppearance();
            FindWheelTransforms();

            // Start hidden
            Hide();
        }

        private void SetupGhostComponents()
        {
            // Add trail renderer for ghost trail
            if (showGhostTrail)
            {
                trailRenderer = gameObject.AddComponent<TrailRenderer>();
                ConfigureTrailRenderer();
            }

            // Add audio source for ghost audio
            if (enableGhostAudio)
            {
                ghostAudioSource = gameObject.AddComponent<AudioSource>();
                ConfigureGhostAudio();
            }

            // Add particle system for ghost effects
            ghostParticles = gameObject.AddComponent<ParticleSystem>();
            ConfigureGhostParticles();

            // Setup system indicators
            SetupSystemIndicators();
        }

        private void ConfigureTrailRenderer()
        {
            if (trailRenderer != null)
            {
                trailRenderer.time = 2f;
                trailRenderer.startWidth = 0.2f;
                trailRenderer.endWidth = 0.05f;
                trailRenderer.material = CreateTrailMaterial();
                trailRenderer.startColor = ghostColor;
                trailRenderer.endColor = new Color(ghostColor.r, ghostColor.g, ghostColor.b, 0f);
            }
        }

        private Material CreateTrailMaterial()
        {
            Material trailMat = new Material(Shader.Find("Sprites/Default"));
            trailMat.color = ghostColor;
            return trailMat;
        }

        private void ConfigureGhostAudio()
        {
            if (ghostAudioSource != null)
            {
                ghostAudioSource.volume = 0.3f;
                ghostAudioSource.spatialBlend = 1f; // 3D audio
                ghostAudioSource.rolloffMode = AudioRolloffMode.Logarithmic;
                ghostAudioSource.maxDistance = 100f;
            }
        }

        private void ConfigureGhostParticles()
        {
            if (ghostParticles != null)
            {
                var main = ghostParticles.main;
                main.startLifetime = 1f;
                main.startSpeed = 2f;
                main.startSize = 0.1f;
                main.startColor = ghostColor;
                main.maxParticles = 50;

                var emission = ghostParticles.emission;
                emission.rateOverTime = 0f; // Only emit when needed

                ghostParticles.Stop();
            }
        }

        private void ConfigureGhostAppearance()
        {
            var renderers = GetComponentsInChildren<Renderer>();

            foreach (var renderer in renderers)
            {
                foreach (var material in renderer.materials)
                {
                    // Make materials transparent
                    if (material.HasProperty("_Color"))
                    {
                        Color color = material.color;
                        color.a = ghostColor.a;
                        material.color = color;
                    }

                    // Set rendering mode to transparent
                    if (material.HasProperty("_Mode"))
                    {
                        material.SetFloat("_Mode", 3); // Transparent mode
                        material.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.SrcAlpha);
                        material.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
                        material.SetInt("_ZWrite", 0);
                        material.DisableKeyword("_ALPHATEST_ON");
                        material.EnableKeyword("_ALPHABLEND_ON");
                        material.DisableKeyword("_ALPHAPREMULTIPLY_ON");
                        material.renderQueue = 3000;
                    }
                }
            }
        }

        private void FindWheelTransforms()
        {
            // Find wheel transforms in the ghost car
            Transform[] allTransforms = GetComponentsInChildren<Transform>();

            int wheelIndex = 0;
            foreach (Transform t in allTransforms)
            {
                if (t.name.ToLower().Contains("wheel") && wheelIndex < 4)
                {
                    wheelTransforms[wheelIndex] = t;
                    wheelIndex++;
                }
            }
        }

        private void SetupSystemIndicators()
        {
            if (!showSystemIndicators) return;

            // Create simple indicator objects if not assigned
            if (escIndicator == null)
                escIndicator = CreateSystemIndicator("ESC", Color.yellow);
            if (tcsIndicator == null)
                tcsIndicator = CreateSystemIndicator("TCS", Color.orange);
            if (absIndicator == null)
                absIndicator = CreateSystemIndicator("ABS", Color.red);

            // Hide all indicators initially
            if (escIndicator != null) escIndicator.SetActive(false);
            if (tcsIndicator != null) tcsIndicator.SetActive(false);
            if (absIndicator != null) absIndicator.SetActive(false);
        }

        private GameObject CreateSystemIndicator(string name, Color color)
        {
            GameObject indicator = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            indicator.transform.SetParent(transform);
            indicator.transform.localPosition = Vector3.up * 2f;
            indicator.transform.localScale = Vector3.one * 0.2f;
            indicator.name = $"Ghost_{name}_Indicator";

            var renderer = indicator.GetComponent<Renderer>();
            if (renderer != null)
            {
                renderer.material.color = color;
            }

            return indicator;
        }

        #endregion

        #region Playback Control

        public void Show()
        {
            if (replayData?.frames == null || replayData.frames.Count == 0)
            {
                Debug.LogWarning($"No replay data for ghost car: {ghostId}");
                return;
            }

            isVisible = true;
            isPlaying = true;
            playbackStartTime = Time.time;
            currentFrameIndex = 0;

            gameObject.SetActive(true);

            Debug.Log($"Ghost car {ghostId} shown");
        }

        public void Hide()
        {
            isVisible = false;
            isPlaying = false;

            gameObject.SetActive(false);

            Debug.Log($"Ghost car {ghostId} hidden");
        }

        public void SetReplayData(ReplayData data)
        {
            replayData = data;
            currentFrameIndex = 0;
        }

        public void Restart()
        {
            if (isVisible)
            {
                playbackStartTime = Time.time;
                currentFrameIndex = 0;
            }
        }

        #endregion

        #region Update Loop

        public void UpdateGhost()
        {
            if (!isVisible || !isPlaying || replayData?.frames == null) return;

            float playbackTime = Time.time - playbackStartTime;
            ReplayFrame currentFrame = GetFrameAtTime(playbackTime);

            if (currentFrame != null)
            {
                UpdateGhostTransform(currentFrame);
                UpdateWheelPositions(currentFrame);
                UpdateSystemIndicators(currentFrame);
                UpdateGhostEffects(currentFrame);
                UpdatePerformanceData(currentFrame);
            }
            else
            {
                // Replay finished, restart if looping
                Restart();
            }
        }

        private ReplayFrame GetFrameAtTime(float time)
        {
            if (replayData.frames.Count == 0) return null;

            // Find the frame at the current time
            while (currentFrameIndex < replayData.frames.Count - 1)
            {
                if (replayData.frames[currentFrameIndex + 1].timestamp > time)
                    break;
                currentFrameIndex++;
            }

            if (currentFrameIndex >= replayData.frames.Count)
            {
                return null; // Replay finished
            }

            ReplayFrame currentFrame = replayData.frames[currentFrameIndex];

            // Interpolate with next frame if smoothing is enabled
            if (smoothInterpolation && currentFrameIndex < replayData.frames.Count - 1)
            {
                ReplayFrame nextFrame = replayData.frames[currentFrameIndex + 1];
                float frameDelta = nextFrame.timestamp - currentFrame.timestamp;

                if (frameDelta > 0)
                {
                    float t = (time - currentFrame.timestamp) / frameDelta;
                    return InterpolateFrames(currentFrame, nextFrame, t);
                }
            }

            return currentFrame;
        }

        private ReplayFrame InterpolateFrames(ReplayFrame frame1, ReplayFrame frame2, float t)
        {
            var interpolatedFrame = new ReplayFrame();

            // Interpolate position and rotation
            interpolatedFrame.position = Vector3.Lerp(frame1.position, frame2.position, t);
            interpolatedFrame.rotation = Quaternion.Slerp(frame1.rotation, frame2.rotation, t);
            interpolatedFrame.velocity = Vector3.Lerp(frame1.velocity, frame2.velocity, t);
            interpolatedFrame.angularVelocity = Vector3.Lerp(frame1.angularVelocity, frame2.angularVelocity, t);

            // Interpolate vehicle state
            interpolatedFrame.speed = Mathf.Lerp(frame1.speed, frame2.speed, t);
            interpolatedFrame.rpm = Mathf.Lerp(frame1.rpm, frame2.rpm, t);
            interpolatedFrame.gear = frame1.gear; // Don't interpolate gear
            interpolatedFrame.throttleInput = Mathf.Lerp(frame1.throttleInput, frame2.throttleInput, t);
            interpolatedFrame.brakeInput = Mathf.Lerp(frame1.brakeInput, frame2.brakeInput, t);
            interpolatedFrame.steerInput = Mathf.Lerp(frame1.steerInput, frame2.steerInput, t);

            // Interpolate wheel positions
            if (frame1.wheelPositions != null && frame2.wheelPositions != null)
            {
                interpolatedFrame.wheelPositions = new Vector3[4];
                interpolatedFrame.wheelRotations = new Quaternion[4];

                for (int i = 0; i < 4; i++)
                {
                    interpolatedFrame.wheelPositions[i] = Vector3.Lerp(frame1.wheelPositions[i], frame2.wheelPositions[i], t);
                    interpolatedFrame.wheelRotations[i] = Quaternion.Slerp(frame1.wheelRotations[i], frame2.wheelRotations[i], t);
                }
            }

            // System states (use current frame values)
            interpolatedFrame.escActive = frame1.escActive;
            interpolatedFrame.tcsActive = frame1.tcsActive;
            interpolatedFrame.absActive = frame1.absActive;

            return interpolatedFrame;
        }

        private void UpdateGhostTransform(ReplayFrame frame)
        {
            if (smoothInterpolation)
            {
                transform.position = Vector3.Lerp(transform.position, frame.position,
                                                 interpolationSpeed * Time.deltaTime);
                transform.rotation = Quaternion.Slerp(transform.rotation, frame.rotation,
                                                     interpolationSpeed * Time.deltaTime);
            }
            else
            {
                transform.position = frame.position;
                transform.rotation = frame.rotation;
            }
        }

        private void UpdateWheelPositions(ReplayFrame frame)
        {
            if (frame.wheelPositions == null || frame.wheelRotations == null) return;

            for (int i = 0; i < 4 && i < wheelTransforms.Length; i++)
            {
                if (wheelTransforms[i] != null)
                {
                    if (smoothInterpolation)
                    {
                        wheelTransforms[i].position = Vector3.Lerp(wheelTransforms[i].position,
                                                                  frame.wheelPositions[i],
                                                                  interpolationSpeed * Time.deltaTime);
                        wheelTransforms[i].rotation = Quaternion.Slerp(wheelTransforms[i].rotation,
                                                                      frame.wheelRotations[i],
                                                                      interpolationSpeed * Time.deltaTime);
                    }
                    else
                    {
                        wheelTransforms[i].position = frame.wheelPositions[i];
                        wheelTransforms[i].rotation = frame.wheelRotations[i];
                    }
                }
            }
        }

        private void UpdateSystemIndicators(ReplayFrame frame)
        {
            if (!showSystemIndicators) return;

            if (escIndicator != null)
                escIndicator.SetActive(frame.escActive);
            if (tcsIndicator != null)
                tcsIndicator.SetActive(frame.tcsActive);
            if (absIndicator != null)
                absIndicator.SetActive(frame.absActive);
        }

        private void UpdateGhostEffects(ReplayFrame frame)
        {
            // Update trail renderer
            if (trailRenderer != null)
            {
                trailRenderer.enabled = frame.speed > 5f; // Only show trail when moving
            }

            // Update particles for special effects
            if (ghostParticles != null)
            {
                // Emit particles during hard braking or acceleration
                if (frame.brakeInput > 0.8f || frame.throttleInput > 0.9f)
                {
                    if (!ghostParticles.isEmitting)
                    {
                        ghostParticles.Play();
                    }
                }
                else
                {
                    if (ghostParticles.isEmitting)
                    {
                        ghostParticles.Stop();
                    }
                }
            }

            // Update audio
            if (ghostAudioSource != null && enableGhostAudio)
            {
                float pitch = Mathf.Lerp(0.8f, 2f, frame.rpm / 7000f);
                ghostAudioSource.pitch = pitch;
                ghostAudioSource.volume = 0.3f * (frame.throttleInput + 0.2f);
            }
        }

        private void UpdatePerformanceData(ReplayFrame frame)
        {
            currentGhostSpeed = frame.speed;
            currentGhostRPM = frame.rpm;
            currentGhostGear = frame.gear;
        }

        #endregion

        #region Public Interface

        public bool IsVisible => isVisible;
        public bool IsPlaying => isPlaying;
        public string GhostId => ghostId;
        public float CurrentSpeed => currentGhostSpeed;
        public float CurrentRPM => currentGhostRPM;
        public int CurrentGear => currentGhostGear;

        public void SetGhostColor(Color color)
        {
            ghostColor = color;
            ConfigureGhostAppearance();

            if (trailRenderer != null)
            {
                trailRenderer.startColor = color;
                trailRenderer.endColor = new Color(color.r, color.g, color.b, 0f);
            }
        }

        public void SetInterpolationSpeed(float speed)
        {
            interpolationSpeed = Mathf.Clamp(speed, 1f, 50f);
        }

        public void EnableTrail(bool enable)
        {
            showGhostTrail = enable;
            if (trailRenderer != null)
            {
                trailRenderer.enabled = enable;
            }
        }

        public void EnableAudio(bool enable)
        {
            enableGhostAudio = enable;
            if (ghostAudioSource != null)
            {
                ghostAudioSource.enabled = enable;
            }
        }

        public Vector3 GetCurrentPosition()
        {
            return transform.position;
        }

        public float GetPlaybackProgress()
        {
            if (replayData?.frames == null || replayData.frames.Count == 0)
                return 0f;

            float playbackTime = Time.time - playbackStartTime;
            float totalTime = replayData.frames[replayData.frames.Count - 1].timestamp;

            return Mathf.Clamp01(playbackTime / totalTime);
        }

        #endregion
    }
}
