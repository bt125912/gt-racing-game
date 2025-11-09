
        // Getters and setters
        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public float getBestLapTime() { return bestLapTime; }
        public void setBestLapTime(float bestLapTime) { this.bestLapTime = bestLapTime; }

        public float getTotalRaceTime() { return totalRaceTime; }
        public void setTotalRaceTime(float totalRaceTime) { this.totalRaceTime = totalRaceTime; }

        public int getLapsCompleted() { return lapsCompleted; }
        public void setLapsCompleted(int lapsCompleted) { this.lapsCompleted = lapsCompleted; }
    }
}
package com.gt.multiplayer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time Multiplayer WebSocket Handler
 * Manages live racing sessions, position updates, and race synchronization
 */
public class MultiplayerHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Logger logger = LoggerFactory.getLogger(MultiplayerHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbClient dynamoDbClient;
    private ApiGatewayManagementApiClient apiGatewayClient;

    // In-memory session management (would use ElastiCache in production)
    private static final Map<String, RaceSession> activeSessions = new ConcurrentHashMap<>();
    private static final Map<String, String> connectionToPlayer = new ConcurrentHashMap<>();

    public MultiplayerHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build();
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {
        try {
            String routeKey = event.getRequestContext().getRouteKey();
            String connectionId = event.getRequestContext().getConnectionId();
            String domainName = event.getRequestContext().getDomainName();
            String stage = event.getRequestContext().getStage();

            // Initialize API Gateway Management client for this request
            String endpoint = String.format("https://%s/%s", domainName, stage);
            this.apiGatewayClient = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .build();

            logger.info("WebSocket event: route={}, connectionId={}", routeKey, connectionId);

            return switch (routeKey) {
                case "$connect" -> handleConnect(event, connectionId);
                case "$disconnect" -> handleDisconnect(event, connectionId);
                case "joinRace" -> handleJoinRace(event, connectionId);
                case "updatePosition" -> handlePositionUpdate(event, connectionId);
                case "finishLap" -> handleLapFinish(event, connectionId);
                case "leaveRace" -> handleLeaveRace(event, connectionId);
                case "chatMessage" -> handleChatMessage(event, connectionId);
                default -> createResponse(400, "Unknown route: " + routeKey);
            };

        } catch (Exception e) {
            logger.error("Error processing WebSocket event", e);
            return createResponse(500, "Internal server error");
        }
    }

    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event, String connectionId) {
        logger.info("New WebSocket connection: {}", connectionId);

        // Store connection in DynamoDB for persistence
        storeConnection(connectionId);

        // Send welcome message
        sendToConnection(connectionId, new WebSocketMessage("connected",
            Map.of("connectionId", connectionId, "timestamp", Instant.now())));

        return createResponse(200, "Connected");
    }

    private APIGatewayV2WebSocketResponse handleDisconnect(APIGatewayV2WebSocketEvent event, String connectionId) {
        logger.info("WebSocket disconnection: {}", connectionId);

        // Remove player from any active race sessions
        String playerId = connectionToPlayer.get(connectionId);
        if (playerId != null) {
            removePlayerFromActiveSessions(playerId, connectionId);
        }

        // Clean up connection data
        removeConnection(connectionId);
        connectionToPlayer.remove(connectionId);

        return createResponse(200, "Disconnected");
    }

    private APIGatewayV2WebSocketResponse handleJoinRace(APIGatewayV2WebSocketEvent event, String connectionId) {
        try {
            JoinRaceRequest request = objectMapper.readValue(event.getBody(), JoinRaceRequest.class);

            if (request.getPlayerId() == null || request.getRaceId() == null) {
                sendError(connectionId, "Missing required fields: playerId, raceId");
                return createResponse(400, "Invalid request");
            }

            // Associate connection with player
            connectionToPlayer.put(connectionId, request.getPlayerId());

            // Get or create race session
            RaceSession session = getOrCreateRaceSession(request.getRaceId(), request);

            // Add player to session
            RaceParticipant participant = new RaceParticipant();
            participant.setPlayerId(request.getPlayerId());
            participant.setPlayerName(request.getPlayerName());
            participant.setCarId(request.getCarId());
            participant.setConnectionId(connectionId);
            participant.setJoinTime(Instant.now());
            participant.setCurrentPosition(new PlayerPosition());

            session.addParticipant(participant);

            // Notify all participants about new player
            broadcastToSession(request.getRaceId(), new WebSocketMessage("playerJoined", participant));

            // Send session info to new player
            SessionInfo sessionInfo = new SessionInfo();
            sessionInfo.setRaceId(request.getRaceId());
            sessionInfo.setTrackId(session.getTrackId());
            sessionInfo.setParticipantCount(session.getParticipants().size());
            sessionInfo.setParticipants(session.getParticipants());
            sessionInfo.setSessionStatus(session.getStatus());

            sendToConnection(connectionId, new WebSocketMessage("sessionJoined", sessionInfo));

            logger.info("Player {} joined race session {}", request.getPlayerId(), request.getRaceId());
            return createResponse(200, "Joined race");

        } catch (Exception e) {
            logger.error("Error joining race", e);
            sendError(connectionId, "Failed to join race");
            return createResponse(500, "Internal error");
        }
    }

    private APIGatewayV2WebSocketResponse handlePositionUpdate(APIGatewayV2WebSocketEvent event, String connectionId) {
        try {
            PositionUpdateRequest request = objectMapper.readValue(event.getBody(), PositionUpdateRequest.class);

            String playerId = connectionToPlayer.get(connectionId);
            if (playerId == null) {
                sendError(connectionId, "Player not identified");
                return createResponse(400, "Player not identified");
            }

            // Find race session for this player
            RaceSession session = findPlayerSession(playerId);
            if (session == null) {
                sendError(connectionId, "Not in any race session");
                return createResponse(400, "Not in race");
            }

            // Update player position
            RaceParticipant participant = session.getParticipant(playerId);
            if (participant != null) {
                participant.setCurrentPosition(request.getPosition());
                participant.setLastUpdate(Instant.now());

                // Broadcast position update to other players in race
                PositionBroadcast broadcast = new PositionBroadcast();
                broadcast.setPlayerId(playerId);
                broadcast.setPosition(request.getPosition());
                broadcast.setTimestamp(Instant.now());

                broadcastToSessionExcept(session.getRaceId(), connectionId,
                    new WebSocketMessage("positionUpdate", broadcast));
            }

            return createResponse(200, "Position updated");

        } catch (Exception e) {
            logger.error("Error updating position", e);
            return createResponse(500, "Internal error");
        }
    }

    private APIGatewayV2WebSocketResponse handleLapFinish(APIGatewayV2WebSocketEvent event, String connectionId) {
        try {
            LapFinishRequest request = objectMapper.readValue(event.getBody(), LapFinishRequest.class);

            String playerId = connectionToPlayer.get(connectionId);
            RaceSession session = findPlayerSession(playerId);

            if (session != null) {
                RaceParticipant participant = session.getParticipant(playerId);
                if (participant != null) {
                    participant.addLapTime(request.getLapTime());
                    participant.setCurrentLap(request.getLapNumber());

                    // Broadcast lap completion
                    LapCompleteBroadcast broadcast = new LapCompleteBroadcast();
                    broadcast.setPlayerId(playerId);
                    broadcast.setPlayerName(participant.getPlayerName());
                    broadcast.setLapNumber(request.getLapNumber());
                    broadcast.setLapTime(request.getLapTime());
                    broadcast.setBestLap(participant.getBestLapTime());
                    broadcast.setPosition(calculateRacePosition(session, participant));

                    broadcastToSession(session.getRaceId(),
                        new WebSocketMessage("lapCompleted", broadcast));

                    // Check for race completion
                    if (request.getLapNumber() >= session.getTotalLaps()) {
                        handleRaceCompletion(session, participant);
                    }
                }
            }

            return createResponse(200, "Lap recorded");

        } catch (Exception e) {
            logger.error("Error recording lap", e);
            return createResponse(500, "Internal error");
        }
    }

    private APIGatewayV2WebSocketResponse handleLeaveRace(APIGatewayV2WebSocketEvent event, String connectionId) {
        String playerId = connectionToPlayer.get(connectionId);
        if (playerId != null) {
            removePlayerFromActiveSessions(playerId, connectionId);
        }

        return createResponse(200, "Left race");
    }

    private APIGatewayV2WebSocketResponse handleChatMessage(APIGatewayV2WebSocketEvent event, String connectionId) {
        try {
            ChatMessageRequest request = objectMapper.readValue(event.getBody(), ChatMessageRequest.class);

            String playerId = connectionToPlayer.get(connectionId);
            RaceSession session = findPlayerSession(playerId);

            if (session != null) {
                RaceParticipant participant = session.getParticipant(playerId);
                if (participant != null) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setPlayerId(playerId);
                    chatMessage.setPlayerName(participant.getPlayerName());
                    chatMessage.setMessage(request.getMessage());
                    chatMessage.setTimestamp(Instant.now());

                    broadcastToSession(session.getRaceId(),
                        new WebSocketMessage("chatMessage", chatMessage));
                }
            }

            return createResponse(200, "Message sent");

        } catch (Exception e) {
            logger.error("Error sending chat message", e);
            return createResponse(500, "Internal error");
        }
    }

    // Helper methods

    private RaceSession getOrCreateRaceSession(String raceId, JoinRaceRequest request) {
        return activeSessions.computeIfAbsent(raceId, id -> {
            RaceSession session = new RaceSession();
            session.setRaceId(raceId);
            session.setTrackId(request.getTrackId());
            session.setCreatedAt(Instant.now());
            session.setStatus("WAITING");
            session.setMaxParticipants(16); // Default max players
            session.setTotalLaps(request.getTotalLaps() != null ? request.getTotalLaps() : 5);
            session.setParticipants(new ArrayList<>());
            return session;
        });
    }

    private void removePlayerFromActiveSessions(String playerId, String connectionId) {
        for (RaceSession session : activeSessions.values()) {
            RaceParticipant participant = session.removeParticipant(playerId);
            if (participant != null) {
                // Notify other players
                broadcastToSessionExcept(session.getRaceId(), connectionId,
                    new WebSocketMessage("playerLeft",
                        Map.of("playerId", playerId, "playerName", participant.getPlayerName())));

                // Remove empty sessions
                if (session.getParticipants().isEmpty()) {
                    activeSessions.remove(session.getRaceId());
                }
                break;
            }
        }
    }

    private RaceSession findPlayerSession(String playerId) {
        for (RaceSession session : activeSessions.values()) {
            if (session.getParticipant(playerId) != null) {
                return session;
            }
        }
        return null;
    }

    private int calculateRacePosition(RaceSession session, RaceParticipant participant) {
        List<RaceParticipant> participants = new ArrayList<>(session.getParticipants());

        // Sort by lap number (descending) then by best lap time (ascending)
        participants.sort((a, b) -> {
            int lapComparison = Integer.compare(b.getCurrentLap(), a.getCurrentLap());
            if (lapComparison != 0) return lapComparison;

            float aTime = a.getBestLapTime();
            float bTime = b.getBestLapTime();
            if (aTime <= 0) aTime = Float.MAX_VALUE;
            if (bTime <= 0) bTime = Float.MAX_VALUE;

            return Float.compare(aTime, bTime);
        });

        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getPlayerId().equals(participant.getPlayerId())) {
                return i + 1;
            }
        }

        return participants.size();
    }

    private void handleRaceCompletion(RaceSession session, RaceParticipant participant) {
        participant.setFinished(true);
        participant.setFinishTime(Instant.now());

        // Check if all players finished
        boolean allFinished = session.getParticipants().stream()
            .allMatch(RaceParticipant::isFinished);

        if (allFinished) {
            session.setStatus("COMPLETED");

            // Generate final results
            List<RaceResult> results = generateRaceResults(session);

            broadcastToSession(session.getRaceId(),
                new WebSocketMessage("raceCompleted",
                    Map.of("results", results, "sessionId", session.getRaceId())));

            // Clean up session after a delay
            // In production, you'd schedule this cleanup
        }
    }

    private List<RaceResult> generateRaceResults(RaceSession session) {
        List<RaceParticipant> participants = new ArrayList<>(session.getParticipants());
        participants.sort((a, b) -> {
            if (a.getFinishTime() == null) return 1;
            if (b.getFinishTime() == null) return -1;
            return a.getFinishTime().compareTo(b.getFinishTime());
        });

        List<RaceResult> results = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            RaceParticipant p = participants.get(i);
            RaceResult result = new RaceResult();
            result.setPosition(i + 1);
            result.setPlayerId(p.getPlayerId());
            result.setPlayerName(p.getPlayerName());
            result.setBestLapTime(p.getBestLapTime());
            result.setTotalRaceTime(p.getTotalRaceTime());
            result.setLapsCompleted(p.getCurrentLap());
            results.add(result);
        }

        return results;
    }

    private void broadcastToSession(String raceId, WebSocketMessage message) {
        RaceSession session = activeSessions.get(raceId);
        if (session != null) {
            for (RaceParticipant participant : session.getParticipants()) {
                sendToConnection(participant.getConnectionId(), message);
            }
        }
    }

    private void broadcastToSessionExcept(String raceId, String excludeConnectionId, WebSocketMessage message) {
        RaceSession session = activeSessions.get(raceId);
        if (session != null) {
            for (RaceParticipant participant : session.getParticipants()) {
                if (!participant.getConnectionId().equals(excludeConnectionId)) {
                    sendToConnection(participant.getConnectionId(), message);
                }
            }
        }
    }

    private void sendToConnection(String connectionId, WebSocketMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            PostToConnectionRequest request = PostToConnectionRequest.builder()
                .connectionId(connectionId)
                .data(SdkBytes.fromUtf8String(messageJson))
                .build();

            apiGatewayClient.postToConnection(request);
        } catch (Exception e) {
            logger.error("Failed to send message to connection {}: {}", connectionId, e.getMessage());
            // Connection might be stale, could clean it up here
        }
    }

    private void sendError(String connectionId, String error) {
        sendToConnection(connectionId, new WebSocketMessage("error", Map.of("message", error)));
    }

    private void storeConnection(String connectionId) {
        // Store connection info in DynamoDB for persistence
        Map<String, AttributeValue> item = Map.of(
            "connectionId", AttributeValue.builder().s(connectionId).build(),
            "connectedAt", AttributeValue.builder().s(Instant.now().toString()).build(),
            "ttl", AttributeValue.builder().n(String.valueOf(Instant.now().getEpochSecond() + 3600)).build()
        );

        PutItemRequest request = PutItemRequest.builder()
            .tableName("GT-WebSocket-Connections")
            .item(item)
            .build();

        try {
            dynamoDbClient.putItem(request);
        } catch (Exception e) {
            logger.error("Failed to store connection", e);
        }
    }

    private void removeConnection(String connectionId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
            .tableName("GT-WebSocket-Connections")
            .key(Map.of("connectionId", AttributeValue.builder().s(connectionId).build()))
            .build();

        try {
            dynamoDbClient.deleteItem(request);
        } catch (Exception e) {
            logger.error("Failed to remove connection", e);
        }
    }

    private APIGatewayV2WebSocketResponse createResponse(int statusCode, String body) {
        return APIGatewayV2WebSocketResponse.builder()
            .withStatusCode(statusCode)
            .withBody(body)
            .build();
    }

    // Data classes for WebSocket communication

    public static class WebSocketMessage {
        private String type;
        private Object data;
        private Instant timestamp;

        public WebSocketMessage(String type, Object data) {
            this.type = type;
            this.data = data;
            this.timestamp = Instant.now();
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class JoinRaceRequest {
        private String playerId;
        private String playerName;
        private String raceId;
        private String trackId;
        private String carId;
        private Integer totalLaps;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public Integer getTotalLaps() { return totalLaps; }
        public void setTotalLaps(Integer totalLaps) { this.totalLaps = totalLaps; }
    }

    public static class PositionUpdateRequest {
        private PlayerPosition position;

        public PlayerPosition getPosition() { return position; }
        public void setPosition(PlayerPosition position) { this.position = position; }
    }

    public static class PlayerPosition {
        private float x, y, z; // World coordinates
        private float rotationY; // Heading
        private float speed; // km/h
        private float lapProgress; // 0.0 to 1.0

        // Getters and setters
        public float getX() { return x; }
        public void setX(float x) { this.x = x; }

        public float getY() { return y; }
        public void setY(float y) { this.y = y; }

        public float getZ() { return z; }
        public void setZ(float z) { this.z = z; }

        public float getRotationY() { return rotationY; }
        public void setRotationY(float rotationY) { this.rotationY = rotationY; }

        public float getSpeed() { return speed; }
        public void setSpeed(float speed) { this.speed = speed; }

        public float getLapProgress() { return lapProgress; }
        public void setLapProgress(float lapProgress) { this.lapProgress = lapProgress; }
    }

    // Additional data classes would continue here...
    // (RaceSession, RaceParticipant, etc. - shortened for brevity)

    public static class RaceSession {
        private String raceId;
        private String trackId;
        private String status;
        private int maxParticipants;
        private int totalLaps;
        private Instant createdAt;
        private List<RaceParticipant> participants;

        public void addParticipant(RaceParticipant participant) {
            participants.add(participant);
        }

        public RaceParticipant removeParticipant(String playerId) {
            return participants.removeIf(p -> p.getPlayerId().equals(playerId)) ?
                participants.stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst().orElse(null) : null;
        }

        public RaceParticipant getParticipant(String playerId) {
            return participants.stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst().orElse(null);
        }

        // Getters and setters
        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getMaxParticipants() { return maxParticipants; }
        public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

        public int getTotalLaps() { return totalLaps; }
        public void setTotalLaps(int totalLaps) { this.totalLaps = totalLaps; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        public List<RaceParticipant> getParticipants() { return participants; }
        public void setParticipants(List<RaceParticipant> participants) { this.participants = participants; }
    }

    public static class RaceParticipant {
        private String playerId;
        private String playerName;
        private String carId;
        private String connectionId;
        private Instant joinTime;
        private PlayerPosition currentPosition;
        private int currentLap;
        private List<Float> lapTimes = new ArrayList<>();
        private boolean finished;
        private Instant finishTime;
        private Instant lastUpdate;

        public void addLapTime(float lapTime) {
            lapTimes.add(lapTime);
        }

        public float getBestLapTime() {
            return lapTimes.stream().min(Float::compare).orElse(0.0f);
        }

        public float getTotalRaceTime() {
            return lapTimes.stream().reduce(0.0f, Float::sum);
        }

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

        public Instant getJoinTime() { return joinTime; }
        public void setJoinTime(Instant joinTime) { this.joinTime = joinTime; }

        public PlayerPosition getCurrentPosition() { return currentPosition; }
        public void setCurrentPosition(PlayerPosition currentPosition) { this.currentPosition = currentPosition; }

        public int getCurrentLap() { return currentLap; }
        public void setCurrentLap(int currentLap) { this.currentLap = currentLap; }

        public List<Float> getLapTimes() { return lapTimes; }
        public void setLapTimes(List<Float> lapTimes) { this.lapTimes = lapTimes; }

        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }

        public Instant getFinishTime() { return finishTime; }
        public void setFinishTime(Instant finishTime) { this.finishTime = finishTime; }

        public Instant getLastUpdate() { return lastUpdate; }
        public void setLastUpdate(Instant lastUpdate) { this.lastUpdate = lastUpdate; }
    }

    // Additional supporting classes (shortened for brevity)
    public static class LapFinishRequest {
        private int lapNumber;
        private float lapTime;

        public int getLapNumber() { return lapNumber; }
        public void setLapNumber(int lapNumber) { this.lapNumber = lapNumber; }

        public float getLapTime() { return lapTime; }
        public void setLapTime(float lapTime) { this.lapTime = lapTime; }
    }

    public static class ChatMessageRequest {
        private String message;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class PositionBroadcast {
        private String playerId;
        private PlayerPosition position;
        private Instant timestamp;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public PlayerPosition getPosition() { return position; }
        public void setPosition(PlayerPosition position) { this.position = position; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class LapCompleteBroadcast {
        private String playerId;
        private String playerName;
        private int lapNumber;
        private float lapTime;
        private float bestLap;
        private int position;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public int getLapNumber() { return lapNumber; }
        public void setLapNumber(int lapNumber) { this.lapNumber = lapNumber; }

        public float getLapTime() { return lapTime; }
        public void setLapTime(float lapTime) { this.lapTime = lapTime; }

        public float getBestLap() { return bestLap; }
        public void setBestLap(float bestLap) { this.bestLap = bestLap; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
    }

    public static class SessionInfo {
        private String raceId;
        private String trackId;
        private int participantCount;
        private List<RaceParticipant> participants;
        private String sessionStatus;

        // Getters and setters
        public String getRaceId() { return raceId; }
        public void setRaceId(String raceId) { this.raceId = raceId; }

        public String getTrackId() { return trackId; }
        public void setTrackId(String trackId) { this.trackId = trackId; }

        public int getParticipantCount() { return participantCount; }
        public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }

        public List<RaceParticipant> getParticipants() { return participants; }
        public void setParticipants(List<RaceParticipant> participants) { this.participants = participants; }

        public String getSessionStatus() { return sessionStatus; }
        public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }
    }

    public static class ChatMessage {
        private String playerId;
        private String playerName;
        private String message;
        private Instant timestamp;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class RaceResult {
        private int position;
        private String playerId;
        private String playerName;
        private float bestLapTime;
        private float totalRaceTime;
        private int lapsCompleted;
