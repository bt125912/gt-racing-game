package com.gt.garage;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gt.models.CarConfiguration;
import com.gt.models.Player;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Garage Management Lambda Function
 * Handles car collection, purchasing, tuning, and selling operations
 */
public class GarageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GarageHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<CarConfiguration> carTable;
    private final DynamoDbTable<Player> playerTable;

    public GarageHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        DynamoDbClient ddbClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();

        this.carTable = enhancedClient.table(
            System.getenv("GARAGE_DATA_TABLE"),
            TableSchema.fromBean(CarConfiguration.class)
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
            Map<String, String> pathParams = input.getPathParameters();

            logger.info("Processing {} request for path: {}", httpMethod, path);

            switch (httpMethod) {
                case "GET":
                    if (path.contains("/garage/") && path.endsWith("/cars")) {
                        return handleGetPlayerCars(pathParams);
                    } else {
                        return createErrorResponse(400, "Invalid endpoint");
                    }
                case "POST":
                    if (path.contains("/garage/") && path.endsWith("/purchase")) {
                        return handlePurchaseCar(input);
                    } else if (path.contains("/garage/") && path.endsWith("/tune")) {
                        return handleTuneCar(input);
                    } else {
                        return createErrorResponse(400, "Invalid endpoint");
                    }
                case "DELETE":
                    if (path.contains("/garage/") && path.contains("/car/")) {
                        return handleSellCar(pathParams);
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
     * Get player's car collection
     */
    private APIGatewayProxyResponseEvent handleGetPlayerCars(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");
            if (playerId == null) {
                return createErrorResponse(400, "Player ID is required");
            }

            // Query for all cars owned by this player
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                    .partitionValue(playerId)
                    .build()))
                .build();

            List<CarConfiguration> playerCars = new ArrayList<>();
            carTable.query(queryRequest)
                .items()
                .forEach(playerCars::add);

            GarageResponse response = new GarageResponse();
            response.setSuccess(true);
            response.setPlayerId(playerId);
            response.setCars(playerCars);
            response.setTotalCars(playerCars.size());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting player cars", e);
            return createErrorResponse(500, "Failed to get player cars: " + e.getMessage());
        }
    }

    /**
     * Purchase a new car
     */
    private APIGatewayProxyResponseEvent handlePurchaseCar(APIGatewayProxyRequestEvent input) {
        try {
            PurchaseCarRequest request = objectMapper.readValue(input.getBody(), PurchaseCarRequest.class);

            if (request.getPlayerId() == null || request.getCarId() == null) {
                return createErrorResponse(400, "Player ID and Car ID are required");
            }

            // Get player to check credits
            Player player = playerTable.getItem(Key.builder().partitionValue(request.getPlayerId()).build());
            if (player == null) {
                return createErrorResponse(404, "Player not found");
            }

            // Get car price (this would typically be from a car catalog)
            long carPrice = getCarPrice(request.getCarId());
            if (player.getCredits() < carPrice) {
                return createErrorResponse(400, "Insufficient credits");
            }

            // Create new car configuration
            String ownedCarId = UUID.randomUUID().toString();
            CarConfiguration newCar = new CarConfiguration();
            newCar.setOwnerId(request.getPlayerId());
            newCar.setCarId(ownedCarId);
            newCar.setBaseName(request.getCarId()); // This would be the car model name
            newCar.setCreatedAt(Instant.now());
            newCar.setLastUsed(Instant.now());

            // Set default tuning
            initializeDefaultTuning(newCar);

            // Save car and update player credits
            carTable.putItem(newCar);
            player.spendCredits(carPrice);
            playerTable.updateItem(player);

            PurchaseCarResponse response = new PurchaseCarResponse();
            response.setSuccess(true);
            response.setCarId(ownedCarId);
            response.setPricePaid(carPrice);
            response.setRemainingCredits(player.getCredits());
            response.setMessage("Car purchased successfully!");

            logger.info("Player {} purchased car {} for {} credits", request.getPlayerId(), request.getCarId(), carPrice);

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error purchasing car", e);
            return createErrorResponse(500, "Failed to purchase car: " + e.getMessage());
        }
    }

    /**
     * Tune/modify a car
     */
    private APIGatewayProxyResponseEvent handleTuneCar(APIGatewayProxyRequestEvent input) {
        try {
            TuneCarRequest request = objectMapper.readValue(input.getBody(), TuneCarRequest.class);

            if (request.getOwnedCarId() == null) {
                return createErrorResponse(400, "Owned car ID is required");
            }

            // Get car configuration
            CarConfiguration car = carTable.getItem(Key.builder()
                .partitionValue(request.getPlayerId())
                .sortValue(request.getOwnedCarId())
                .build());

            if (car == null) {
                return createErrorResponse(404, "Car not found");
            }

            // Apply tuning modifications
            if (request.getTuningData() != null) {
                applyTuningModifications(car, request.getTuningData());
                car.setLastUsed(Instant.now());
                carTable.updateItem(car);
            }

            TuneCarResponse response = new TuneCarResponse();
            response.setSuccess(true);
            response.setMessage("Car tuned successfully");
            response.setCar(car);

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error tuning car", e);
            return createErrorResponse(500, "Failed to tune car: " + e.getMessage());
        }
    }

    /**
     * Sell a car
     */
    private APIGatewayProxyResponseEvent handleSellCar(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");
            String carId = pathParams.get("carId");

            if (playerId == null || carId == null) {
                return createErrorResponse(400, "Player ID and Car ID are required");
            }

            // Get car and player
            CarConfiguration car = carTable.getItem(Key.builder()
                .partitionValue(playerId)
                .sortValue(carId)
                .build());

            if (car == null) {
                return createErrorResponse(404, "Car not found");
            }

            Player player = playerTable.getItem(Key.builder().partitionValue(playerId).build());
            if (player == null) {
                return createErrorResponse(404, "Player not found");
            }

            // Calculate sell price (50% of original)
            long originalPrice = getCarPrice(car.getBaseName());
            long sellPrice = originalPrice / 2;

            // Delete car and add credits to player
            carTable.deleteItem(car);
            player.addCredits(sellPrice);
            playerTable.updateItem(player);

            SellCarResponse response = new SellCarResponse();
            response.setSuccess(true);
            response.setCreditsEarned(sellPrice);
            response.setRemainingCredits(player.getCredits());
            response.setMessage("Car sold successfully");

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error selling car", e);
            return createErrorResponse(500, "Failed to sell car: " + e.getMessage());
        }
    }

    /**
     * Get car price from catalog (simplified)
     */
    private long getCarPrice(String carId) {
        // This is a simplified pricing system
        Map<String, Long> carPrices = Map.of(
            "GT_R34", 75000L,
            "SUPRA_MK4", 85000L,
            "NSX_TYPE_R", 120000L,
            "911_GT3", 150000L,
            "F40", 300000L
        );
        return carPrices.getOrDefault(carId, 50000L);
    }

    /**
     * Initialize default tuning settings
     */
    private void initializeDefaultTuning(CarConfiguration car) {
        // Set default values - these would be realistic defaults for the car
        // This is a simplified version using only available fields
        car.setMass(1500.0f); // kg
        // Note: Other tuning parameters would be set via Engine, Transmission, etc. objects
        // For now, just setting basic mass which is available directly
    }

    /**
     * Apply tuning modifications
     */
    private void applyTuningModifications(CarConfiguration car, Map<String, Object> tuningData) {
        // Apply each modification with validation
        tuningData.forEach((key, value) -> {
            switch (key) {
                case "mass":
                    if (value instanceof Number) {
                        car.setMass(((Number) value).floatValue());
                    }
                    break;
                case "customName":
                    if (value instanceof String) {
                        car.setCustomName((String) value);
                    }
                    break;
                case "bodyColor":
                    if (value instanceof String) {
                        car.setBodyColor((String) value);
                    }
                    break;
                // Note: Other tuning parameters would require accessing Engine, Transmission,
                // Aerodynamics objects which have their own setters
                default:
                    logger.debug("Unknown tuning parameter: {}", key);
                    break;
            }
        });
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

    public static class PurchaseCarRequest {
        private String playerId;
        private String carId;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }
    }

    public static class PurchaseCarResponse {
        private boolean success;
        private String carId;
        private long pricePaid;
        private long remainingCredits;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }
        public long getPricePaid() { return pricePaid; }
        public void setPricePaid(long pricePaid) { this.pricePaid = pricePaid; }
        public long getRemainingCredits() { return remainingCredits; }
        public void setRemainingCredits(long remainingCredits) { this.remainingCredits = remainingCredits; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class TuneCarRequest {
        private String playerId;
        private String ownedCarId;
        private Map<String, Object> tuningData;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getOwnedCarId() { return ownedCarId; }
        public void setOwnedCarId(String ownedCarId) { this.ownedCarId = ownedCarId; }
        public Map<String, Object> getTuningData() { return tuningData; }
        public void setTuningData(Map<String, Object> tuningData) { this.tuningData = tuningData; }
    }

    public static class TuneCarResponse {
        private boolean success;
        private String message;
        private CarConfiguration car;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public CarConfiguration getCar() { return car; }
        public void setCar(CarConfiguration car) { this.car = car; }
    }

    public static class SellCarResponse {
        private boolean success;
        private long creditsEarned;
        private long remainingCredits;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public long getCreditsEarned() { return creditsEarned; }
        public void setCreditsEarned(long creditsEarned) { this.creditsEarned = creditsEarned; }
        public long getRemainingCredits() { return remainingCredits; }
        public void setRemainingCredits(long remainingCredits) { this.remainingCredits = remainingCredits; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class GarageResponse {
        private boolean success;
        private String playerId;
        private List<CarConfiguration> cars;
        private int totalCars;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public List<CarConfiguration> getCars() { return cars; }
        public void setCars(List<CarConfiguration> cars) { this.cars = cars; }
        public int getTotalCars() { return totalCars; }
        public void setTotalCars(int totalCars) { this.totalCars = totalCars; }
    }
}
