using UnityEngine;
using System.Collections;
using System.Collections.Generic;

namespace GTRacing.Audio
{
    /// <summary>
    /// Advanced audio system for GT Racing Game
    /// Handles engine sounds, tire sounds, environmental audio, and 3D positioning
    /// </summary>
    public class AudioController : MonoBehaviour
    {
        [Header("Engine Audio")]
        [SerializeField] private AudioClip[] engineSounds = new AudioClip[4]; // Idle, Low, Mid, High RPM
        [SerializeField] private AudioClip gearShiftSound;
        [SerializeField] private AudioClip turboSound;
        [SerializeField] private AudioClip backfireSound;

        [Header("Tire Audio")]
        [SerializeField] private AudioClip tireScreechSound;
        [SerializeField] private AudioClip tireLockupSound;
        [SerializeField] private AudioClip gravelSound;

        [Header("Environmental Audio")]
        [SerializeField] private AudioClip windSound;
        [SerializeField] private AudioClip rainSound;

        [Header("Collision Audio")]
        [SerializeField] private AudioClip[] collisionSounds = new AudioClip[3]; // Light, Medium, Heavy
        [SerializeField] private AudioClip scrapeSound;

        [Header("UI Audio")]
        [SerializeField] private AudioClip buttonClickSound;
        [SerializeField] private AudioClip warningBeepSound;
        [SerializeField] private AudioClip countdownSound;
        [SerializeField] private AudioClip raceStartSound;

        [Header("Audio Settings")]
        [SerializeField] private float masterVolume = 1f;
        [SerializeField] private float engineVolume = 0.8f;
        [SerializeField] private float environmentVolume = 0.6f;
        [SerializeField] private float uiVolume = 0.7f;
        [SerializeField] private bool enable3DAudio = true;
        [SerializeField] private float dopplerLevel = 1f;

        // Audio Sources
        private AudioSource engineAudioSource;
        private AudioSource tireAudioSource;
        private AudioSource environmentAudioSource;
        private AudioSource uiAudioSource;
        private AudioSource collisionAudioSource;
        private AudioSource turboAudioSource;

        // Engine audio management
        private float currentRPM = 800f;
        private float currentEngineLoad = 0f;
        private float targetEnginePitch = 1f;
        private float targetEngineVolume = 0.5f;
        private bool isEngineRunning = true;

        // Tire audio management
        private float currentTireSlip = 0f;
        private float tireScreechVolume = 0f;
        private bool isTireScreeching = false;

        // Environmental audio
        private float currentSpeed = 0f;
        private float windVolume = 0f;

        // Audio pooling for one-shot sounds
        private Queue<AudioSource> audioSourcePool = new Queue<AudioSource>();
        private const int poolSize = 10;

        // Reference to car controller
        private CarController carController;

        public static AudioController Instance { get; private set; }

        #region Initialization

        void Awake()
        {
            if (Instance == null)
            {
                Instance = this;
                InitializeAudioSources();
                InitializeAudioPool();
            }
            else
            {
                Destroy(gameObject);
            }
        }

        void Start()
        {
            carController = GetComponent<CarController>();
            LoadAudioSettings();
            StartCoroutine(AudioUpdateCoroutine());
        }

        private void InitializeAudioSources()
        {
            // Engine audio source
            engineAudioSource = CreateAudioSource("Engine", engineVolume);
            engineAudioSource.clip = engineSounds[0]; // Start with idle sound
            engineAudioSource.loop = true;
            engineAudioSource.Play();

            // Tire audio source
            tireAudioSource = CreateAudioSource("Tire", environmentVolume);
            tireAudioSource.clip = tireScreechSound;
            tireAudioSource.loop = true;

            // Environment audio source
            environmentAudioSource = CreateAudioSource("Environment", environmentVolume);
            environmentAudioSource.clip = windSound;
            environmentAudioSource.loop = true;
            environmentAudioSource.Play();

            // UI audio source
            uiAudioSource = CreateAudioSource("UI", uiVolume);
            uiAudioSource.spatialBlend = 0f; // 2D audio for UI

            // Collision audio source
            collisionAudioSource = CreateAudioSource("Collision", environmentVolume);

            // Turbo audio source
            turboAudioSource = CreateAudioSource("Turbo", engineVolume * 0.6f);
            turboAudioSource.clip = turboSound;
            turboAudioSource.loop = true;
        }

