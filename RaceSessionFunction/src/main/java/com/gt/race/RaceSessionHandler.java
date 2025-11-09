package com.gt.race;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gt.models.RaceResult;
import com.gt.models.Track;
import com.gt.models.Player;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Race Session Management Lambda Function
 * Handles race lifecycle: start, progress tracking, and completion
 */
public class RaceSessionHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RaceSessionHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<RaceResult> raceResultsTable;
    private final DynamoDbTable<Player> playerTable;

    public RaceSessionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        DynamoDbClient ddbClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();

        this.raceResultsTable = enhancedClient.table(
            System.getenv("RACE_RESULTS_TABLE"),
            TableSchema.fromBean(RaceResult.class)
        );

        this.playerTable = enhancedClient.table(
            System.getenv("PLAYER_DATA_TABLE"),
            TableSchema.fromBean(Player.class)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String httpMethod = input.getHttpMethod();
            String path = input.getPath();

            logger.info("Processing {} request for path: {}", httpMethod, path);

            return switch (httpMethod) {
                case "POST" -> {
                    if (path.contains("/race/start")) {
                        yield handleStartRace(input);
                    } else if (path.contains("/race/end")) {
                        yield handleEndRace(input);
                    } else {
                        yield createErrorResponse(400, "Invalid endpoint");
                    }
                }
                case "GET" -> {
                    if (path.contains("/race/") && path.contains("/status")) {
                        yield handleGetRaceStatus(input);
                    } else {
                        yield createErrorResponse(400, "Invalid endpoint");
                    }
                }
                default -> createErrorResponse(405, "Method not allowed");
            };

        } catch (Exception e) {
            logger.error("Error processing request", e);
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handle race start request
     */
    private APIGatewayProxyResponseEvent handleStartRace(APIGatewayProxyRequestEvent input) {
        try {
            RaceStartRequest request = objectMapper.readValue(input.getBody(), RaceStartRequest.class);

            // Validate request
            if (request.getPlayerId() == null || request.getTrackId() == null || request.getCarId() == null) {
                return createErrorResponse(400, "Missing required fields: playerId, trackId, carId");
            }

            // Generate unique race ID
            String raceId = generateRaceId();

            // Create race session
            RaceSession session = new RaceSession();
            session.setRaceId(raceId);
            session.setPlayerId(request.getPlayerId());
            session.setTrackId(request.getTrackId());
            session.setCarId(request.getCarId());
            session.setGameMode(request.getGameMode() != null ? request.getGameMode() : "time_trial");
            session.setSessionStart(Instant.now());
            session.setStatus("STARTED");
            session.setLapCount(0);
            session.setBestLapTime(0.0);
            session.setCurrentLapTime(0.0);
            session.setTotalRaceTime(0.0);
            session.setWeatherConditions(request.getWeatherConditions() != null ? request.getWeatherConditions() : "clear");

            // Initialize race result record
            RaceResult raceResult = new RaceResult(raceId, request.getPlayerId(), request.getTrackId(), request.getCarId());
            raceResult.setGameMode(session.getGameMode());
            raceResult.setWeatherConditions(session.getWeatherConditions());
            raceResult.setTrackCondition(request.getTrackCondition() != null ? request.getTrackCondition() : "dry");
            raceResult.setTimestamp(Instant.now());

            // Store in DynamoDB
            raceResultsTable.putItem(raceResult);

            logger.info("Started race session: {} for player: {}", raceId, request.getPlayerId());

            return createSuccessResponse(session);

        } catch (Exception e) {
            logger.error("Error starting race", e);
            return createErrorResponse(500, "Failed to start race: " + e.getMessage());
        }
    }

    /**
     * Handle race end request with results
     */
    private APIGatewayProxyResponseEvent handleEndRace(APIGatewayProxyRequestEvent input) {
        try {
            RaceEndRequest request = objectMapper.readValue(input.getBody(), RaceEndRequest.class);

            // Validate request
            if (request.getRaceId() == null || request.getPlayerId() == null) {
                return createErrorResponse(400, "Missing required fields: raceId, playerId");
            }

            // Get existing race result
            RaceResult raceResult = raceResultsTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getRaceId()).sortValue(request.getPlayerId()))
            );

            if (raceResult == null) {
                return createErrorResponse(404, "Race session not found");
            }

            // Update race result with final data
            raceResult.setPosition(request.getPosition());
            raceResult.setLapTime(request.getBestLapTime());
            raceResult.setTotalRaceTime(request.getTotalRaceTime());
            raceResult.setLapTimes(request.getLapTimes());
            raceResult.setRaceDistance(request.getRaceDistance());
            raceResult.setTotalLaps(request.getTotalLaps());
            raceResult.setFinished(request.isFinished());
            raceResult.setCrashed(request.isCrashed());
            raceResult.setTelemetryData(request.getTelemetryData());

            // Calculate rewards
            raceResult.calculateRewards();

            // Update player stats and credits
            updatePlayerAfterRace(raceResult);

            // Save final race result
            raceResultsTable.putItem(raceResult);

            // Create race summary
            RaceSummary summary = new RaceSummary();
            summary.setRaceId(request.getRaceId());
            summary.setFinalPosition(request.getPosition());
            summary.setBestLapTime(request.getBestLapTime());
            summary.setTotalRaceTime(request.getTotalRaceTime());
            summary.setExperienceEarned(raceResult.getExperience());
            summary.setCreditsEarned(raceResult.getCredits());
            summary.setLapTimes(request.getLapTimes());
            summary.setPersonalBest(isPersonalBest(raceResult));

            logger.info("Completed race session: {} for player: {}", request.getRaceId(), request.getPlayerId());

            return createSuccessResponse(summary);

        } catch (Exception e) {
            logger.error("Error ending race", e);
            return createErrorResponse(500, "Failed to end race: " + e.getMessage());
        }
    }

    /**
     * Get current race status
     */
    private APIGatewayProxyResponseEvent handleGetRaceStatus(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> pathParams = input.getPathParameters();
            String raceId = pathParams.get("raceId");

            if (raceId == null) {
                return createErrorResponse(400, "Race ID is required");
            }

            // For simplicity, we'll create a status based on race existence
            // In a real implementation, this would track live race progress
            RaceStatus status = new RaceStatus();
            status.setRaceId(raceId);
            status.setStatus("ACTIVE");
            status.setCurrentLap(1);
            status.setElapsedTime(0.0);
            status.setLastUpdateTime(Instant.now());

            return createSuccessResponse(status);

        } catch (Exception e) {
            logger.error("Error getting race status", e);
            return createErrorResponse(500, "Failed to get race status: " + e.getMessage());
        }
    }

    /**
     * Update player stats after race completion
     */
    private void updatePlayerAfterRace(RaceResult raceResult) {
        try {
            Player player = playerTable.getItem(builder ->
                builder.key(k -> k.partitionValue(raceResult.getPlayerId()))
            );

            if (player != null) {
                // Update player statistics
                player.addExperience(raceResult.getExperience());
                player.addCredits(raceResult.getCredits());

                // Update racing stats
                if (player.getStats() != null) {
                    player.getStats().updateAfterRace(
                        raceResult.getPosition(),
                        raceResult.getLapTime(),
                        raceResult.getRaceDistance(),
                        (long)(raceResult.getTotalRaceTime() * 1000), // Convert to milliseconds
                        raceResult.isCrashed(),
                        "road" // Track type - would be determined from track data
                    );
                }

                // Save updated player
                playerTable.putItem(player);

                logger.info("Updated player stats for: {}", raceResult.getPlayerId());
            }
        } catch (Exception e) {
            logger.error("Error updating player after race", e);
        }
    }

    /**
     * Check if this is a personal best lap time
     */
    private boolean isPersonalBest(RaceResult raceResult) {
        // This would query historical race results for the same player/track combination
        // For now, return false as a placeholder
        return false;
    }

    /**
     * Generate unique race ID
     */
    private String generateRaceId() {
        return "race_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Create success response
     */
    private APIGatewayProxyResponseEvent createSuccessResponse(Object data) {
        try {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*"
                ))
                .withBody(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            logger.error("Error creating success response", e);
            return createErrorResponse(500, "Error serializing response");
        }
    }

    /**
     * Create error response
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
        Map<String, String> errorBody = Map.of("error", message);
        try {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*"
                ))
                .withBody(objectMapper.writeValueAsString(errorBody));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withBody("{\"error\":\"Internal server error\"}");
        }
    }

    // Request/Response DTOs
    public static class RaceStartRequest {
        private String playerId;
        private String trackId;
        private String carId;
        private String gameMode;
        private String weatherConditions;
        private String trackCondition;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public String getGameMode() { return gameMode; }
        public void setGameMode(String gameMode) { this.gameMode = gameMode; }

        public String getWeatherConditions() { return weatherConditions; }
        public void setWeatherConditions(String weatherConditions) { this.weatherConditions = weatherConditions; }

        public String getTrackCondition() { return trackCondition; }
        public void setTrackCondition(String trackCondition) { this.trackCondition = trackCondition; }
    }

    public static class RaceEndRequest {
        private String raceId;
        private String playerId;
        private int position;
        private double bestLapTime;
        private double totalRaceTime;
        private List<Double> lapTimes;
        private double raceDistance;
        private int totalLaps;
        private boolean finished;
        private boolean crashed;
        private Map<String, Object> telemetryData;

        // Getters and setters
        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public double getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(double bestLapTime) { this.bestLapTime = bestLapTime; }

        public double getTotalRaceTime() { return totalRaceTime; }
        public void setTotalRaceTime(double totalRaceTime) { this.totalRaceTime = totalRaceTime; }

        public List<Double> getLapTimes() { return lapTimes; }
        public void setLapTimes(List<Double> lapTimes) { this.lapTimes = lapTimes; }

        public double getRaceDistance() { return raceDistance; }
        public void setRaceDistance(double raceDistance) { this.raceDistance = raceDistance; }

        public int getTotalLaps() { return totalLaps; }
        public void setTotalLaps(int totalLaps) { this.totalLaps = totalLaps; }

        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }

        public boolean isCrashed() { return crashed; }
        public void setCrashed(boolean crashed) { this.crashed = crashed; }

        public Map<String, Object> getTelemetryData() { return telemetryData; }
        public void setTelemetryData(Map<String, Object> telemetryData) { this.telemetryData = telemetryData; }
    }

    public static class RaceSession {
        private String raceId;
        private String playerId;
        private String trackId;
        private String carId;
        private String gameMode;
        private Instant sessionStart;
        private String status;
        private int lapCount;
        private double bestLapTime;
        private double currentLapTime;
        private double totalRaceTime;
        private String weatherConditions;

        // Getters and setters
        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public String getGameMode() { return gameMode; }
        public void setGameMode(String gameMode) { this.gameMode = gameMode; }

        public Instant getSessionStart() { return sessionStart; }
        public void setSessionStart(Instant sessionStart) { this.sessionStart = sessionStart; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getLapCount() { return lapCount; }
        public void setLapCount(int lapCount) { this.lapCount = lapCount; }

        public double getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(double bestLapTime) { this.bestLapTime = bestLapTime; }

        public double getCurrentLapTime() { return currentLapTime; }
        public void setCurrentLapTime(double currentLapTime) { this.currentLapTime = currentLapTime; }

        public double getTotalRaceTime() { return totalRaceTime; }
        public void setTotalRaceTime(double totalRaceTime) { this.totalRaceTime = totalRaceTime; }

        public String getWeatherConditions() { return weatherConditions; }
        public void setWeatherConditions(String weatherConditions) { this.weatherConditions = weatherConditions; }
    }

    public static class RaceSummary {
        private String raceId;
        private int finalPosition;
        private double bestLapTime;
        private double totalRaceTime;
        private long experienceEarned;
        private long creditsEarned;
        private List<Double> lapTimes;
        private boolean personalBest;

        // Getters and setters
        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public int getFinalPosition() { return finalPosition; }
        public void setFinalPosition(int finalPosition) { this.finalPosition = finalPosition; }

        public double getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(double bestLapTime) { this.bestLapTime = bestLapTime; }

        public double getTotalRaceTime() { return totalRaceTime; }
        public void setTotalRaceTime(double totalRaceTime) { this.totalRaceTime = totalRaceTime; }

        public long getExperienceEarned() { return experienceEarned; }
        public void setExperienceEarned(long experienceEarned) { this.experienceEarned = experienceEarned; }

        public long getCreditsEarned() { return creditsEarned; }
        public void setCreditsEarned(long creditsEarned) { this.creditsEarned = creditsEarned; }

        public List<Double> getLapTimes() { return lapTimes; }
        public void setLapTimes(List<Double> lapTimes) { this.lapTimes = lapTimes; }

        public boolean isPersonalBest() { return personalBest; }
        public void setPersonalBest(boolean personalBest) { this.personalBest = personalBest; }
    }

    public static class RaceStatus {
        private String raceId;
        private String status;
        private int currentLap;
        private double elapsedTime;
        private Instant lastUpdateTime;

        // Getters and setters
        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getCurrentLap() { return currentLap; }
        public void setCurrentLap(int currentLap) { this.currentLap = currentLap; }

        public double getElapsedTime() { return elapsedTime; }
        public void setElapsedTime(double elapsedTime) { this.elapsedTime = elapsedTime; }

        public Instant getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Instant lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }
}
