package com.gt.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.List;
import java.util.Map;

/**
 * Track/Circuit model with elevation, weather zones, and racing line data
 */
@DynamoDbBean
@JsonIgnoreProperties(ignoreUnknown = true)
public class Track {
    private String trackId;
    private String name;
    private String location;
    private String country;
    private float lengthKm;
    private int turnCount;
    private String surfaceType; // "asphalt", "concrete", "dirt", "mixed"
    private String trackType; // "road_course", "oval", "drag_strip", "rally"
    private String difficulty; // "beginner", "intermediate", "advanced", "expert"
    private float recordLapTime; // Track record lap time in seconds
    private String recordHolder; // Player ID who holds the record
    private List<TrackSector> sectors;
    private List<WeatherZone> weatherZones;
    private List<TrackPoint> racingLine; // Optimal racing line waypoints
    private Map<String, Float> surfaceFriction; // Surface friction coefficients
    private ElevationData elevationData;
    private TrackLimits trackLimits;
    private List<CheckPoint> checkpoints;
    private StartingGrid startingGrid;
    private boolean isNightRace;
    private String timeOfDay; // "dawn", "morning", "noon", "afternoon", "sunset", "night"

    public Track() {
        this.surfaceType = "asphalt";
        this.trackType = "road_course";
        this.difficulty = "intermediate";
        this.isNightRace = false;
        this.timeOfDay = "afternoon";
    }

    public Track(String trackId, String name, float lengthKm, int turnCount) {
        this();
        this.trackId = trackId;
        this.name = name;
        this.lengthKm = lengthKm;
        this.turnCount = turnCount;
    }

    // Getters and Setters
    @DynamoDbPartitionKey
    @JsonProperty("trackId")
    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public float getLengthKm() {
        return lengthKm;
    }

