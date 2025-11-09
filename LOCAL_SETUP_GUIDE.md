# GT Racing Game - Local Setup Guide

## 🎮 **Complete Step-by-Step Local Setup**

### **Prerequisites**
- Unity 2023.2 or newer
- Visual Studio 2022 or Visual Studio Code
- Git (optional, for version control)
- At least 8GB RAM
- DirectX 11 compatible graphics card

---

## **STEP 1: Setup Unity Project**

### 1.1 Create New Unity Project
1. Open **Unity Hub**
2. Click **"New Project"**
3. Select **"3D (URP)"** template
4. Set Project Name: **"GTRacingGame"**
5. Choose Location: `C:\Users\INFOMERICA-1213\Projects\GTRacingGame`
6. Click **"Create Project"**

### 1.2 Configure Unity Project Settings
```csharp
// In Unity Editor:
1. Go to Edit → Project Settings
2. Player → Configuration:
   - Company Name: "GT Racing Studio"
   - Product Name: "GT Racing Game"
   - Version: "1.0.0"
3. XR Plug-in Management → Initialize XR SDK (if VR support needed)
4. Graphics → Scriptable Render Pipeline Settings:
   - Verify URP Asset is assigned
```

### 1.3 Install Required Unity Packages
```
Window → Package Manager → Install:
- Input System (2.7.0+)
- TextMeshPro (3.0.6+) 
- Universal RP (17.0.3+)
- WebSocket Sharp (optional for multiplayer)
- Newtonsoft Json (3.2.1+)
```

---

## **STEP 2: Copy Game Scripts**

### 2.1 Create Folder Structure
```
Assets/
├── Scripts/
│   ├── Car/
│   ├── Network/
│   ├── UI/
│   ├── Multiplayer/
│   ├── Audio/
│   ├── Telemetry/
│   ├── Input/
│   ├── Core/
│   ├── Physics/
│   └── Replay/
├── Prefabs/
├── Materials/
├── Scenes/
└── Resources/
```

### 2.2 Copy All Script Files
Copy these files from the implementation to your Unity project:

**Car Scripts:**
- `CarController.cs` → `Assets/Scripts/Car/`
- `CarData.cs` → `Assets/Scripts/Car/`

**Network Scripts:**
- `APIClient.cs` → `Assets/Scripts/Network/`
- `BackendPhysicsBridge.cs` → `Assets/Scripts/Network/`

**UI Scripts:**
- `UIManager.cs` → `Assets/Scripts/UI/`

**Multiplayer Scripts:**
- `MultiplayerClient.cs` → `Assets/Scripts/Multiplayer/`

**Audio Scripts:**
- `AudioController.cs` → `Assets/Scripts/Audio/`

**Telemetry Scripts:**
- `TelemetryCollector.cs` → `Assets/Scripts/Telemetry/`

**Input Scripts:**
- `InputManager.cs` → `Assets/Scripts/Input/`

**Core Scripts:**
- `GameManager.cs` → `Assets/Scripts/Core/`

**Physics Scripts:**
- `OfflinePhysicsEngine.cs` → `Assets/Scripts/Physics/`
- `ElectronicStabilitySystem.cs` → `Assets/Scripts/Physics/`

**Replay Scripts:**
- `ReplaySystem.cs` → `Assets/Scripts/Replay/`
- `GhostCar.cs` → `Assets/Scripts/Replay/`

---

## **STEP 3: Configure API Endpoints**

### 3.1 Update GameManager Configuration
Edit `GameManager.cs`:
```csharp
[Header("Game Configuration")]
[SerializeField] private string apiBaseUrl = "http://localhost:3000/dev"; // Local SAM API
[SerializeField] private string websocketUrl = "ws://localhost:3001"; // Local WebSocket
[SerializeField] private bool enableTelemetry = true;
[SerializeField] private bool enableMultiplayer = false; // Start with single player
```

### 3.2 Update APIClient Configuration
Edit `APIClient.cs`:
```csharp
[Header("API Configuration")]
[SerializeField] private string baseApiUrl = "http://localhost:3000/dev";
[SerializeField] private float requestTimeoutSeconds = 10f; // Reduced for local testing
```

---

## **STEP 4: Create Basic Game Scenes**

### 4.1 Create Main Menu Scene
1. **File → New Scene → Basic (URP)**
2. **Save as:** `Assets/Scenes/MainMenu.unity`
3. **Create UI Canvas:**
   ```
   Hierarchy → Right Click → UI → Canvas
   - Canvas Scaler → UI Scale Mode: "Scale With Screen Size"
   - Reference Resolution: 1920x1080
   ```

4. **Add Main Menu UI:**
   ```
   Canvas → Right Click → UI → Button (TextMeshPro)
   - Rename to "StartRaceButton"
   - Text: "Start Race"
   - OnClick: GameManager.StartSinglePlayerRace()
   
   Add more buttons:
   - "GarageButton" → "Garage"
   - "SettingsButton" → "Settings"
   - "QuitButton" → "Quit"
   ```

### 4.2 Create Race Scene
1. **File → New Scene → Basic (URP)**
2. **Save as:** `Assets/Scenes/Race.unity`
3. **Add Terrain:**
   ```
   GameObject → 3D Object → Terrain
   - Size: 500x500
   - Add simple track layout with Terrain tools
   ```

4. **Create Player Car:**
   ```
   GameObject → 3D Object → Cube (temporary car model)
   - Name: "PlayerCar"
   - Scale: (4, 1.5, 2)
   - Add Rigidbody component
   - Mass: 1560
   - Drag: 0.3
   - Angular Drag: 3
   ```

