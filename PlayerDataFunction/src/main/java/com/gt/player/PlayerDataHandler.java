package com.gt.player;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gt.models.Player;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Player Data Management Lambda Function
 * Handles player profiles, progression, statistics, and account management
 */
public class PlayerDataHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PlayerDataHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Player> playerTable;

    public PlayerDataHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        DynamoDbClient ddbClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();

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
            Map<String, String> pathParams = input.getPathParameters();

            logger.info("Processing {} request for path: {}", httpMethod, path);

            switch (httpMethod) {
                case "GET":
                    if (path.contains("/player/") && path.endsWith("/profile")) {
                        return handleGetPlayer(pathParams);
                    } else if (path.contains("/player/") && path.endsWith("/stats")) {
                        return handleGetPlayerStats(pathParams);
                    } else {
                        return createErrorResponse(400, "Invalid endpoint");
                    }
                case "POST":
                    if (path.contains("/player/create")) {
                        return handleCreatePlayer(input);
                    } else {
                        return createErrorResponse(400, "Invalid endpoint");
                    }
                case "PUT":
                    if (path.contains("/player/") && path.endsWith("/update")) {
                        return handleUpdatePlayer(input);
                    } else {
                        return createErrorResponse(400, "Invalid endpoint");
                    }
                default:
                    return createErrorResponse(405, "Method not allowed");
            }

        } catch (Exception e) {
            logger.error("Error processing request", e);
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Get player profile
     */
    private APIGatewayProxyResponseEvent handleGetPlayer(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");
            if (playerId == null) {
                return createErrorResponse(400, "Player ID is required");
            }

            Player player = playerTable.getItem(Key.builder().partitionValue(playerId).build());
            if (player == null) {
                return createErrorResponse(404, "Player not found");
            }

            return createSuccessResponse(player);

        } catch (Exception e) {
            logger.error("Error getting player", e);
            return createErrorResponse(500, "Failed to get player: " + e.getMessage());
        }
    }

    /**
     * Get player statistics
     */
    private APIGatewayProxyResponseEvent handleGetPlayerStats(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");
            if (playerId == null) {
                return createErrorResponse(400, "Player ID is required");
            }

            Player player = playerTable.getItem(Key.builder().partitionValue(playerId).build());
            if (player == null) {
                return createErrorResponse(404, "Player not found");
            }

            PlayerStatsResponse stats = new PlayerStatsResponse();
            stats.setPlayerId(playerId);
            stats.setPlayerName(player.getUsername());
            stats.setLevel(player.getLevel());
            stats.setExperience(player.getExperience());
            stats.setCredits(player.getCredits());

            // Get stats from PlayerStats object
            if (player.getStats() != null) {
                stats.setTotalRaces(player.getStats().getTotalRaces());
                stats.setWins(player.getStats().getWins());
                stats.setBestLapTime((float) player.getStats().getBestLapTime());
                stats.setTotalDistance((float) player.getStats().getTotalDistance());
                stats.setWinRate(player.getStats().getTotalRaces() > 0 ?
                    (double) player.getStats().getWins() / player.getStats().getTotalRaces() : 0.0);
            } else {
                stats.setTotalRaces(0);
                stats.setWins(0);
                stats.setBestLapTime(0.0f);
                stats.setTotalDistance(0.0f);
                stats.setWinRate(0.0);
            }

            return createSuccessResponse(stats);

        } catch (Exception e) {
            logger.error("Error getting player stats", e);
            return createErrorResponse(500, "Failed to get player stats: " + e.getMessage());
        }
    }

    /**
     * Create new player
     */
    private APIGatewayProxyResponseEvent handleCreatePlayer(APIGatewayProxyRequestEvent input) {
        try {
            CreatePlayerRequest request = objectMapper.readValue(input.getBody(), CreatePlayerRequest.class);

            if (request.getPlayerId() == null || request.getPlayerName() == null) {
                return createErrorResponse(400, "Player ID and name are required");
            }

            // Check if player already exists
            Player existingPlayer = playerTable.getItem(Key.builder().partitionValue(request.getPlayerId()).build());
            if (existingPlayer != null) {
                return createErrorResponse(400, "Player already exists");
            }

            // Create new player
            Player newPlayer = new Player();
            newPlayer.setPlayerId(request.getPlayerId());
            newPlayer.setUsername(request.getPlayerName());
            newPlayer.setEmail(""); // Empty email for now
            newPlayer.setLevel(1);
            newPlayer.setExperience(0);
            newPlayer.setCredits(50000L); // Starting credits
            newPlayer.setCreatedAt(Instant.now());
            newPlayer.setLastLoginAt(Instant.now());

            // Initialize stats (PlayerStats object will be created by default constructor)

            playerTable.putItem(newPlayer);

            PlayerCreatedResponse response = new PlayerCreatedResponse();
            response.setSuccess(true);
            response.setPlayerId(newPlayer.getPlayerId());
            response.setPlayerName(newPlayer.getUsername());
            response.setStartingCredits(newPlayer.getCredits());
            response.setMessage("Player created successfully!");

            logger.info("Created new player: {}", newPlayer.getPlayerId());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error creating player", e);
            return createErrorResponse(500, "Failed to create player: " + e.getMessage());
        }
    }

    /**
     * Update player data
     */
    private APIGatewayProxyResponseEvent handleUpdatePlayer(APIGatewayProxyRequestEvent input) {
        try {
            UpdatePlayerRequest request = objectMapper.readValue(input.getBody(), UpdatePlayerRequest.class);

            if (request.getPlayerId() == null) {
                return createErrorResponse(400, "Player ID is required");
            }

            Player player = playerTable.getItem(Key.builder().partitionValue(request.getPlayerId()).build());
            if (player == null) {
                return createErrorResponse(404, "Player not found");
            }

            // Update fields if provided
            if (request.getPlayerName() != null) {
                player.setUsername(request.getPlayerName());
            }

            if (request.getExperienceGain() != null) {
                player.setExperience(player.getExperience() + request.getExperienceGain());

                // Level up calculation (simple: level = experience / 1000)
                int newLevel = (int) (player.getExperience() / 1000) + 1;
                if (newLevel > player.getLevel()) {
                    player.setLevel(newLevel);
                    player.setCredits(player.getCredits() + (newLevel * 1000)); // Bonus credits for leveling up
                }
            }

            if (request.getCreditsDelta() != null) {
                player.setCredits(Math.max(0, player.getCredits() + request.getCreditsDelta()));
            }

            player.setLastLoginAt(Instant.now());
            playerTable.updateItem(player);

            return createSuccessResponse(Map.of(
                "success", true,
                "message", "Player updated successfully",
                "player", player
            ));

        } catch (Exception e) {
            logger.error("Error updating player", e);
            return createErrorResponse(500, "Failed to update player: " + e.getMessage());
        }
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

    public static class CreatePlayerRequest {
        private String playerId;
        private String playerName;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
    }

    public static class PlayerCreatedResponse {
        private boolean success;
        private String playerId;
        private String playerName;
        private Long startingCredits;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Long getStartingCredits() { return startingCredits; }
        public void setStartingCredits(Long startingCredits) { this.startingCredits = startingCredits; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class UpdatePlayerRequest {
        private String playerId;
        private String playerName;
        private Integer experienceGain;
        private Long creditsDelta;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Integer getExperienceGain() { return experienceGain; }
        public void setExperienceGain(Integer experienceGain) { this.experienceGain = experienceGain; }
        public Long getCreditsDelta() { return creditsDelta; }
        public void setCreditsDelta(Long creditsDelta) { this.creditsDelta = creditsDelta; }
    }

    public static class PlayerStatsResponse {
        private String playerId;
        private String playerName;
        private int level;
        private long experience;
        private long credits;
        private int totalRaces;
        private int wins;
        private float bestLapTime;
        private float totalDistance;
        private double winRate;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public long getExperience() { return experience; }
        public void setExperience(long experience) { this.experience = experience; }
        public long getCredits() { return credits; }
        public void setCredits(long credits) { this.credits = credits; }
        public int getTotalRaces() { return totalRaces; }
        public void setTotalRaces(int totalRaces) { this.totalRaces = totalRaces; }
        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }
        public float getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(float bestLapTime) { this.bestLapTime = bestLapTime; }
        public float getTotalDistance() { return totalDistance; }
        public void setTotalDistance(float totalDistance) { this.totalDistance = totalDistance; }
        public double getWinRate() { return winRate; }
        public void setWinRate(double winRate) { this.winRate = winRate; }
    }
}
