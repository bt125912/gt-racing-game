package com.gt.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@DynamoDbBean
@JsonIgnoreProperties(ignoreUnknown = true)
public class RaceResult {
    private String raceId;
    private String playerId;
    private String trackId;
    private String carId;
    private String gameMode; // "career", "time_trial", "multiplayer", "championship"
    private int position;
    private double lapTime; // Best lap time in seconds
    private double totalRaceTime; // Total race time in seconds
    private List<Double> lapTimes; // All lap times
    private double raceDistance; // in kilometers
    private int totalLaps;
    private boolean finished; // Did the player finish the race
    private boolean crashed;
    private Map<String, Object> telemetryData; // Additional telemetry
    private long experience; // XP earned from this race
    private long credits; // Credits earned from this race
    private Instant timestamp;
    private String weatherConditions;
    private String trackCondition; // "dry", "wet", "mixed"

    public RaceResult() {}

    public RaceResult(String raceId, String playerId, String trackId, String carId) {
        this.raceId = raceId;
        this.playerId = playerId;
        this.trackId = trackId;
        this.carId = carId;
        this.timestamp = Instant.now();
        this.finished = false;
        this.crashed = false;
    }

    @DynamoDbPartitionKey
    @JsonProperty("raceId")
    public String getRaceId() {
        return raceId;
    }

    public void setRaceId(String raceId) {
        this.raceId = raceId;
    }

    @DynamoDbSortKey
    @JsonProperty("playerId")
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public double getLapTime() {
        return lapTime;
    }

    public void setLapTime(double lapTime) {
        this.lapTime = lapTime;
    }

    public double getTotalRaceTime() {
        return totalRaceTime;
    }

    public void setTotalRaceTime(double totalRaceTime) {
        this.totalRaceTime = totalRaceTime;
    }

    public List<Double> getLapTimes() {
        return lapTimes;
    }

    public void setLapTimes(List<Double> lapTimes) {
        this.lapTimes = lapTimes;
        if (lapTimes != null && !lapTimes.isEmpty()) {
            this.lapTime = lapTimes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }
    }

    public double getRaceDistance() {
        return raceDistance;
    }

    public void setRaceDistance(double raceDistance) {
        this.raceDistance = raceDistance;
    }

    public int getTotalLaps() {
        return totalLaps;
    }

    public void setTotalLaps(int totalLaps) {
        this.totalLaps = totalLaps;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isCrashed() {
        return crashed;
    }

    public void setCrashed(boolean crashed) {
        this.crashed = crashed;
    }

    public Map<String, Object> getTelemetryData() {
        return telemetryData;
    }

    public void setTelemetryData(Map<String, Object> telemetryData) {
        this.telemetryData = telemetryData;
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = experience;
    }

    public long getCredits() {
        return credits;
    }

    public void setCredits(long credits) {
        this.credits = credits;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getWeatherConditions() {
        return weatherConditions;
    }

    public void setWeatherConditions(String weatherConditions) {
        this.weatherConditions = weatherConditions;
    }

    public String getTrackCondition() {
        return trackCondition;
    }

    public void setTrackCondition(String trackCondition) {
        this.trackCondition = trackCondition;
    }

    // Utility methods
    public void calculateRewards() {
        // Calculate experience and credits based on performance
        long baseExp = 100;
        long baseCredits = 500;

        if (finished) {
            // Bonus for finishing
            baseExp += 50;
            baseCredits += 200;

            // Position bonus (better position = more rewards)
            int positionBonus = Math.max(0, 10 - position);
            baseExp += positionBonus * 20;
            baseCredits += positionBonus * 100;
        }

        // Distance bonus
        baseExp += (long) (raceDistance * 5);
        baseCredits += (long) (raceDistance * 10);

        // Clean racing bonus
        if (!crashed) {
            baseExp += 25;
            baseCredits += 100;
        }

        this.experience = baseExp;
        this.credits = baseCredits;
    }
}
