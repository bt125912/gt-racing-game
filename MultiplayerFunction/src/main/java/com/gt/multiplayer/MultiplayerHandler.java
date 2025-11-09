package com.gt.multiplayer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gt.models.RaceResult;
import com.gt.models.Player;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.core.SdkBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Multiplayer Race Management Lambda Function
 * Handles real-time multiplayer racing, lobbies, and race synchronization
 */
public class MultiplayerHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Logger logger = LoggerFactory.getLogger(MultiplayerHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<RaceSession> raceSessionTable;
    private final DynamoDbTable<PlayerConnection> connectionTable;
    private final ApiGatewayManagementApiClient apiGatewayClient;

    // In-memory cache for active sessions (in production, use Redis or DynamoDB)
    private static final Map<String, RaceSession> activeSessions = new ConcurrentHashMap<>();

    public MultiplayerHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        DynamoDbClient ddbClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();

        this.raceSessionTable = enhancedClient.table(
            System.getenv("RACE_SESSIONS_TABLE"),
            TableSchema.fromBean(RaceSession.class)
        );

        this.connectionTable = enhancedClient.table(
            System.getenv("CONNECTIONS_TABLE"),
            TableSchema.fromBean(PlayerConnection.class)
        );

        this.apiGatewayClient = ApiGatewayManagementApiClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {
        try {
            String connectionId = event.getRequestContext().getConnectionId();
            String routeKey = event.getRequestContext().getRouteKey();

            logger.info("Processing WebSocket event - Route: {}, Connection: {}", routeKey, connectionId);

            switch (routeKey) {
                case "$connect":
                    return handleConnect(event);
                case "$disconnect":
                    return handleDisconnect(event);
                case "joinLobby":
                    return handleJoinLobby(event);
                case "createRace":
                    return handleCreateRace(event);
                case "startRace":
                    return handleStartRace(event);
                case "raceUpdate":
                    return handleRaceUpdate(event);
                case "finishRace":
                    return handleFinishRace(event);
                default:
                    logger.warn("Unknown route: {}", routeKey);
                    return createResponse(400, "Unknown route");
            }

        } catch (Exception e) {
            logger.error("Error processing WebSocket request", e);
            return createResponse(500, "Internal server error");
        }
    }

    /**
     * Handle WebSocket connection
     */
    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event) {
        try {
            String connectionId = event.getRequestContext().getConnectionId();
            Map<String, String> queryParams = event.getQueryStringParameters();

            if (queryParams == null || !queryParams.containsKey("playerId")) {
                return createResponse(400, "Player ID is required");
            }

            String playerId = queryParams.get("playerId");
            String playerName = queryParams.get("playerName");

            // Store connection info
            PlayerConnection connection = new PlayerConnection();
            connection.setConnectionId(connectionId);
            connection.setPlayerId(playerId);
            connection.setPlayerName(playerName);
            connection.setConnectedAt(Instant.now());
            connection.setStatus("CONNECTED");

            connectionTable.putItem(connection);

            logger.info("Player {} connected with connection {}", playerId, connectionId);

            return createResponse(200, "Connected successfully");

        } catch (Exception e) {
            logger.error("Error handling connect", e);
            return createResponse(500, "Failed to connect");
        }
    }

    /**
     * Handle WebSocket disconnection
     */
    private APIGatewayV2WebSocketResponse handleDisconnect(APIGatewayV2WebSocketEvent event) {
        try {
            String connectionId = event.getRequestContext().getConnectionId();

            // Find and remove connection
            PlayerConnection connection = connectionTable.getItem(Key.builder()
                .partitionValue(connectionId)
                .build());

            if (connection != null) {
                // Remove from any active race sessions
                removePlayerFromActiveSessions(connection.getPlayerId());

                // Delete connection record
                connectionTable.deleteItem(Key.builder()
                    .partitionValue(connectionId)
                    .build());

                logger.info("Player {} disconnected", connection.getPlayerId());
            }

            return createResponse(200, "Disconnected");

        } catch (Exception e) {
            logger.error("Error handling disconnect", e);
            return createResponse(500, "Failed to disconnect");
        }
    }

    /**
     * Handle joining a racing lobby
     */
    private APIGatewayV2WebSocketResponse handleJoinLobby(APIGatewayV2WebSocketEvent event) {
        try {
            JoinLobbyRequest request = objectMapper.readValue(event.getBody(), JoinLobbyRequest.class);
            String connectionId = event.getRequestContext().getConnectionId();

            // Find or create lobby
            RaceSession session = findOrCreateLobby(request.getTrackId(), request.getRaceMode());

            // Add player to session
            RaceParticipant participant = new RaceParticipant();
            participant.setPlayerId(request.getPlayerId());
            participant.setPlayerName(request.getPlayerName());
            participant.setCarId(request.getCarId());
            participant.setConnectionId(connectionId);
            participant.setStatus("WAITING");
            participant.setJoinedAt(Instant.now());

            session.addParticipant(participant);
            activeSessions.put(session.getSessionId(), session);

            // Notify all players in the lobby
            broadcastToSession(session.getSessionId(), "playerJoined", participant);

            LobbyResponse response = new LobbyResponse();
            response.setSessionId(session.getSessionId());
            response.setStatus(session.getStatus());
            response.setParticipants(session.getParticipants());
            response.setTrackId(session.getTrackId());

            return createResponse(200, objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            logger.error("Error joining lobby", e);
            return createResponse(500, "Failed to join lobby");
        }
    }

    /**
     * Handle creating a new race
     */
    private APIGatewayV2WebSocketResponse handleCreateRace(APIGatewayV2WebSocketEvent event) {
        try {
            CreateRaceRequest request = objectMapper.readValue(event.getBody(), CreateRaceRequest.class);
            String connectionId = event.getRequestContext().getConnectionId();

            RaceSession session = new RaceSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setHostPlayerId(request.getPlayerId());
            session.setTrackId(request.getTrackId());
            session.setRaceMode(request.getRaceMode());
            session.setMaxParticipants(request.getMaxParticipants());
            session.setLapCount(request.getLapCount());
            session.setStatus("LOBBY");
            session.setCreatedAt(Instant.now());
            session.setParticipants(new ArrayList<>());

            // Add host as first participant
            RaceParticipant host = new RaceParticipant();
            host.setPlayerId(request.getPlayerId());
            host.setPlayerName(request.getPlayerName());
            host.setCarId(request.getCarId());
            host.setConnectionId(connectionId);
            host.setStatus("WAITING");
            host.setJoinedAt(Instant.now());

            session.addParticipant(host);
            activeSessions.put(session.getSessionId(), session);

            // Save to database
            raceSessionTable.putItem(session);

            RaceCreatedResponse response = new RaceCreatedResponse();
            response.setSessionId(session.getSessionId());
            response.setStatus("SUCCESS");
            response.setMessage("Race created successfully");

            return createResponse(200, objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            logger.error("Error creating race", e);
            return createResponse(500, "Failed to create race");
        }
    }

    /**
     * Handle starting a race
     */
    private APIGatewayV2WebSocketResponse handleStartRace(APIGatewayV2WebSocketEvent event) {
        try {
            StartRaceRequest request = objectMapper.readValue(event.getBody(), StartRaceRequest.class);

            RaceSession session = activeSessions.get(request.getSessionId());
            if (session == null) {
                return createResponse(404, "Race session not found");
            }

            if (!session.getHostPlayerId().equals(request.getPlayerId())) {
                return createResponse(403, "Only host can start the race");
            }

            // Start the race
            session.setStatus("RACING");
            session.setStartedAt(Instant.now());

            // Set all participants to racing status
            session.getParticipants().forEach(p -> p.setStatus("RACING"));

            // Broadcast race start to all participants
            RaceStartEvent startEvent = new RaceStartEvent();
            startEvent.setSessionId(session.getSessionId());
            startEvent.setStartTime(session.getStartedAt());
            startEvent.setCountdownStart(Instant.now().plusSeconds(3)); // 3-second countdown

            broadcastToSession(session.getSessionId(), "raceStart", startEvent);

            return createResponse(200, "Race started");

        } catch (Exception e) {
            logger.error("Error starting race", e);
            return createResponse(500, "Failed to start race");
        }
    }

    /**
     * Handle real-time race updates (position, lap times, etc.)
     */
    private APIGatewayV2WebSocketResponse handleRaceUpdate(APIGatewayV2WebSocketEvent event) {
        try {
            RaceUpdateEvent update = objectMapper.readValue(event.getBody(), RaceUpdateEvent.class);

            RaceSession session = activeSessions.get(update.getSessionId());
            if (session == null) {
                return createResponse(404, "Race session not found");
            }

            // Update participant data
            RaceParticipant participant = session.getParticipants().stream()
                .filter(p -> p.getPlayerId().equals(update.getPlayerId()))
                .findFirst()
                .orElse(null);

            if (participant != null) {
                participant.setCurrentLap(update.getCurrentLap());
                participant.setPosition(update.getPosition());
                participant.setBestLapTime(update.getBestLapTime());
                participant.setLastLapTime(update.getLastLapTime());
                participant.setTotalRaceTime(update.getTotalRaceTime());
            }

            // Broadcast update to all participants
            broadcastToSession(session.getSessionId(), "raceUpdate", update);

            return createResponse(200, "Update processed");

        } catch (Exception e) {
            logger.error("Error processing race update", e);
            return createResponse(500, "Failed to process update");
        }
    }

    /**
     * Handle race completion
     */
    private APIGatewayV2WebSocketResponse handleFinishRace(APIGatewayV2WebSocketEvent event) {
        try {
            FinishRaceRequest request = objectMapper.readValue(event.getBody(), FinishRaceRequest.class);

            RaceSession session = activeSessions.get(request.getSessionId());
            if (session == null) {
                return createResponse(404, "Race session not found");
            }

            // Mark participant as finished
            RaceParticipant participant = session.getParticipants().stream()
                .filter(p -> p.getPlayerId().equals(request.getPlayerId()))
                .findFirst()
                .orElse(null);

            if (participant != null) {
                participant.setStatus("FINISHED");
                participant.setFinishTime(Instant.now());
                participant.setFinalPosition(request.getPosition());
                participant.setTotalRaceTime(request.getTotalTime());
            }

            // Check if race is complete (all players finished)
            boolean allFinished = session.getParticipants().stream()
                .allMatch(p -> "FINISHED".equals(p.getStatus()));

            if (allFinished) {
                session.setStatus("COMPLETED");
                session.setCompletedAt(Instant.now());

                // Generate final results
                List<RaceParticipant> finalResults = session.getParticipants().stream()
                    .sorted((a, b) -> Integer.compare(a.getFinalPosition(), b.getFinalPosition()))
                    .collect(Collectors.toList());

                RaceResultsEvent resultsEvent = new RaceResultsEvent();
                resultsEvent.setSessionId(session.getSessionId());
                resultsEvent.setResults(finalResults);

                broadcastToSession(session.getSessionId(), "raceResults", resultsEvent);

                // Remove from active sessions
                activeSessions.remove(session.getSessionId());
            }

            return createResponse(200, "Finish processed");

        } catch (Exception e) {
            logger.error("Error processing race finish", e);
            return createResponse(500, "Failed to process finish");
        }
    }

    /**
     * Find or create a lobby for matchmaking
     */
    private RaceSession findOrCreateLobby(String trackId, String raceMode) {
        // Find existing lobby with available slots
        RaceSession existingLobby = activeSessions.values().stream()
            .filter(session -> "LOBBY".equals(session.getStatus()))
            .filter(session -> trackId.equals(session.getTrackId()))
            .filter(session -> raceMode.equals(session.getRaceMode()))
            .filter(session -> session.getParticipants().size() < session.getMaxParticipants())
            .findFirst()
            .orElse(null);

        if (existingLobby != null) {
            return existingLobby;
        }

        // Create new lobby
        RaceSession newSession = new RaceSession();
        newSession.setSessionId(UUID.randomUUID().toString());
        newSession.setTrackId(trackId);
        newSession.setRaceMode(raceMode);
        newSession.setMaxParticipants(8); // Default lobby size
        newSession.setLapCount(3); // Default lap count
        newSession.setStatus("LOBBY");
        newSession.setCreatedAt(Instant.now());
        newSession.setParticipants(new ArrayList<>());

        return newSession;
    }

    /**
     * Broadcast message to all participants in a session
     */
    private void broadcastToSession(String sessionId, String messageType, Object data) {
        try {
            RaceSession session = activeSessions.get(sessionId);
            if (session == null) {
                return;
            }

            WebSocketMessage message = new WebSocketMessage();
            message.setType(messageType);
            message.setData(data);
            message.setTimestamp(Instant.now());

            String messageJson = objectMapper.writeValueAsString(message);

            for (RaceParticipant participant : session.getParticipants()) {
                try {
                    sendToConnection(participant.getConnectionId(), messageJson);
                } catch (Exception e) {
                    logger.error("Failed to send message to connection {}", participant.getConnectionId(), e);
                }
            }

        } catch (Exception e) {
            logger.error("Error broadcasting to session", e);
        }
    }

    /**
     * Send message to specific WebSocket connection
     */
    private void sendToConnection(String connectionId, String message) {
        try {
            PostToConnectionRequest request = PostToConnectionRequest.builder()
                .connectionId(connectionId)
                .data(SdkBytes.fromUtf8String(message))
                .build();

            apiGatewayClient.postToConnection(request);
        } catch (Exception e) {
            logger.error("Failed to send to connection {}", connectionId, e);
        }
    }

    /**
     * Remove player from all active sessions
     */
    private void removePlayerFromActiveSessions(String playerId) {
        activeSessions.values().forEach(session -> {
            session.getParticipants().removeIf(p -> p.getPlayerId().equals(playerId));
        });
    }

    /**
     * Create WebSocket response
     */
    private APIGatewayV2WebSocketResponse createResponse(int statusCode, String body) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        response.setBody(body);
        return response;
    }

    // Data Transfer Objects

    public static class RaceSession {
        private String sessionId;
        private String hostPlayerId;
        private String trackId;
        private String raceMode;
        private int maxParticipants;
        private int lapCount;
        private String status;
        private List<RaceParticipant> participants;
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getHostPlayerId() { return hostPlayerId; }
        public void setHostPlayerId(String hostPlayerId) { this.hostPlayerId = hostPlayerId; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getRaceMode() { return raceMode; }
        public void setRaceMode(String raceMode) { this.raceMode = raceMode; }

        public int getMaxParticipants() { return maxParticipants; }
        public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

        public int getLapCount() { return lapCount; }
        public void setLapCount(int lapCount) { this.lapCount = lapCount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<RaceParticipant> getParticipants() { return participants; }
        public void setParticipants(List<RaceParticipant> participants) { this.participants = participants; }

        public void addParticipant(RaceParticipant participant) {
            if (this.participants == null) {
                this.participants = new ArrayList<>();
            }
            this.participants.add(participant);
        }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        public Instant getStartedAt() { return startedAt; }
        public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    }

    public static class RaceParticipant {
        private String playerId;
        private String playerName;
        private String carId;
        private String connectionId;
        private String status;
        private int currentLap;
        private int position;
        private float bestLapTime;
        private float lastLapTime;
        private float totalRaceTime;
        private int finalPosition;
        private Instant joinedAt;
        private Instant finishTime;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getCurrentLap() { return currentLap; }
        public void setCurrentLap(int currentLap) { this.currentLap = currentLap; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public float getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(float bestLapTime) { this.bestLapTime = bestLapTime; }

        public float getLastLapTime() { return lastLapTime; }
        public void setLastLapTime(float lastLapTime) { this.lastLapTime = lastLapTime; }

        public float getTotalRaceTime() { return totalRaceTime; }
        public void setTotalRaceTime(float totalRaceTime) { this.totalRaceTime = totalRaceTime; }

        public int getFinalPosition() { return finalPosition; }
        public void setFinalPosition(int finalPosition) { this.finalPosition = finalPosition; }

        public Instant getJoinedAt() { return joinedAt; }
        public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

        public Instant getFinishTime() { return finishTime; }
        public void setFinishTime(Instant finishTime) { this.finishTime = finishTime; }
    }

    public static class PlayerConnection {
        private String connectionId;
        private String playerId;
        private String playerName;
        private String status;
        private Instant connectedAt;

        // Getters and setters
        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Instant getConnectedAt() { return connectedAt; }
        public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    }

    // Request/Response DTOs
    public static class JoinLobbyRequest {
        private String playerId;
        private String playerName;
        private String carId;
        private String trackId;
        private String raceMode;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }
        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }
        public String getRaceMode() { return raceMode; }
        public void setRaceMode(String raceMode) { this.raceMode = raceMode; }
    }

    public static class CreateRaceRequest {
        private String playerId;
        private String playerName;
        private String carId;
        private String trackId;
        private String raceMode;
        private int maxParticipants;
        private int lapCount;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }
        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }
        public String getRaceMode() { return raceMode; }
        public void setRaceMode(String raceMode) { this.raceMode = raceMode; }
        public int getMaxParticipants() { return maxParticipants; }
        public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
        public int getLapCount() { return lapCount; }
        public void setLapCount(int lapCount) { this.lapCount = lapCount; }
    }

    public static class StartRaceRequest {
        private String sessionId;
        private String playerId;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
    }

    public static class FinishRaceRequest {
        private String sessionId;
        private String playerId;
        private int position;
        private float totalTime;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
        public float getTotalTime() { return totalTime; }
        public void setTotalTime(float totalTime) { this.totalTime = totalTime; }
    }

    public static class LobbyResponse {
        private String sessionId;
        private String status;
        private String trackId;
        private List<RaceParticipant> participants;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }
        public List<RaceParticipant> getParticipants() { return participants; }
        public void setParticipants(List<RaceParticipant> participants) { this.participants = participants; }
    }

    public static class RaceCreatedResponse {
        private String sessionId;
        private String status;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class RaceUpdateEvent {
        private String sessionId;
        private String playerId;
        private int currentLap;
        private int position;
        private float bestLapTime;
        private float lastLapTime;
        private float totalRaceTime;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public int getCurrentLap() { return currentLap; }
        public void setCurrentLap(int currentLap) { this.currentLap = currentLap; }
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
        public float getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(float bestLapTime) { this.bestLapTime = bestLapTime; }
        public float getLastLapTime() { return lastLapTime; }
        public void setLastLapTime(float lastLapTime) { this.lastLapTime = lastLapTime; }
        public float getTotalRaceTime() { return totalRaceTime; }
        public void setTotalRaceTime(float totalRaceTime) { this.totalRaceTime = totalRaceTime; }
    }

    public static class RaceStartEvent {
        private String sessionId;
        private Instant startTime;
        private Instant countdownStart;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getCountdownStart() { return countdownStart; }
        public void setCountdownStart(Instant countdownStart) { this.countdownStart = countdownStart; }
    }

    public static class RaceResultsEvent {
        private String sessionId;
        private List<RaceParticipant> results;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public List<RaceParticipant> getResults() { return results; }
        public void setResults(List<RaceParticipant> results) { this.results = results; }
    }

    public static class WebSocketMessage {
        private String type;
        private Object data;
        private Instant timestamp;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }
}