        private AudioSource CreateAudioSource(string name, float volume)
        {
            GameObject audioGO = new GameObject($"AudioSource_{name}");
            audioGO.transform.SetParent(transform);
            audioGO.transform.localPosition = Vector3.zero;

            AudioSource source = audioGO.AddComponent<AudioSource>();
            source.volume = volume * masterVolume;
            source.spatialBlend = enable3DAudio ? 1f : 0f;
            source.dopplerLevel = dopplerLevel;
            source.rolloffMode = AudioRolloffMode.Logarithmic;
            source.maxDistance = 500f;

            return source;
        }

        private void InitializeAudioPool()
        {
            for (int i = 0; i < poolSize; i++)
            {
                AudioSource pooledSource = CreateAudioSource($"Pooled_{i}", 1f);
                pooledSource.gameObject.SetActive(false);
                audioSourcePool.Enqueue(pooledSource);
            }
        }

        #endregion

        #region Engine Audio

        public void UpdateEngineSound(float rpm, float throttleInput)
        {
            currentRPM = rpm;
            currentEngineLoad = throttleInput;

            // Calculate engine pitch based on RPM
            float rpmNormalized = Mathf.Clamp01(rpm / 7000f); // Assuming 7000 RPM redline
            targetEnginePitch = Mathf.Lerp(0.8f, 2.2f, rpmNormalized);

            // Calculate engine volume based on load
            targetEngineVolume = Mathf.Lerp(0.3f, 1f, Mathf.Clamp01(throttleInput + 0.2f));

            // Select appropriate engine sound based on RPM range
            AudioClip targetClip = GetEngineClipForRPM(rpm);
            if (engineAudioSource.clip != targetClip)
            {
                StartCoroutine(CrossfadeEngineSound(targetClip));
            }
        }

        private AudioClip GetEngineClipForRPM(float rpm)
        {
            if (rpm < 1500f) return engineSounds[0]; // Idle
            if (rpm < 3500f) return engineSounds[1]; // Low
            if (rpm < 5500f) return engineSounds[2]; // Mid
            return engineSounds[3]; // High
        }

        private IEnumerator CrossfadeEngineSound(AudioClip newClip)
        {
            float crossfadeTime = 0.3f;
            float originalVolume = engineAudioSource.volume;

            // Fade out current clip
            for (float t = 0; t < crossfadeTime; t += Time.deltaTime)
            {
                engineAudioSource.volume = Mathf.Lerp(originalVolume, 0f, t / crossfadeTime);
                yield return null;
            }

            // Switch clip
            engineAudioSource.clip = newClip;
            engineAudioSource.Play();

            // Fade in new clip
            for (float t = 0; t < crossfadeTime; t += Time.deltaTime)
            {
                engineAudioSource.volume = Mathf.Lerp(0f, originalVolume, t / crossfadeTime);
                yield return null;
            }
        }

        public void PlayGearShift()
        {
            PlayOneShot(gearShiftSound, engineVolume);
        }

        public void PlayBackfire()
        {
            PlayOneShot(backfireSound, engineVolume * 0.8f);
        }

        public void UpdateTurboSound(float boost)
        {
            if (boost > 0.1f && !turboAudioSource.isPlaying)
            {
                turboAudioSource.Play();
            }
            else if (boost <= 0.1f && turboAudioSource.isPlaying)
            {
                turboAudioSource.Stop();
            }

            turboAudioSource.volume = boost * engineVolume * 0.6f * masterVolume;
            turboAudioSource.pitch = Mathf.Lerp(0.8f, 1.4f, boost);
        }

        #endregion

        #region Tire Audio

        public void UpdateTireSound(float maxSlip)
        {
            currentTireSlip = maxSlip;

            if (maxSlip > 0.15f)
            {
                if (!isTireScreeching)
                {
                    isTireScreeching = true;
                    tireAudioSource.Play();
                }

                tireScreechVolume = Mathf.Clamp01((maxSlip - 0.15f) * 2f);
                tireAudioSource.volume = tireScreechVolume * environmentVolume * masterVolume;
                tireAudioSource.pitch = Mathf.Lerp(0.8f, 1.3f, maxSlip);
            }
            else
            {
                if (isTireScreeching)
                {
                    isTireScreeching = false;
                    StartCoroutine(FadeOutTireSound());
                }
            }
        }

        private IEnumerator FadeOutTireSound()
        {
            float startVolume = tireAudioSource.volume;

            for (float t = 0; t < 0.5f; t += Time.deltaTime)
            {
                tireAudioSource.volume = Mathf.Lerp(startVolume, 0f, t / 0.5f);
                yield return null;
            }

            tireAudioSource.Stop();
        }

