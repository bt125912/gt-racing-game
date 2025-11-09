        newCar.setBodyColor(request.getBodyColor() != null ? request.getBodyColor() : "White");
        newCar.setRimColor("Silver");
        newCar.setInteriorColor("Black");

        // Initialize with stock configuration
        initializeStockConfiguration(newCar, request.getCarModelId());

        return newCar;
    }

    // Simplified helper methods (would be more complex in production)

    private List<DealershipCar> generateDealershipInventory(String category, String manufacturer, String priceRange) {
        // This would normally query a dealership inventory table
        // For demo purposes, returning sample data
        List<DealershipCar> cars = new ArrayList<>();

        cars.add(createDealershipCar("GT_R34", "Nissan Skyline GT-R R34", "Nissan", 85000, "sport"));
        cars.add(createDealershipCar("M3_E46", "BMW M3 E46", "BMW", 65000, "sport"));
        cars.add(createDealershipCar("911_997", "Porsche 911 GT3", "Porsche", 150000, "supercar"));
        cars.add(createDealershipCar("CIVIC_TYPE_R", "Honda Civic Type R", "Honda", 45000, "hatchback"));
        cars.add(createDealershipCar("F1_CAR", "Formula 1 2023", "McLaren", 2000000, "formula"));

        return cars.stream()
            .filter(car -> category == null || category.equals(car.getCategory()))
            .filter(car -> manufacturer == null || manufacturer.equals(car.getManufacturer()))
            .collect(Collectors.toList());
    }

    private DealershipCar createDealershipCar(String modelId, String name, String manufacturer, long price, String category) {
        DealershipCar car = new DealershipCar();
        car.setModelId(modelId);
        car.setName(name);
        car.setManufacturer(manufacturer);
        car.setPrice(price);
        car.setCategory(category);
        car.setAvailable(true);
        car.setImageUrl("/images/cars/" + modelId + ".jpg");
        car.setDescription("Professional racing vehicle with advanced physics simulation");
        return car;
    }

    private long getCarPrice(String carModelId) {
        // Sample pricing - would come from car database
        return switch (carModelId) {
            case "GT_R34" -> 85000L;
            case "M3_E46" -> 65000L;
            case "911_997" -> 150000L;
            case "CIVIC_TYPE_R" -> 45000L;
            case "F1_CAR" -> 2000000L;
            default -> 50000L;
        };
    }

    private String getCarModelName(String carModelId) {
        return switch (carModelId) {
            case "GT_R34" -> "Nissan Skyline GT-R R34";
            case "M3_E46" -> "BMW M3 E46";
            case "911_997" -> "Porsche 911 GT3";
            case "CIVIC_TYPE_R" -> "Honda Civic Type R";
            case "F1_CAR" -> "Formula 1 2023";
            default -> "Unknown Car";
        };
    }

    private String getCarManufacturer(String carModelId) {
        return switch (carModelId) {
            case "GT_R34" -> "Nissan";
            case "M3_E46" -> "BMW";
            case "911_997" -> "Porsche";
            case "CIVIC_TYPE_R" -> "Honda";
            case "F1_CAR" -> "McLaren";
            default -> "Unknown";
        };
    }

    private String getCarCategory(String carModelId) {
        return switch (carModelId) {
            case "GT_R34", "M3_E46" -> "sport";
            case "911_997" -> "supercar";
            case "CIVIC_TYPE_R" -> "hatchback";
            case "F1_CAR" -> "formula";
            default -> "street";
        };
    }

    // Additional helper methods would continue here...
    // (Shortened for brevity)

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

    // Placeholder methods (would be implemented fully in production)
    private void initializeStockConfiguration(CarConfiguration car, String modelId) {
        // Initialize with stock engine, suspension, etc. based on model
    }

    private List<String> calculateMaintenanceNeeded(CarConfiguration car) {
        List<String> maintenance = new ArrayList<>();
        if (car.getDamageLevel() > 0.3f) maintenance.add("Body repair needed");
        if (car.getTotalDistance() > 100000) maintenance.add("Engine service recommended");
        return maintenance;
    }

    private List<String> generateUpgradeRecommendations(CarConfiguration car) {
        List<String> upgrades = new ArrayList<>();
        upgrades.add("Engine tuning for +15% power");
        upgrades.add("Sport suspension for improved handling");
        upgrades.add("Racing brakes for better stopping power");
        return upgrades;
    }

    private List<DealershipCar> getFeaturedCars(List<DealershipCar> allCars) {
        return allCars.stream().limit(3).collect(Collectors.toList());
    }

    private List<String> getSpecialOffers() {
        return List.of("20% off BMW vehicles this week", "Free tuning with supercar purchase");
    }

    private void applyEngineUpgrades(CarConfiguration car, Map<String, Object> upgrades) {
        // Apply engine modifications
    }

    private void applySuspensionUpgrades(CarConfiguration car, Map<String, Object> upgrades) {
        // Apply suspension modifications
    }

    private void applyBrakeUpgrades(CarConfiguration car, Map<String, Object> upgrades) {
        // Apply brake modifications
    }

    private void applyAerodynamicsUpgrades(CarConfiguration car, Map<String, Object> upgrades) {
        // Apply aero modifications
    }

    private void applyVisualUpgrades(CarConfiguration car, Map<String, Object> upgrades) {
        // Apply visual modifications
    }

    private long calculateTuningCost(TuneCarRequest request) {
        return 10000L; // Simplified cost calculation
    }

    private Map<String, Float> calculatePerformanceGain(CarConfiguration car, TuneCarRequest request) {
        return Map.of(
            "power", 15.0f,
            "handling", 10.0f,
            "braking", 8.0f
        );
    }

    // Data classes for garage operations

    public static class PlayerGarageResponse {
        private String playerId;
        private int totalCars;
        private Map<String, List<CarConfiguration>> carsByCategory;
        private GarageStats stats;
        private Instant lastUpdated;

        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public int getTotalCars() { return totalCars; }
        public void setTotalCars(int totalCars) { this.totalCars = totalCars; }

        public Map<String, List<CarConfiguration>> getCarsByCategory() { return carsByCategory; }
        public void setCarsByCategory(Map<String, List<CarConfiguration>> carsByCategory) { this.carsByCategory = carsByCategory; }

        public GarageStats getStats() { return stats; }
        public void setStats(GarageStats stats) { this.stats = stats; }

        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    public static class GarageStats {
        private long totalValue;
        private CarConfiguration mostValuableCar;
        private float averagePerformanceRating;
        private Map<String, Long> carsByManufacturer;
        private int totalRaces;

        // Getters and setters
        public long getTotalValue() { return totalValue; }
        public void setTotalValue(long totalValue) { this.totalValue = totalValue; }

        public CarConfiguration getMostValuableCar() { return mostValuableCar; }
        public void setMostValuableCar(CarConfiguration mostValuableCar) { this.mostValuableCar = mostValuableCar; }

        public float getAveragePerformanceRating() { return averagePerformanceRating; }
        public void setAveragePerformanceRating(float averagePerformanceRating) { this.averagePerformanceRating = averagePerformanceRating; }

        public Map<String, Long> getCarsByManufacturer() { return carsByManufacturer; }
        public void setCarsByManufacturer(Map<String, Long> carsByManufacturer) { this.carsByManufacturer = carsByManufacturer; }

        public int getTotalRaces() { return totalRaces; }
        public void setTotalRaces(int totalRaces) { this.totalRaces = totalRaces; }
    }

    // Additional data classes (PurchaseCarRequest, TuneCarRequest, etc.)
    // would be defined here - shortened for brevity

    public static class PurchaseCarRequest {
        private String playerId;
        private String carModelId;
        private String customName;
        private String bodyColor;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getCarModelId() { return carModelId; }
        public void setCarModelId(String carModelId) { this.carModelId = carModelId; }

        public String getCustomName() { return customName; }
        public void setCustomName(String customName) { this.customName = customName; }

        public String getBodyColor() { return bodyColor; }
        public void setBodyColor(String bodyColor) { this.bodyColor = bodyColor; }
    }

    public static class DealershipCar {
        private String modelId;
        private String name;
        private String manufacturer;
        private long price;
        private String category;
        private boolean available;
        private String imageUrl;
        private String description;

        // Getters and setters
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

        public long getPrice() { return price; }
        public void setPrice(long price) { this.price = price; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // Additional request/response classes would be defined here...

    public static class CarDetailsResponse {
        private CarConfiguration carConfiguration;
        private float performanceRating;
        private long estimatedValue;
        private List<String> maintenanceRequired;
        private List<String> upgradeRecommendations;

        public CarConfiguration getCarConfiguration() { return carConfiguration; }
        public void setCarConfiguration(CarConfiguration carConfiguration) { this.carConfiguration = carConfiguration; }

        public float getPerformanceRating() { return performanceRating; }
        public void setPerformanceRating(float performanceRating) { this.performanceRating = performanceRating; }

        public long getEstimatedValue() { return estimatedValue; }
        public void setEstimatedValue(long estimatedValue) { this.estimatedValue = estimatedValue; }

        public List<String> getMaintenanceRequired() { return maintenanceRequired; }
        public void setMaintenanceRequired(List<String> maintenanceRequired) { this.maintenanceRequired = maintenanceRequired; }

        public List<String> getUpgradeRecommendations() { return upgradeRecommendations; }
        public void setUpgradeRecommendations(List<String> upgradeRecommendations) { this.upgradeRecommendations = upgradeRecommendations; }
    }

    public static class DealershipResponse {
        private List<DealershipCar> cars;
        private List<DealershipCar> featuredCars;
        private List<String> specialOffers;
        private Instant lastUpdated;

        public List<DealershipCar> getCars() { return cars; }
        public void setCars(List<DealershipCar> cars) { this.cars = cars; }

        public List<DealershipCar> getFeaturedCars() { return featuredCars; }
        public void setFeaturedCars(List<DealershipCar> featuredCars) { this.featuredCars = featuredCars; }

        public List<String> getSpecialOffers() { return specialOffers; }
        public void setSpecialOffers(List<String> specialOffers) { this.specialOffers = specialOffers; }

        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    public static class PurchaseCarResponse {
        private boolean success;
        private CarConfiguration purchasedCar;
        private long creditsRemaining;
        private String transactionId;
        private Instant purchaseDate;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public CarConfiguration getPurchasedCar() { return purchasedCar; }
        public void setPurchasedCar(CarConfiguration purchasedCar) { this.purchasedCar = purchasedCar; }

        public long getCreditsRemaining() { return creditsRemaining; }
        public void setCreditsRemaining(long creditsRemaining) { this.creditsRemaining = creditsRemaining; }

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

        public Instant getPurchaseDate() { return purchaseDate; }
        public void setPurchaseDate(Instant purchaseDate) { this.purchaseDate = purchaseDate; }
    }

    public static class TuneCarRequest {
        private String playerId;
        private String carId;
        private Map<String, Object> engineUpgrades;
        private Map<String, Object> suspensionUpgrades;
        private Map<String, Object> brakeUpgrades;
        private Map<String, Object> aerodynamicsUpgrades;
        private Map<String, Object> visualUpgrades;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public Map<String, Object> getEngineUpgrades() { return engineUpgrades; }
        public void setEngineUpgrades(Map<String, Object> engineUpgrades) { this.engineUpgrades = engineUpgrades; }

        public Map<String, Object> getSuspensionUpgrades() { return suspensionUpgrades; }
        public void setSuspensionUpgrades(Map<String, Object> suspensionUpgrades) { this.suspensionUpgrades = suspensionUpgrades; }

        public Map<String, Object> getBrakeUpgrades() { return brakeUpgrades; }
        public void setBrakeUpgrades(Map<String, Object> brakeUpgrades) { this.brakeUpgrades = brakeUpgrades; }

        public Map<String, Object> getAerodynamicsUpgrades() { return aerodynamicsUpgrades; }
        public void setAerodynamicsUpgrades(Map<String, Object> aerodynamicsUpgrades) { this.aerodynamicsUpgrades = aerodynamicsUpgrades; }

        public Map<String, Object> getVisualUpgrades() { return visualUpgrades; }
        public void setVisualUpgrades(Map<String, Object> visualUpgrades) { this.visualUpgrades = visualUpgrades; }
    }

    public static class TuneCarResponse {
        private boolean success;
        private CarConfiguration tunedCar;
        private long tuningCost;
        private long creditsRemaining;
        private Map<String, Float> performanceGain;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public CarConfiguration getTunedCar() { return tunedCar; }
        public void setTunedCar(CarConfiguration tunedCar) { this.tunedCar = tunedCar; }

        public long getTuningCost() { return tuningCost; }
        public void setTuningCost(long tuningCost) { this.tuningCost = tuningCost; }

        public long getCreditsRemaining() { return creditsRemaining; }
        public void setCreditsRemaining(long creditsRemaining) { this.creditsRemaining = creditsRemaining; }

        public Map<String, Float> getPerformanceGain() { return performanceGain; }
        public void setPerformanceGain(Map<String, Float> performanceGain) { this.performanceGain = performanceGain; }
    }

    public static class SellCarRequest {
        private String playerId;
        private String carId;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }
    }

    public static class SellCarResponse {
        private boolean success;
        private long sellValue;
        private long creditsAfterSale;
        private Instant saleDate;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public long getSellValue() { return sellValue; }
        public void setSellValue(long sellValue) { this.sellValue = sellValue; }

        public long getCreditsAfterSale() { return creditsAfterSale; }
        public void setCreditsAfterSale(long creditsAfterSale) { this.creditsAfterSale = creditsAfterSale; }

        public Instant getSaleDate() { return saleDate; }
        public void setSaleDate(Instant saleDate) { this.saleDate = saleDate; }
    }

    public static class UpdateCarRequest {
        private String playerId;
        private String carId;
        private String customName;
        private String bodyColor;
        private String liveryId;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getCarId() { return carId; }
        public void setCarId(String carId) { this.carId = carId; }

        public String getCustomName() { return customName; }
        public void setCustomName(String customName) { this.customName = customName; }

        public String getBodyColor() { return bodyColor; }
        public void setBodyColor(String bodyColor) { this.bodyColor = bodyColor; }

        public String getLiveryId() { return liveryId; }
        public void setLiveryId(String liveryId) { this.liveryId = liveryId; }
    }
}
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
import java.util.stream.Collectors;

/**
 * Garage Management Lambda Function
 * Handles car collection, tuning, purchasing, and garage organization
 */
public class GarageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(GarageHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<CarConfiguration> garageTable;
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

        this.garageTable = enhancedClient.table(
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

            if ("GET".equals(httpMethod)) {
                if (path.contains("/garage/") && path.contains("/cars")) {
                    return handleGetPlayerCars(pathParams);
                } else if (path.contains("/garage/") && path.contains("/car/")) {
                    return handleGetCarDetails(pathParams);
                } else if (path.contains("/garage/dealership")) {
                    return handleGetDealership(input);
                } else {
                    return createErrorResponse(400, "Invalid endpoint");
                }
            } else if ("POST".equals(httpMethod)) {
                if (path.contains("/garage/") && path.contains("/purchase")) {
                    return handlePurchaseCar(input);
                } else if (path.contains("/garage/") && path.contains("/tune")) {
                    return handleTuneCar(input);
                } else if (path.contains("/garage/") && path.contains("/sell")) {
                    return handleSellCar(input);
                } else {
                    return createErrorResponse(400, "Invalid endpoint");
                }
            } else if ("PUT".equals(httpMethod)) {
                if (path.contains("/garage/") && path.contains("/car/")) {
                    return handleUpdateCar(input);
                } else {
                    return createErrorResponse(400, "Invalid endpoint");
                }
            } else if ("DELETE".equals(httpMethod)) {
                if (path.contains("/garage/") && path.contains("/car/")) {
                    return handleDeleteCar(input);
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
     * Get all cars owned by a player
     */
    private APIGatewayProxyResponseEvent handleGetPlayerCars(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");

            if (playerId == null) {
                return createErrorResponse(400, "Player ID is required");
            }

            // Query all cars owned by this player
            DynamoDbIndex<CarConfiguration> playerIndex = garageTable.index("OwnerIndex");

            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(playerId).build()
            );

            List<CarConfiguration> playerCars = playerIndex.query(QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build())
                .items()
                .stream()
                .collect(Collectors.toList());

            // Organize cars by category
            Map<String, List<CarConfiguration>> carsByCategory = playerCars.stream()
                .collect(Collectors.groupingBy(car ->
                    car.getCategory() != null ? car.getCategory() : "unknown"));

            // Calculate garage statistics
            GarageStats stats = calculateGarageStats(playerCars);

            PlayerGarageResponse response = new PlayerGarageResponse();
            response.setPlayerId(playerId);
            response.setTotalCars(playerCars.size());
            response.setCarsByCategory(carsByCategory);
            response.setStats(stats);
            response.setLastUpdated(Instant.now());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting player cars", e);
            return createErrorResponse(500, "Failed to get player cars: " + e.getMessage());
        }
    }

    /**
     * Get detailed information about a specific car
     */
    private APIGatewayProxyResponseEvent handleGetCarDetails(Map<String, String> pathParams) {
        try {
            String playerId = pathParams.get("playerId");
            String carId = pathParams.get("carId");

            if (playerId == null || carId == null) {
                return createErrorResponse(400, "Player ID and Car ID are required");
            }

            CarConfiguration car = garageTable.getItem(builder ->
                builder.key(k -> k.partitionValue(carId).sortValue(playerId))
            );

            if (car == null) {
                return createErrorResponse(404, "Car not found in player's garage");
            }

            // Get detailed car performance data
            CarDetailsResponse response = new CarDetailsResponse();
            response.setCarConfiguration(car);
            response.setPerformanceRating(car.getPerformanceRating());
            response.setEstimatedValue(car.getCurrentValue());
            response.setMaintenanceRequired(calculateMaintenanceNeeded(car));
            response.setUpgradeRecommendations(generateUpgradeRecommendations(car));

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting car details", e);
            return createErrorResponse(500, "Failed to get car details: " + e.getMessage());
        }
    }

    /**
     * Get available cars from dealership
     */
    private APIGatewayProxyResponseEvent handleGetDealership(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();

            String category = queryParams != null ? queryParams.get("category") : null;
            String manufacturer = queryParams != null ? queryParams.get("manufacturer") : null;
            String priceRange = queryParams != null ? queryParams.get("priceRange") : null;

            // In a real implementation, this would query a separate dealership table
            // For now, we'll generate a sample dealership inventory
            List<DealershipCar> dealershipCars = generateDealershipInventory(category, manufacturer, priceRange);

            DealershipResponse response = new DealershipResponse();
            response.setCars(dealershipCars);
            response.setFeaturedCars(getFeaturedCars(dealershipCars));
            response.setSpecialOffers(getSpecialOffers());
            response.setLastUpdated(Instant.now());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting dealership inventory", e);
            return createErrorResponse(500, "Failed to get dealership inventory: " + e.getMessage());
        }
    }

    /**
     * Purchase a car from dealership
     */
    private APIGatewayProxyResponseEvent handlePurchaseCar(APIGatewayProxyRequestEvent input) {
        try {
            PurchaseCarRequest request = objectMapper.readValue(input.getBody(), PurchaseCarRequest.class);

            if (request.getPlayerId() == null || request.getCarModelId() == null) {
                return createErrorResponse(400, "Missing required fields: playerId, carModelId");
            }

            // Get player data to check credits
            Player player = playerTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getPlayerId()))
            );

            if (player == null) {
                return createErrorResponse(404, "Player not found");
            }

            // Get car price (would normally be from dealership inventory)
            long carPrice = getCarPrice(request.getCarModelId());

            if (player.getCredits() < carPrice) {
                return createErrorResponse(400, "Insufficient credits");
            }

            // Create new car configuration
            CarConfiguration newCar = createNewCarConfiguration(request);

            // Deduct credits from player
            player.setCredits(player.getCredits() - carPrice);

            // Save car to garage
            garageTable.putItem(newCar);

            // Update player credits
            playerTable.putItem(player);

            PurchaseCarResponse response = new PurchaseCarResponse();
            response.setSuccess(true);
            response.setPurchasedCar(newCar);
            response.setCreditsRemaining(player.getCredits());
            response.setTransactionId(UUID.randomUUID().toString());
            response.setPurchaseDate(Instant.now());

            logger.info("Player {} purchased car {} for {} credits",
                request.getPlayerId(), request.getCarModelId(), carPrice);

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

            if (request.getPlayerId() == null || request.getCarId() == null) {
                return createErrorResponse(400, "Missing required fields: playerId, carId");
            }

            // Get existing car
            CarConfiguration car = garageTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getCarId()).sortValue(request.getPlayerId()))
            );

            if (car == null) {
                return createErrorResponse(404, "Car not found");
            }

            // Apply tuning modifications
            if (request.getEngineUpgrades() != null) {
                applyEngineUpgrades(car, request.getEngineUpgrades());
            }

            if (request.getSuspensionUpgrades() != null) {
                applySuspensionUpgrades(car, request.getSuspensionUpgrades());
            }

            if (request.getBrakeUpgrades() != null) {
                applyBrakeUpgrades(car, request.getBrakeUpgrades());
            }

            if (request.getAerodynamicsUpgrades() != null) {
                applyAerodynamicsUpgrades(car, request.getAerodynamicsUpgrades());
            }

            if (request.getVisualUpgrades() != null) {
                applyVisualUpgrades(car, request.getVisualUpgrades());
            }

            // Recalculate performance stats
            car.calculatePerformanceStats();

            // Calculate tuning cost
            long tuningCost = calculateTuningCost(request);

            // Check if player has enough credits
            Player player = playerTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getPlayerId()))
            );

            if (player.getCredits() < tuningCost) {
                return createErrorResponse(400, "Insufficient credits for tuning");
            }

            // Deduct credits and save
            player.setCredits(player.getCredits() - tuningCost);
            playerTable.putItem(player);

            // Save updated car
            garageTable.putItem(car);

            TuneCarResponse response = new TuneCarResponse();
            response.setSuccess(true);
            response.setTunedCar(car);
            response.setTuningCost(tuningCost);
            response.setCreditsRemaining(player.getCredits());
            response.setPerformanceGain(calculatePerformanceGain(car, request));

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error tuning car", e);
            return createErrorResponse(500, "Failed to tune car: " + e.getMessage());
        }
    }

    /**
     * Sell a car from garage
     */
    private APIGatewayProxyResponseEvent handleSellCar(APIGatewayProxyRequestEvent input) {
        try {
            SellCarRequest request = objectMapper.readValue(input.getBody(), SellCarRequest.class);

            // Get car to sell
            CarConfiguration car = garageTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getCarId()).sortValue(request.getPlayerId()))
            );

            if (car == null) {
                return createErrorResponse(404, "Car not found");
            }

            // Calculate sell value (depreciation)
            long sellValue = car.getCurrentValue();

            // Get player
            Player player = playerTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getPlayerId()))
            );

            // Add credits to player
            player.setCredits(player.getCredits() + sellValue);

            // Remove car from garage
            garageTable.deleteItem(builder ->
                builder.key(k -> k.partitionValue(request.getCarId()).sortValue(request.getPlayerId()))
            );

            // Update player credits
            playerTable.putItem(player);

            SellCarResponse response = new SellCarResponse();
            response.setSuccess(true);
            response.setSellValue(sellValue);
            response.setCreditsAfterSale(player.getCredits());
            response.setSaleDate(Instant.now());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error selling car", e);
            return createErrorResponse(500, "Failed to sell car: " + e.getMessage());
        }
    }

    /**
     * Update car (name, livery, etc.)
     */
    private APIGatewayProxyResponseEvent handleUpdateCar(APIGatewayProxyRequestEvent input) {
        try {
            UpdateCarRequest request = objectMapper.readValue(input.getBody(), UpdateCarRequest.class);

            // Get car
            CarConfiguration car = garageTable.getItem(builder ->
                builder.key(k -> k.partitionValue(request.getCarId()).sortValue(request.getPlayerId()))
            );

            if (car == null) {
                return createErrorResponse(404, "Car not found");
            }

            // Update fields
            if (request.getCustomName() != null) {
                car.setCustomName(request.getCustomName());
            }

            if (request.getBodyColor() != null) {
                car.setBodyColor(request.getBodyColor());
            }

            if (request.getLiveryId() != null) {
                car.setLiveryId(request.getLiveryId());
            }

            // Save updated car
            garageTable.putItem(car);

            return createSuccessResponse(Map.of("success", true, "updatedCar", car));

        } catch (Exception e) {
            logger.error("Error updating car", e);
            return createErrorResponse(500, "Failed to update car: " + e.getMessage());
        }
    }

    /**
     * Delete car from garage
     */
    private APIGatewayProxyResponseEvent handleDeleteCar(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> pathParams = input.getPathParameters();
            String playerId = pathParams.get("playerId");
            String carId = pathParams.get("carId");

            // Delete car
            garageTable.deleteItem(builder ->
                builder.key(k -> k.partitionValue(carId).sortValue(playerId))
            );

            return createSuccessResponse(Map.of("success", true, "message", "Car deleted"));

        } catch (Exception e) {
            logger.error("Error deleting car", e);
            return createErrorResponse(500, "Failed to delete car: " + e.getMessage());
        }
    }

    // Helper methods

    private GarageStats calculateGarageStats(List<CarConfiguration> cars) {
        GarageStats stats = new GarageStats();

        if (cars.isEmpty()) {
            return stats;
        }

        // Calculate total value
        long totalValue = cars.stream()
            .mapToLong(CarConfiguration::getCurrentValue)
            .sum();

        // Find most valuable car
        CarConfiguration mostValuable = cars.stream()
            .max(Comparator.comparingLong(CarConfiguration::getCurrentValue))
            .orElse(null);

        // Calculate average performance rating
        double avgPerformance = cars.stream()
            .mapToDouble(CarConfiguration::getPerformanceRating)
            .average()
            .orElse(0.0);

        // Count cars by manufacturer
        Map<String, Long> carsByManufacturer = cars.stream()
            .collect(Collectors.groupingBy(
                car -> car.getManufacturer() != null ? car.getManufacturer() : "Unknown",
                Collectors.counting()
            ));

        stats.setTotalValue(totalValue);
        stats.setMostValuableCar(mostValuable);
        stats.setAveragePerformanceRating((float) avgPerformance);
        stats.setCarsByManufacturer(carsByManufacturer);
        stats.setTotalRaces(cars.stream().mapToInt(CarConfiguration::getRaceCount).sum());

        return stats;
    }

    private CarConfiguration createNewCarConfiguration(PurchaseCarRequest request) {
        CarConfiguration newCar = new CarConfiguration();

        // Set basic info
        newCar.setCarId(UUID.randomUUID().toString());
        newCar.setOwnerId(request.getPlayerId());
        newCar.setBaseName(getCarModelName(request.getCarModelId()));
        newCar.setCustomName(request.getCustomName() != null ? request.getCustomName() : newCar.getBaseName());
        newCar.setManufacturer(getCarManufacturer(request.getCarModelId()));
        newCar.setCategory(getCarCategory(request.getCarModelId()));
        newCar.setPurchasePrice(getCarPrice(request.getCarModelId()));
        newCar.setCurrentValue(newCar.getPurchasePrice());
        newCar.setCreatedAt(Instant.now());

        // Set default colors