    public void setLengthKm(float lengthKm) {
        this.lengthKm = lengthKm;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    public String getSurfaceType() {
        return surfaceType;
    }

    public void setSurfaceType(String surfaceType) {
        this.surfaceType = surfaceType;
    }

    public String getTrackType() {
        return trackType;
    }

    public void setTrackType(String trackType) {
        this.trackType = trackType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public float getRecordLapTime() {
        return recordLapTime;
    }

    public void setRecordLapTime(float recordLapTime) {
        this.recordLapTime = recordLapTime;
    }

    public String getRecordHolder() {
        return recordHolder;
    }

    public void setRecordHolder(String recordHolder) {
        this.recordHolder = recordHolder;
    }

    public List<TrackSector> getSectors() {
        return sectors;
    }

    public void setSectors(List<TrackSector> sectors) {
        this.sectors = sectors;
    }

    public List<WeatherZone> getWeatherZones() {
        return weatherZones;
    }

    public void setWeatherZones(List<WeatherZone> weatherZones) {
        this.weatherZones = weatherZones;
    }

    public List<TrackPoint> getRacingLine() {
        return racingLine;
    }

    public void setRacingLine(List<TrackPoint> racingLine) {
        this.racingLine = racingLine;
    }

    public Map<String, Float> getSurfaceFriction() {
        return surfaceFriction;
    }

    public void setSurfaceFriction(Map<String, Float> surfaceFriction) {
        this.surfaceFriction = surfaceFriction;
    }

    public ElevationData getElevationData() {
        return elevationData;
    }

    public void setElevationData(ElevationData elevationData) {
        this.elevationData = elevationData;
    }

    public TrackLimits getTrackLimits() {
        return trackLimits;
    }

    public void setTrackLimits(TrackLimits trackLimits) {
        this.trackLimits = trackLimits;
    }

    public List<CheckPoint> getCheckpoints() {
        return checkpoints;
    }

    public void setCheckpoints(List<CheckPoint> checkpoints) {
        this.checkpoints = checkpoints;
    }

    public StartingGrid getStartingGrid() {
        return startingGrid;
    }

    public void setStartingGrid(StartingGrid startingGrid) {
        this.startingGrid = startingGrid;
    }

    public boolean isNightRace() {
        return isNightRace;
    }

    public void setNightRace(boolean nightRace) {
        isNightRace = nightRace;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(String timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    // Nested classes for track components
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackSector {
        private int sectorNumber;
        private float startDistance; // Distance from start line in km
        private float endDistance;
        private String sectorType; // "straight", "corner", "chicane", "hairpin"
        private float optimalSpeed; // Optimal speed through sector in km/h
        private float difficulty; // Sector difficulty rating (1-10)

        // Getters and Setters
        public int getSectorNumber() { return sectorNumber; }
        public void setSectorNumber(int sectorNumber) { this.sectorNumber = sectorNumber; }

        public float getStartDistance() { return startDistance; }
        public void setStartDistance(float startDistance) { this.startDistance = startDistance; }

        public float getEndDistance() { return endDistance; }
        public void setEndDistance(float endDistance) { this.endDistance = endDistance; }

        public String getSectorType() { return sectorType; }
        public void setSectorType(String sectorType) { this.sectorType = sectorType; }

        public float getOptimalSpeed() { return optimalSpeed; }
        public void setOptimalSpeed(float optimalSpeed) { this.optimalSpeed = optimalSpeed; }

        public float getDifficulty() { return difficulty; }
        public void setDifficulty(float difficulty) { this.difficulty = difficulty; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherZone {
        private float startKm;
        private float endKm;
        private String condition; // "dry", "wet", "mixed", "snow", "ice"
        private float intensity; // 0.0 = none, 1.0 = maximum
        private float visibility; // Visibility factor (0.0 = no visibility, 1.0 = perfect)
        private float temperature; // Temperature in Celsius
        private float windSpeed; // Wind speed in m/s
        private String windDirection; // "north", "south", "east", "west", etc.

        // Getters and Setters
        public float getStartKm() { return startKm; }
        public void setStartKm(float startKm) { this.startKm = startKm; }

        public float getEndKm() { return endKm; }
        public void setEndKm(float endKm) { this.endKm = endKm; }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }

        public float getIntensity() { return intensity; }
        public void setIntensity(float intensity) { this.intensity = intensity; }

        public float getVisibility() { return visibility; }
        public void setVisibility(float visibility) { this.visibility = visibility; }

        public float getTemperature() { return temperature; }
        public void setTemperature(float temperature) { this.temperature = temperature; }

        public float getWindSpeed() { return windSpeed; }
        public void setWindSpeed(float windSpeed) { this.windSpeed = windSpeed; }

        public String getWindDirection() { return windDirection; }
        public void setWindDirection(String windDirection) { this.windDirection = windDirection; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackPoint {
        private float x, y, z; // World coordinates
        private float distance; // Distance from start line
        private float speedLimit; // Speed limit at this point
        private float camber; // Track banking/camber angle
        private String surfaceType; // Surface type at this point
        private boolean isCorner; // Is this point in a corner
        private float cornerRadius; // Corner radius if applicable

        // Getters and Setters
        public float getX() { return x; }
        public void setX(float x) { this.x = x; }

        public float getY() { return y; }
        public void setY(float y) { this.y = y; }

        public float getZ() { return z; }
        public void setZ(float z) { this.z = z; }

        public float getDistance() { return distance; }
        public void setDistance(float distance) { this.distance = distance; }

        public float getSpeedLimit() { return speedLimit; }
        public void setSpeedLimit(float speedLimit) { this.speedLimit = speedLimit; }

        public float getCamber() { return camber; }
        public void setCamber(float camber) { this.camber = camber; }

        public String getSurfaceType() { return surfaceType; }
        public void setSurfaceType(String surfaceType) { this.surfaceType = surfaceType; }

        public boolean isCorner() { return isCorner; }
        public void setCorner(boolean corner) { isCorner = corner; }

        public float getCornerRadius() { return cornerRadius; }
        public void setCornerRadius(float cornerRadius) { this.cornerRadius = cornerRadius; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElevationData {
        private float maxElevation;
        private float minElevation;
        private float elevationChange;
        private String heightmapFile; // Reference to heightmap file
        private List<ElevationPoint> elevationPoints;

        // Getters and Setters
        public float getMaxElevation() { return maxElevation; }
        public void setMaxElevation(float maxElevation) { this.maxElevation = maxElevation; }

        public float getMinElevation() { return minElevation; }
        public void setMinElevation(float minElevation) { this.minElevation = minElevation; }

        public float getElevationChange() { return elevationChange; }
        public void setElevationChange(float elevationChange) { this.elevationChange = elevationChange; }

        public String getHeightmapFile() { return heightmapFile; }
        public void setHeightmapFile(String heightmapFile) { this.heightmapFile = heightmapFile; }

        public List<ElevationPoint> getElevationPoints() { return elevationPoints; }
        public void setElevationPoints(List<ElevationPoint> elevationPoints) { this.elevationPoints = elevationPoints; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ElevationPoint {
        private float distance;
        private float elevation;
        private float grade; // Uphill/downhill percentage

        // Getters and Setters
        public float getDistance() { return distance; }
        public void setDistance(float distance) { this.distance = distance; }

        public float getElevation() { return elevation; }
        public void setElevation(float elevation) { this.elevation = elevation; }

        public float getGrade() { return grade; }
        public void setGrade(float grade) { this.grade = grade; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackLimits {
        private List<TrackBoundary> boundaries;
        private float penaltyTime; // Time penalty for track limits violation
        private int maxViolations; // Maximum violations before disqualification

        // Getters and Setters
        public List<TrackBoundary> getBoundaries() { return boundaries; }
        public void setBoundaries(List<TrackBoundary> boundaries) { this.boundaries = boundaries; }

        public float getPenaltyTime() { return penaltyTime; }
        public void setPenaltyTime(float penaltyTime) { this.penaltyTime = penaltyTime; }

        public int getMaxViolations() { return maxViolations; }
        public void setMaxViolations(int maxViolations) { this.maxViolations = maxViolations; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackBoundary {
        private float startDistance;
        private float endDistance;
        private float leftLimit;
        private float rightLimit;
        private String penaltyType; // "time", "position", "warning"

        // Getters and Setters
        public float getStartDistance() { return startDistance; }
        public void setStartDistance(float startDistance) { this.startDistance = startDistance; }

        public float getEndDistance() { return endDistance; }
        public void setEndDistance(float endDistance) { this.endDistance = endDistance; }

        public float getLeftLimit() { return leftLimit; }
        public void setLeftLimit(float leftLimit) { this.leftLimit = leftLimit; }

        public float getRightLimit() { return rightLimit; }
        public void setRightLimit(float rightLimit) { this.rightLimit = rightLimit; }

        public String getPenaltyType() { return penaltyType; }
        public void setPenaltyType(String penaltyType) { this.penaltyType = penaltyType; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckPoint {
        private int checkpointId;
        private float distance;
        private float x, y, z; // Checkpoint position
        private boolean isRequired; // Must pass through this checkpoint
        private float width; // Checkpoint width

        // Getters and Setters
        public int getCheckpointId() { return checkpointId; }
        public void setCheckpointId(int checkpointId) { this.checkpointId = checkpointId; }

        public float getDistance() { return distance; }
        public void setDistance(float distance) { this.distance = distance; }

        public float getX() { return x; }
        public void setX(float x) { this.x = x; }

        public float getY() { return y; }
        public void setY(float y) { this.y = y; }

        public float getZ() { return z; }
        public void setZ(float z) { this.z = z; }

        public boolean isRequired() { return isRequired; }
        public void setRequired(boolean required) { isRequired = required; }

        public float getWidth() { return width; }
        public void setWidth(float width) { this.width = width; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StartingGrid {
        private List<GridPosition> positions;
        private float gridSpacing; // Distance between grid positions
        private String gridType; // "single_file", "two_by_two", "three_by_three"

        // Getters and Setters
        public List<GridPosition> getPositions() { return positions; }
        public void setPositions(List<GridPosition> positions) { this.positions = positions; }

        public float getGridSpacing() { return gridSpacing; }
        public void setGridSpacing(float gridSpacing) { this.gridSpacing = gridSpacing; }

        public String getGridType() { return gridType; }
        public void setGridType(String gridType) { this.gridType = gridType; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GridPosition {
        private int position; // Grid position number (1 = pole position)
        private float x, y, z; // Grid slot coordinates
        private float heading; // Car heading direction in radians

        // Getters and Setters
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public float getX() { return x; }
        public void setX(float x) { this.x = x; }

        public float getY() { return y; }
        public void setY(float y) { this.y = y; }

        public float getZ() { return z; }
        public void setZ(float z) { this.z = z; }

        public float getHeading() { return heading; }
        public void setHeading(float heading) { this.heading = heading; }
    }

    // Utility methods
    public float getDifficultyRating() {
        // Calculate overall difficulty based on various factors
        float baseRating;
        switch (difficulty.toLowerCase()) {
            case "beginner":
                baseRating = 2.0f;
                break;
            case "intermediate":
                baseRating = 5.0f;
                break;
            case "advanced":
                baseRating = 7.0f;
                break;
            case "expert":
                baseRating = 9.0f;
                break;
            default:
                baseRating = 5.0f;
                break;
        }

        // Modify based on track characteristics
        if (turnCount > 20) baseRating += 1.0f;
        if (lengthKm > 6.0f) baseRating += 0.5f;
        if (elevationData != null && elevationData.getElevationChange() > 100.0f) baseRating += 1.0f;

        return Math.min(10.0f, baseRating);
    }

    public boolean updateRecord(float newTime, String playerId) {
        if (recordLapTime == 0.0f || newTime < recordLapTime) {
            recordLapTime = newTime;
            recordHolder = playerId;
            return true;
        }
        return false;
    }
}