        public void PlayTireLockup(float intensity)
        {
            PlayOneShot(tireLockupSound, intensity * environmentVolume);
        }

        public void PlayTireOnGravel(float intensity)
        {
            PlayOneShot(gravelSound, intensity * environmentVolume);
        }

        #endregion

        #region Environmental Audio

        public void UpdateWindSound(float speed)
        {
            currentSpeed = speed;

            // Wind volume based on speed
            windVolume = Mathf.Clamp01(speed / 200f); // Max wind at 200 km/h
            environmentAudioSource.volume = windVolume * environmentVolume * masterVolume;

            // Wind pitch slightly increases with speed
            environmentAudioSource.pitch = Mathf.Lerp(0.9f, 1.2f, windVolume);
        }

        public void SetWeatherAudio(string weatherType)
        {
            switch (weatherType.ToLower())
            {
                case "rain":
                    environmentAudioSource.clip = rainSound;
                    environmentAudioSource.volume = 0.4f * environmentVolume * masterVolume;
                    environmentAudioSource.Play();
                    break;
                case "clear":
                default:
                    environmentAudioSource.clip = windSound;
                    break;
            }
        }

        #endregion

        #region Collision Audio

        public void PlayCollisionSound(float intensity, Vector3 position)
        {
            AudioClip collisionClip = null;

            if (intensity < 0.3f)
                collisionClip = collisionSounds[0]; // Light
            else if (intensity < 0.7f)
                collisionClip = collisionSounds[1]; // Medium
            else
                collisionClip = collisionSounds[2]; // Heavy

            if (collisionClip != null)
            {
                AudioSource source = GetPooledAudioSource();
                if (source != null)
                {
                    source.transform.position = position;
                    source.clip = collisionClip;
                    source.volume = intensity * environmentVolume * masterVolume;
                    source.Play();

                    StartCoroutine(ReturnToPool(source, collisionClip.length));
                }
            }
        }

        public void PlayScrapeSound(float intensity)
        {
            if (!collisionAudioSource.isPlaying)
            {
                collisionAudioSource.clip = scrapeSound;
                collisionAudioSource.loop = true;
                collisionAudioSource.Play();
            }

            collisionAudioSource.volume = intensity * environmentVolume * masterVolume;
        }

        public void StopScrapeSound()
        {
            if (collisionAudioSource.isPlaying && collisionAudioSource.clip == scrapeSound)
            {
                StartCoroutine(FadeOutAudioSource(collisionAudioSource, 0.3f));
            }
        }

        #endregion

        #region UI Audio

        public void PlayButtonClick()
        {
            PlayUISound(buttonClickSound);
        }

        public void PlayWarningBeep()
        {
            PlayUISound(warningBeepSound);
        }

        public void PlayCountdownBeep()
        {
            PlayUISound(countdownSound);
        }

        public void PlayRaceStart()
        {
            PlayUISound(raceStartSound);
        }

        private void PlayUISound(AudioClip clip)
        {
            if (clip != null && uiAudioSource != null)
            {
                uiAudioSource.PlayOneShot(clip, uiVolume * masterVolume);
            }
        }

        #endregion

        #region Audio Update Loop

        private IEnumerator AudioUpdateCoroutine()
        {
            while (true)
            {
                UpdateAudioSources();
                yield return new WaitForSeconds(0.05f); // 20Hz update rate
            }
        }

        private void UpdateAudioSources()
        {
            if (isEngineRunning && engineAudioSource != null)
            {
                // Smooth engine pitch and volume changes
                engineAudioSource.pitch = Mathf.MoveTowards(engineAudioSource.pitch, targetEnginePitch, Time.deltaTime * 3f);
                engineAudioSource.volume = Mathf.MoveTowards(engineAudioSource.volume,
                    targetEngineVolume * engineVolume * masterVolume, Time.deltaTime * 2f);
            }

            // Update 3D audio positioning for listeners
            if (enable3DAudio)
            {
                Update3DAudio();
            }
        }

        private void Update3DAudio()
        {
            // Update Doppler effect and distance attenuation
            if (Camera.main != null)
            {
                float distanceToCamera = Vector3.Distance(transform.position, Camera.main.transform.position);
                float distanceFactor = Mathf.Clamp01(1f - (distanceToCamera / 100f));

                // Adjust volume based on distance
                engineAudioSource.volume *= distanceFactor;
                tireAudioSource.volume *= distanceFactor;
                collisionAudioSource.volume *= distanceFactor;
            }
        }