5. **Add Wheel Colliders:**
   ```
   PlayerCar → Create 4 Empty GameObjects:
   - "Wheel_FL", "Wheel_FR", "Wheel_RL", "Wheel_RR"
   - Position them at car corners
   - Add WheelCollider to each:
     * Mass: 20
     * Radius: 0.33
     * Wheel Damping Rate: 0.25
     * Suspension Distance: 0.15
     * Force App Point Distance: 0
     * Spring: 35000, Damper: 4500, Target Position: 0.5
   ```

### 4.3 Setup Racing HUD
1. **In Race Scene, add Canvas**
2. **Create HUD Elements:**
   ```
   Canvas → UI → Panel (rename to "HUD_Panel")
   Add child elements:
   - Text (TMP) → "SpeedText" (bottom left)
   - Text (TMP) → "RPMText" (bottom right)  
   - Text (TMP) → "GearText" (center bottom)
   - Slider → "FuelGauge" (top right)
   - Text (TMP) → "LapTimeText" (top center)
   ```

---

## **STEP 5: Setup Game Objects**

### 5.1 Create GameManager GameObject
```
Hierarchy → Create Empty → "GameManager"
Add Scripts:
- GameManager.cs
- APIClient.cs
- InputManager.cs
- AudioController.cs
- ReplaySystem.cs
```

### 5.2 Create UIManager GameObject
```
Hierarchy → Create Empty → "UIManager"
Add Script: UIManager.cs
Configure Inspector:
- Drag HUD elements to corresponding fields
- Assign menu panels
- Set up button references
```

### 5.3 Setup Player Car
```
PlayerCar GameObject:
Add Scripts:
- CarController.cs
- TelemetryCollector.cs
- BackendPhysicsBridge.cs
- AudioController.cs
- OfflinePhysicsEngine.cs

Configure CarController:
- Assign wheel colliders array
- Set physics parameters
- Configure input settings
```

---

## **STEP 6: Local Backend Setup (Optional)**

### 6.1 Install AWS SAM CLI
```bash
# Windows (using Chocolatey)
choco install aws-sam-cli

# Or download from: https://aws.amazon.com/serverless/sam/
```

### 6.2 Run Backend Locally
```bash
# Navigate to backend directory
cd C:\Users\INFOMERICA-1213\IdeaProjects\GT_game

# Build the backend
sam build

# Start local API
sam local start-api --port 3000
```

### 6.3 Alternative: Mock API Server
If SAM is not available, create a simple mock server:
```javascript
// Create mock-server.js
const express = require('express');
const app = express();
const port = 3000;

app.use(express.json());

// Mock endpoints
app.post('/dev/auth/device', (req, res) => {
  res.json({ token: 'mock-token', playerId: 'player123', expiresIn: 3600 });
});

app.post('/dev/race/start', (req, res) => {
  res.json({ sessionId: 'session123', success: true });
});

app.get('/dev/garage/:playerId/cars', (req, res) => {
  res.json({ cars: [], totalCars: 0 });
});

app.listen(port, () => {
  console.log(`Mock server running at http://localhost:${port}`);
});
```

---

## **STEP 7: Build and Test**

### 7.1 Configure Build Settings
```
File → Build Settings:
- Add Scenes: MainMenu, Race
- Target Platform: PC, Mac & Linux Standalone
- Architecture: x86_64
- Scripting Backend: IL2CPP (for performance)
```

### 7.2 Test in Editor
```
1. Play → MainMenu scene
2. Test UI navigation
3. Start race → should load Race scene
4. Test car controls (WASD/Arrow keys)
5. Check console for errors
```

### 7.3 Build Standalone
```
File → Build Settings → Build
Choose output folder: 
C:\Users\INFOMERICA-1213\Projects\GTRacingGame\Builds\
```

---

## **STEP 8: Troubleshooting Common Issues**

### 8.1 Missing References
```
Common Issues:
- "Object reference not set" → Check Inspector assignments
- "Component not found" → Verify script attachments
- "Scene not found" → Add scenes to Build Settings
```

### 8.2 Input System Issues
```
If old Input Manager conflicts:
1. Edit → Project Settings → XR Plug-in Management
2. Input System Package → "Both" or "Input System Package (New)"
3. Restart Unity Editor
```

### 8.3 Network/API Issues
```
For local testing without backend:
1. Set enableTelemetry = false in GameManager
2. Set enableMultiplayer = false
3. Use offline physics mode
```

---

## **STEP 9: Quick Start Commands**

### 9.1 Unity Quick Setup
```bash
# After Unity project creation:
1. Create folder structure in Assets/Scripts/
2. Copy all .cs files to appropriate folders
3. Create scenes: MainMenu.unity, Race.unity
4. Setup GameManager and UIManager GameObjects
5. Configure car with WheelColliders
6. Test in Play Mode
```

### 9.2 Testing Checklist
```
✓ Main menu loads without errors
✓ Car spawns in race scene  
✓ Car responds to input (WASD)
✓ HUD displays speed/RPM
✓ Physics feels responsive
✓ Can pause/resume race
✓ Can return to main menu
```

---

## **🎮 Quick Start Summary**

**Minimum Setup to Run Locally:**
1. **Unity 2023.2+** with URP template
2. **Copy all scripts** to Assets/Scripts/ folders
3. **Create 2 scenes:** MainMenu + Race
4. **Setup GameManager** with scripts attached  
5. **Create basic car** with WheelColliders
6. **Configure UIManager** with HUD elements
7. **Test in Play Mode** ▶️

**For Full Experience:**
- Run local backend with SAM
- Enable telemetry and multiplayer
- Add proper car models and tracks
- Configure audio clips
- Build for target platform

The game will run locally with offline physics simulation, providing the full GT Racing experience without requiring backend connectivity!
