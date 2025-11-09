package com.gt.leaderboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gt.models.RaceResult;
import com.gt.models.Player;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Leaderboard Management Lambda Function
 * Handles ranking systems, records, and competitive features
 */
public class LeaderboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<RaceResult> raceResultsTable;
    private final DynamoDbTable<LeaderboardEntry> leaderboardTable;

    public LeaderboardHandler() {
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

        this.leaderboardTable = enhancedClient.table(
            System.getenv("LEADERBOARD_TABLE"),
            TableSchema.fromBean(LeaderboardEntry.class)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String httpMethod = input.getHttpMethod();
            String path = input.getPath();
            Map<String, String> pathParams = input.getPathParameters();
            Map<String, String> queryParams = input.getQueryStringParameters();

            logger.info("Processing {} request for path: {}", httpMethod, path);

            if ("GET".equals(httpMethod)) {
                if (path.contains("/leaderboard/track/")) {
                    return handleGetTrackLeaderboard(pathParams, queryParams);
                } else if (path.contains("/leaderboard/") && !path.contains("/track/")) {
                    return handleGetCategoryLeaderboard(pathParams, queryParams);
                } else if (path.contains("/player/") && path.contains("/ranking")) {
                    return handleGetPlayerRanking(pathParams);
                } else {
                    return createErrorResponse(400, "Invalid endpoint");
                }
            } else if ("POST".equals(httpMethod)) {
                if (path.contains("/leaderboard/update")) {
                    return handleUpdateLeaderboard(input);
                } else {
                    return createErrorResponse(400, "Invalid endpoint");
                }
            } else {
                return createErrorResponse(405, "Method not allowed");
            }

        } catch (Exception e) {
            logger.error("Error processing request", e);
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get leaderboard for specific track
     */
    private APIGatewayProxyResponseEvent handleGetTrackLeaderboard(Map<String, String> pathParams, Map<String, String> queryParams) {
        try {
            String trackId = pathParams.get("trackId");
            if (trackId == null) {
                return createErrorResponse(400, "Track ID is required");
            }

            int limit = queryParams != null && queryParams.containsKey("limit")
                ? Integer.parseInt(queryParams.get("limit")) : 10;
            String carClass = queryParams != null ? queryParams.get("carClass") : null;

            // Query leaderboard entries for this track
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(trackId).build()
            );

            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit)
                .scanIndexForward(true); // Ascending order for lap times

            List<LeaderboardEntry> entries = leaderboardTable.query(requestBuilder.build())
                .items()
                .stream()
                .collect(Collectors.toList());

            // Filter by car class if specified
            if (carClass != null) {
                entries = entries.stream()
                    .filter(entry -> carClass.equals(entry.getCarClass()))
                    .collect(Collectors.toList());
            }

            // Convert to response format
            List<LeaderboardResponse> response = entries.stream()
                .map(this::convertToLeaderboardResponse)
                .collect(Collectors.toList());

            // Add ranking positions
            for (int i = 0; i < response.size(); i++) {
                response.get(i).setPosition(i + 1);
            }

            TrackLeaderboardResponse trackResponse = new TrackLeaderboardResponse();
            trackResponse.setTrackId(trackId);
            trackResponse.setCarClass(carClass);
            trackResponse.setEntries(response);
            trackResponse.setTotalEntries(response.size());
            trackResponse.setLastUpdated(Instant.now());

            return createSuccessResponse(trackResponse);

        } catch (Exception e) {
            logger.error("Error getting track leaderboard", e);
            return createErrorResponse(500, "Failed to get track leaderboard: " + e.getMessage());
        }
    }

    /**
     * Get leaderboard by category (overall, weekly, monthly)
     */
    private APIGatewayProxyResponseEvent handleGetCategoryLeaderboard(Map<String, String> pathParams, Map<String, String> queryParams) {
        try {
            String category = pathParams.get("category");
            if (category == null) {
                return createErrorResponse(400, "Category is required");
            }

            int limit = queryParams != null && queryParams.containsKey("limit")
                ? Integer.parseInt(queryParams.get("limit")) : 50;

            // Use GSI to query by category
            DynamoDbIndex<LeaderboardEntry> categoryIndex = leaderboardTable.index("CategoryIndex");

            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(category).build()
            );

            List<LeaderboardEntry> entries = categoryIndex.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit)
                .scanIndexForward(true)
                .build())
                .items()
                .stream()
                .collect(Collectors.toList());

            List<LeaderboardResponse> response = entries.stream()
                .map(this::convertToLeaderboardResponse)
                .collect(Collectors.toList());

            // Add ranking positions
            for (int i = 0; i < response.size(); i++) {
                response.get(i).setPosition(i + 1);
            }

            CategoryLeaderboardResponse categoryResponse = new CategoryLeaderboardResponse();
            categoryResponse.setCategory(category);
            categoryResponse.setEntries(response);
            categoryResponse.setTotalEntries(response.size());
            categoryResponse.setLastUpdated(Instant.now());

            return createSuccessResponse(categoryResponse);

        } catch (Exception e) {
            logger.error("Error getting category leaderboard", e);
            return createErrorResponse(500, "Failed to get category leaderboard: " + e.getMessage());
        }
    }

    /**
     * Get player's ranking across different categories
     */
    private APIGatewayProxyResponseEvent handleGetPlayerRanking(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");
            if (playerId == null) {
                return createErrorResponse(400, "Player ID is required");
            }

            // Get player's best times across different tracks
            DynamoDbIndex<RaceResult> playerIndex = raceResultsTable.index("PlayerRaceIndex");

            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(playerId).build()
            );

            List<RaceResult> playerResults = playerIndex.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true)
                .build())
                .items()
                .stream()
                .collect(Collectors.toList());

            // Group by track and get best times
            Map<String, Double> bestTimesByTrack = playerResults.stream()
                .filter(result -> result.getLapTime() > 0)
                .collect(Collectors.groupingBy(
                    RaceResult::getTrackId,
                    Collectors.mapping(RaceResult::getLapTime,
                        Collectors.minBy(Double::compareTo))
                ))
                .entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().get()
                ));

            // Calculate overall statistics
            PlayerRankingResponse response = new PlayerRankingResponse();
            response.setPlayerId(playerId);
            response.setBestTimesByTrack(bestTimesByTrack);
            response.setTotalRaces(playerResults.size());
            response.setWins((int) playerResults.stream().filter(r -> r.getPosition() == 1).count());
            response.setPodiums((int) playerResults.stream().filter(r -> r.getPosition() <= 3).count());

            if (!playerResults.isEmpty()) {
                response.setAveragePosition(playerResults.stream()
                    .mapToInt(RaceResult::getPosition)
                    .average().orElse(0.0));
                response.setBestLapTime(bestTimesByTrack.values().stream()
                    .min(Double::compareTo).orElse(0.0));
            }

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting player ranking", e);
            return createErrorResponse(500, "Failed to get player ranking: " + e.getMessage());
        }
    }

    /**
     * Update leaderboard with new race result
     */
    private APIGatewayProxyResponseEvent handleUpdateLeaderboard(APIGatewayProxyRequestEvent input) {
        try {
            LeaderboardUpdateRequest request = objectMapper.readValue(input.getBody(), LeaderboardUpdateRequest.class);

            if (request.getRaceResult() == null) {
                return createErrorResponse(400, "Race result is required");
            }

            RaceResult raceResult = request.getRaceResult();

            // Create or update leaderboard entry
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setTrackId(raceResult.getTrackId());
            entry.setLapTime(raceResult.getLapTime());
            entry.setPlayerId(raceResult.getPlayerId());
            entry.setPlayerName(request.getPlayerName());
            entry.setCarId(raceResult.getCarId());
            entry.setCarName(request.getCarName());
            entry.setCarClass(request.getCarClass() != null ? request.getCarClass() : "A");
            entry.setGameMode(raceResult.getGameMode());
            entry.setWeatherConditions(raceResult.getWeatherConditions());
            entry.setTrackCondition(raceResult.getTrackCondition());
            entry.setTimestamp(raceResult.getTimestamp());
            entry.setCategory("overall"); // Could be weekly/monthly based on timestamp

            // Check if this is a new record
            boolean isRecord = checkIfRecord(entry);
            entry.setIsRecord(isRecord);

            // Save to leaderboard table
            leaderboardTable.putItem(entry);

            LeaderboardUpdateResponse response = new LeaderboardUpdateResponse();
            response.setSuccess(true);
            response.setNewRecord(isRecord);
            response.setPosition(calculatePosition(entry));
            response.setLapTime(entry.getLapTime());

            logger.info("Updated leaderboard for player {} on track {}",
                entry.getPlayerId(), entry.getTrackId());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error updating leaderboard", e);
            return createErrorResponse(500, "Failed to update leaderboard: " + e.getMessage());
        }
    }

    /**
     * Check if the entry is a new record
     */
    private boolean checkIfRecord(LeaderboardEntry entry) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(entry.getTrackId()).build()
            );

            // Get the current best time for this track
            PageIterable<LeaderboardEntry> results = leaderboardTable.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(1)
                .scanIndexForward(true)
                .build());

            for (LeaderboardEntry existingEntry : results.items()) {
                return entry.getLapTime() < existingEntry.getLapTime();
            }

            // If no existing entries, this is a record
            return true;

        } catch (Exception e) {
            logger.error("Error checking record", e);
            return false;
        }
    }

    /**
     * Calculate position in leaderboard
     */
    private int calculatePosition(LeaderboardEntry entry) {
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(entry.getTrackId()).build()
            );

            // Count entries with better times
            int position = 1;
            PageIterable<LeaderboardEntry> results = leaderboardTable.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(true)
                .build());

            for (LeaderboardEntry existingEntry : results.items()) {
                if (existingEntry.getLapTime() < entry.getLapTime()) {
                    position++;
                } else {
                    break;
                }
            }

            return position;

        } catch (Exception e) {
            logger.error("Error calculating position", e);
            return 0;
        }
    }

    /**
     * Convert LeaderboardEntry to response format
     */
    private LeaderboardResponse convertToLeaderboardResponse(LeaderboardEntry entry) {
        LeaderboardResponse response = new LeaderboardResponse();
        response.setPlayerId(entry.getPlayerId());
        response.setPlayerName(entry.getPlayerName());
        response.setLapTime(entry.getLapTime());
        response.setCarName(entry.getCarName());
        response.setCarClass(entry.getCarClass());
        response.setTimestamp(entry.getTimestamp());
        response.setIsRecord(entry.isIsRecord());
        return response;
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

    // Data Transfer Objects

    public static class LeaderboardEntry {
        private String trackId;
        private double lapTime;
        private String playerId;
        private String playerName;
        private String carId;
        private String carName;
        private String carClass;
        private String gameMode;
        private String weatherConditions;
        private String trackCondition;
        private Instant timestamp;
        private String category;
        private boolean isRecord;

        // Getters and setters
        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public double getLapTime() { return lapTime; }
        public void setLapTime(double lapTime) { this.lapTime = lapTime; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public String getCarName() { return carName; }
        public void setCarName(String carName) { this.carName = carName; }

        public String getCarClass() { return carClass; }
        public void setCarClass(String carClass) { this.carClass = carClass; }

        public String getGameMode() { return gameMode; }
        public void setGameMode(String gameMode) { this.gameMode = gameMode; }

        public String getWeatherConditions() { return weatherConditions; }
        public void setWeatherConditions(String weatherConditions) { this.weatherConditions = weatherConditions; }

        public String getTrackCondition() { return trackCondition; }
        public void setTrackCondition(String trackCondition) { this.trackCondition = trackCondition; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public boolean isIsRecord() { return isRecord; }
        public void setIsRecord(boolean isRecord) { this.isRecord = isRecord; }
    }

    public static class LeaderboardUpdateRequest {
        private RaceResult raceResult;
        private String playerName;
        private String carName;
        private String carClass;

        // Getters and setters
        public RaceResult getRaceResult() { return raceResult; }
        public void setRaceResult(RaceResult raceResult) { this.raceResult = raceResult; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getCarName() { return carName; }
        public void setCarName(String carName) { this.carName = carName; }

        public String getCarClass() { return carClass; }
        public void setCarClass(String carClass) { this.carClass = carClass; }
    }

    public static class LeaderboardUpdateResponse {
        private boolean success;
        private boolean newRecord;
        private int position;
        private double lapTime;

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public boolean isNewRecord() { return newRecord; }
        public void setNewRecord(boolean newRecord) { this.newRecord = newRecord; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public double getLapTime() { return lapTime; }
        public void setLapTime(double lapTime) { this.lapTime = lapTime; }
    }

    public static class LeaderboardResponse {
        private int position;
        private String playerId;
        private String playerName;
        private double lapTime;
        private String carName;
        private String carClass;
        private Instant timestamp;
        private boolean isRecord;

        // Getters and setters
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public double getLapTime() { return lapTime; }
        public void setLapTime(double lapTime) { this.lapTime = lapTime; }

        public String getCarName() { return carName; }
        public void setCarName(String carName) { this.carName = carName; }

        public String getCarClass() { return carClass; }
        public void setCarClass(String carClass) { this.carClass = carClass; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public boolean isRecord() { return isRecord; }
        public void setRecord(boolean record) { isRecord = record; }
    }

    public static class TrackLeaderboardResponse {
        private String trackId;
        private String carClass;
        private List<LeaderboardResponse> entries;
        private int totalEntries;
        private Instant lastUpdated;

        // Getters and setters
        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getCarClass() { return carClass; }
        public void setCarClass(String carClass) { this.carClass = carClass; }

        public List<LeaderboardResponse> getEntries() { return entries; }
        public void setEntries(List<LeaderboardResponse> entries) { this.entries = entries; }

        public int getTotalEntries() { return totalEntries; }
        public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }

        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    public static class CategoryLeaderboardResponse {
        private String category;
        private List<LeaderboardResponse> entries;
        private int totalEntries;
        private Instant lastUpdated;

        // Getters and setters
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public List<LeaderboardResponse> getEntries() { return entries; }
        public void setEntries(List<LeaderboardResponse> entries) { this.entries = entries; }

        public int getTotalEntries() { return totalEntries; }
        public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }

        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    public static class PlayerRankingResponse {
        private String playerId;
        private Map<String, Double> bestTimesByTrack;
        private int totalRaces;
        private int wins;
        private int podiums;
        private double averagePosition;
        private double bestLapTime;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public Map<String, Double> getBestTimesByTrack() { return bestTimesByTrack; }
        public void setBestTimesByTrack(Map<String, Double> bestTimesByTrack) { this.bestTimesByTrack = bestTimesByTrack; }

        public int getTotalRaces() { return totalRaces; }
        public void setTotalRaces(int totalRaces) { this.totalRaces = totalRaces; }

        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }

        public int getPodiums() { return podiums; }
        public void setPodiums(int podiums) { this.podiums = podiums; }

        public double getAveragePosition() { return averagePosition; }
        public void setAveragePosition(double averagePosition) { this.averagePosition = averagePosition; }

        public double getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(double bestLapTime) { this.bestLapTime = bestLapTime; }
    }
}