        #endregion

        #region Audio Pool Management

        private AudioSource GetPooledAudioSource()
        {
            if (audioSourcePool.Count > 0)
            {
                AudioSource source = audioSourcePool.Dequeue();
                source.gameObject.SetActive(true);
                return source;
            }

            // If pool is empty, create a new temporary source
            return CreateAudioSource("Temp", 1f);
        }

        private IEnumerator ReturnToPool(AudioSource source, float delay)
        {
            yield return new WaitForSeconds(delay);

            source.Stop();
            source.gameObject.SetActive(false);
            audioSourcePool.Enqueue(source);
        }

        private void PlayOneShot(AudioClip clip, float volume)
        {
            if (clip != null)
            {
                AudioSource source = GetPooledAudioSource();
                if (source != null)
                {
                    source.volume = volume * masterVolume;
                    source.PlayOneShot(clip);
                    StartCoroutine(ReturnToPool(source, clip.length));
                }
            }
        }

        #endregion

        #region Volume Control

        public void SetMasterVolume(float volume)
        {
            masterVolume = Mathf.Clamp01(volume);
            UpdateAllVolumes();
            PlayerPrefs.SetFloat("MasterVolume", masterVolume);
        }

        public void SetEngineVolume(float volume)
        {
            engineVolume = Mathf.Clamp01(volume);
            UpdateAllVolumes();
            PlayerPrefs.SetFloat("EngineVolume", engineVolume);
        }

        public void SetEnvironmentVolume(float volume)
        {
            environmentVolume = Mathf.Clamp01(volume);
            UpdateAllVolumes();
            PlayerPrefs.SetFloat("EnvironmentVolume", environmentVolume);
        }

        public void SetUIVolume(float volume)
        {
            uiVolume = Mathf.Clamp01(volume);
            UpdateAllVolumes();
            PlayerPrefs.SetFloat("UIVolume", uiVolume);
        }

        private void UpdateAllVolumes()
        {
            if (engineAudioSource != null)
                engineAudioSource.volume = targetEngineVolume * engineVolume * masterVolume;
            if (environmentAudioSource != null)
                environmentAudioSource.volume = windVolume * environmentVolume * masterVolume;
            if (tireAudioSource != null)
                tireAudioSource.volume = tireScreechVolume * environmentVolume * masterVolume;
            if (turboAudioSource != null)
                turboAudioSource.volume = turboAudioSource.volume; // Recalculate in UpdateTurboSound
        }

        private void LoadAudioSettings()
        {
            masterVolume = PlayerPrefs.GetFloat("MasterVolume", 1f);
            engineVolume = PlayerPrefs.GetFloat("EngineVolume", 0.8f);
            environmentVolume = PlayerPrefs.GetFloat("EnvironmentVolume", 0.6f);
            uiVolume = PlayerPrefs.GetFloat("UIVolume", 0.7f);

            UpdateAllVolumes();
        }

        #endregion

        #region Engine State Control

        public void StartEngine()
        {
            isEngineRunning = true;
            if (!engineAudioSource.isPlaying)
            {
                engineAudioSource.Play();
            }
        }

        public void StopEngine()
        {
            isEngineRunning = false;
            StartCoroutine(FadeOutAudioSource(engineAudioSource, 2f));
            StartCoroutine(FadeOutAudioSource(turboAudioSource, 1f));
        }

        private IEnumerator FadeOutAudioSource(AudioSource source, float fadeTime)
        {
            float startVolume = source.volume;

            for (float t = 0; t < fadeTime; t += Time.deltaTime)
            {
                source.volume = Mathf.Lerp(startVolume, 0f, t / fadeTime);
                yield return null;
            }

            source.Stop();
        }

        #endregion

        #region Audio Events

        public void OnCarCollision(Collision collision)
        {
            float impactForce = collision.relativeVelocity.magnitude;
            float intensity = Mathf.Clamp01(impactForce / 20f);

            PlayCollisionSound(intensity, collision.contacts[0].point);
        }

        public void OnCarScrape(float intensity)
        {
            PlayScrapeSound(intensity);
        }

        public void OnCarStoppedScraping()
        {
            StopScrapeSound();
        }

        #endregion

        #region Cleanup

        void OnDestroy()
        {
            PlayerPrefs.Save();
        }

        #endregion
    }
}
