package com.gt.telemetry;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gt.models.physics.Car;
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
 * Telemetry Processing Lambda Function
 * Handles real-time telemetry data collection, processing, and analysis
 */
public class TelemetryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryHandler.class);
    private final ObjectMapper objectMapper;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<TelemetryRecord> telemetryTable;

    public TelemetryHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        DynamoDbClient ddbClient = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();

        this.enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(ddbClient)
            .build();

        this.telemetryTable = enhancedClient.table(
            System.getenv("TELEMETRY_TABLE"),
            TableSchema.fromBean(TelemetryRecord.class)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String httpMethod = input.getHttpMethod();
            String path = input.getPath();

            logger.info("Processing {} request for path: {}", httpMethod, path);

            if ("POST".equals(httpMethod)) {
                if (path.contains("/telemetry/batch")) {
                    return handleBatchTelemetry(input);
                } else if (path.contains("/telemetry/realtime")) {
                    return handleRealtimeTelemetry(input);
                } else {
                    return createErrorResponse(400, "Invalid endpoint");
                }
            } else if ("GET".equals(httpMethod)) {
                if (path.contains("/telemetry/session/")) {
                    return handleGetSessionTelemetry(input);
                } else if (path.contains("/telemetry/analysis/")) {
                    return handleGetTelemetryAnalysis(input);
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
     * Handle batch telemetry upload
     */
    private APIGatewayProxyResponseEvent handleBatchTelemetry(APIGatewayProxyRequestEvent input) {
        try {
            BatchTelemetryRequest request = objectMapper.readValue(input.getBody(), BatchTelemetryRequest.class);

            if (request.getTelemetryData() == null || request.getTelemetryData().isEmpty()) {
                return createErrorResponse(400, "No telemetry data provided");
            }

            List<TelemetryRecord> processedRecords = new ArrayList<>();

            // Process each telemetry data point
            for (TelemetryDataPoint dataPoint : request.getTelemetryData()) {
                TelemetryRecord record = createTelemetryRecord(request.getSessionId(), dataPoint);

                // Perform real-time analysis
                TelemetryAnalysis analysis = analyzeTelemetryPoint(dataPoint);
                record.setAnalysis(analysis);

                // Set TTL for automatic cleanup (24 hours)
                record.setTtl(Instant.now().getEpochSecond() + 86400);

                processedRecords.add(record);
            }

            // Batch write to DynamoDB
            batchWriteTelemetry(processedRecords);

            // Generate session summary
            SessionTelemetrySummary summary = generateSessionSummary(processedRecords);

            BatchTelemetryResponse response = new BatchTelemetryResponse();
            response.setProcessedCount(processedRecords.size());
            response.setSessionSummary(summary);
            response.setSuccess(true);

            logger.info("Processed {} telemetry records for session {}",
                processedRecords.size(), request.getSessionId());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error processing batch telemetry", e);
            return createErrorResponse(500, "Failed to process telemetry: " + e.getMessage());
        }
    }

    /**
     * Handle real-time telemetry streaming
     */
    private APIGatewayProxyResponseEvent handleRealtimeTelemetry(APIGatewayProxyRequestEvent input) {
        try {
            RealtimeTelemetryRequest request = objectMapper.readValue(input.getBody(), RealtimeTelemetryRequest.class);

            // Process real-time telemetry point
            TelemetryRecord record = createTelemetryRecord(request.getSessionId(), request.getTelemetryData());

            // Perform real-time analysis and warnings
            TelemetryAnalysis analysis = analyzeTelemetryPoint(request.getTelemetryData());
            record.setAnalysis(analysis);

            // Check for critical conditions
            List<String> warnings = checkCriticalConditions(request.getTelemetryData());

            // Store in DynamoDB
            telemetryTable.putItem(record);

            RealtimeTelemetryResponse response = new RealtimeTelemetryResponse();
            response.setAnalysis(analysis);
            response.setWarnings(warnings);
            response.setTimestamp(record.getTimestamp());

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error processing real-time telemetry", e);
            return createErrorResponse(500, "Failed to process real-time telemetry: " + e.getMessage());
        }
    }

    /**
     * Get telemetry data for a specific session
     */
    private APIGatewayProxyResponseEvent handleGetSessionTelemetry(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> pathParams = input.getPathParameters();
            String sessionId = pathParams.get("sessionId");

            if (sessionId == null) {
                return createErrorResponse(400, "Session ID is required");
            }

            // Query telemetry records for session
            // This is a simplified implementation - in production you'd use pagination

            SessionTelemetryResponse response = new SessionTelemetryResponse();
            response.setSessionId(sessionId);
            response.setRecordCount(0); // Placeholder
            response.setDataPoints(new ArrayList<>()); // Placeholder

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting session telemetry", e);
            return createErrorResponse(500, "Failed to get session telemetry: " + e.getMessage());
        }
    }

    /**
     * Get telemetry analysis and insights
     */
    private APIGatewayProxyResponseEvent handleGetTelemetryAnalysis(APIGatewayProxyRequestEvent input) {
        try {
            Map<String, String> pathParams = input.getPathParameters();
            Map<String, String> queryParams = input.getQueryStringParameters();

            String sessionId = pathParams.get("sessionId");
            String analysisType = queryParams != null ? queryParams.get("type") : "summary";

            if (sessionId == null) {
                return createErrorResponse(400, "Session ID is required");
            }

            TelemetryAnalysisResponse response = new TelemetryAnalysisResponse();
            response.setSessionId(sessionId);
            response.setAnalysisType(analysisType);

            switch (analysisType) {
                case "performance":
                    response.setPerformanceAnalysis(generatePerformanceAnalysis(sessionId));
                    break;
                case "vehicle_health":
                    response.setVehicleHealthAnalysis(generateVehicleHealthAnalysis(sessionId));
                    break;
                case "driving_style":
                    response.setDrivingStyleAnalysis(generateDrivingStyleAnalysis(sessionId));
                    break;
                default:
                    response.setSummaryAnalysis(generateSummaryAnalysis(sessionId));
            }

            return createSuccessResponse(response);

        } catch (Exception e) {
            logger.error("Error getting telemetry analysis", e);
            return createErrorResponse(500, "Failed to get telemetry analysis: " + e.getMessage());
        }
    }

    /**
     * Create telemetry record from data point
     */
    private TelemetryRecord createTelemetryRecord(String sessionId, TelemetryDataPoint dataPoint) {
        TelemetryRecord record = new TelemetryRecord();
        record.setSessionId(sessionId);
        record.setTimestamp(dataPoint.getTimestamp() != null ? dataPoint.getTimestamp() : Instant.now());
        record.setTelemetryData(dataPoint);
        return record;
    }

    /**
     * Analyze individual telemetry point
     */
    private TelemetryAnalysis analyzeTelemetryPoint(TelemetryDataPoint dataPoint) {
        TelemetryAnalysis analysis = new TelemetryAnalysis();

        // Performance metrics
        analysis.setSpeedEfficiency(calculateSpeedEfficiency(dataPoint));
        analysis.setBrakeEfficiency(calculateBrakeEfficiency(dataPoint));
        analysis.setCorneringEfficiency(calculateCorneringEfficiency(dataPoint));

        // Vehicle health metrics
        analysis.setEngineHealth(assessEngineHealth(dataPoint));
        analysis.setBrakeHealth(assessBrakeHealth(dataPoint));
        analysis.setTireHealth(assessTireHealth(dataPoint));

        // Driving style metrics
        analysis.setAggressiveness(calculateAggressiveness(dataPoint));
        analysis.setSmoothness(calculateSmoothness(dataPoint));
        analysis.setConsistency(calculateConsistency(dataPoint));

        return analysis;
    }

    /**
     * Check for critical conditions that require immediate attention
     */
    private List<String> checkCriticalConditions(TelemetryDataPoint dataPoint) {
        List<String> warnings = new ArrayList<>();

        // Engine temperature warning
        if (dataPoint.getEngineTemperature() > 110.0f) {
            warnings.add("Engine overheating detected");
        }

        // Brake temperature warning
        if (dataPoint.getBrakeTemperatureFront() > 500.0f || dataPoint.getBrakeTemperatureRear() > 500.0f) {
            warnings.add("Brake overheating detected");
        }

        // Tire wear warning
        for (float wear : dataPoint.getTireWear()) {
            if (wear > 0.8f) {
                warnings.add("Excessive tire wear detected");
                break;
            }
        }

        // RPM warning
        if (dataPoint.getRpm() > dataPoint.getRedlineRpm() * 0.95f) {
            warnings.add("Engine approaching redline");
        }

        // Fuel level warning
        if (dataPoint.getFuelLevel() < 0.1f) {
            warnings.add("Low fuel level");
        }

        return warnings;
    }

    /**
     * Batch write telemetry records to DynamoDB
     */
    private void batchWriteTelemetry(List<TelemetryRecord> records) {
        // In a real implementation, you'd use DynamoDB batch write
        // For now, write individually (not efficient for large batches)
        for (TelemetryRecord record : records) {
            telemetryTable.putItem(record);
        }
    }

    /**
     * Generate session summary from telemetry records
     */
    private SessionTelemetrySummary generateSessionSummary(List<TelemetryRecord> records) {
        SessionTelemetrySummary summary = new SessionTelemetrySummary();

        if (records.isEmpty()) {
            return summary;
        }

        // Calculate summary statistics
        float maxSpeed = 0.0f;
        float avgSpeed = 0.0f;
        float maxRpm = 0.0f;
        float maxBrakeTemp = 0.0f;
        int gearShifts = 0;

        for (TelemetryRecord record : records) {
            TelemetryDataPoint data = record.getTelemetryData();
            if (data != null) {
                maxSpeed = Math.max(maxSpeed, data.getSpeed());
                avgSpeed += data.getSpeed();
                maxRpm = Math.max(maxRpm, data.getRpm());
                maxBrakeTemp = Math.max(maxBrakeTemp,
                    Math.max(data.getBrakeTemperatureFront(), data.getBrakeTemperatureRear()));
            }
        }

        avgSpeed /= records.size();

        summary.setMaxSpeed(maxSpeed);
        summary.setAverageSpeed(avgSpeed);
        summary.setMaxRpm(maxRpm);
        summary.setMaxBrakeTemperature(maxBrakeTemp);
        summary.setTotalDataPoints(records.size());

        return summary;
    }

    // Analysis helper methods (simplified implementations)

    private float calculateSpeedEfficiency(TelemetryDataPoint dataPoint) {
        // Simplified efficiency calculation based on throttle vs speed
        if (dataPoint.getThrottlePosition() > 0.1f) {
            return dataPoint.getSpeed() / (dataPoint.getThrottlePosition() * 100.0f);
        }
        return 1.0f;
    }

    private float calculateBrakeEfficiency(TelemetryDataPoint dataPoint) {
        // Simplified brake efficiency based on brake temperature and pressure
        float tempFactor = Math.max(0.5f, 1.0f - (dataPoint.getBrakeTemperatureFront() - 200.0f) / 300.0f);
        return tempFactor;
    }

    private float calculateCorneringEfficiency(TelemetryDataPoint dataPoint) {
        // Simplified cornering efficiency based on lateral G and speed
        float lateralG = Math.abs(dataPoint.getLateralAcceleration());
        return Math.min(1.0f, lateralG / 1.5f); // Assume 1.5G is maximum efficient cornering
    }

    private float assessEngineHealth(TelemetryDataPoint dataPoint) {
        float health = 1.0f;

        // Temperature factor
        if (dataPoint.getEngineTemperature() > 100.0f) {
            health -= (dataPoint.getEngineTemperature() - 100.0f) / 50.0f;
        }

        // RPM factor
        float rpmRatio = dataPoint.getRpm() / dataPoint.getRedlineRpm();
        if (rpmRatio > 0.9f) {
            health -= (rpmRatio - 0.9f) * 2.0f;
        }

        return Math.max(0.0f, health);
    }

    private float assessBrakeHealth(TelemetryDataPoint dataPoint) {
        float health = 1.0f;

        // Temperature factors
        float frontTempRatio = dataPoint.getBrakeTemperatureFront() / 600.0f; // 600°C max safe temp
        float rearTempRatio = dataPoint.getBrakeTemperatureRear() / 600.0f;

        health -= Math.max(0, frontTempRatio - 0.8f) * 2.0f;
        health -= Math.max(0, rearTempRatio - 0.8f) * 2.0f;

        return Math.max(0.0f, health);
    }

    private float assessTireHealth(TelemetryDataPoint dataPoint) {
        float health = 1.0f;

        // Wear factor
        for (float wear : dataPoint.getTireWear()) {
            health -= wear * 0.25f; // Each tire contributes 25% to overall health
        }

        // Temperature factor
        for (float temp : dataPoint.getTireTemperatures()) {
            if (temp > 100.0f) {
                health -= (temp - 100.0f) / 200.0f * 0.1f;
            }
        }

        return Math.max(0.0f, health);
    }

    private float calculateAggressiveness(TelemetryDataPoint dataPoint) {
        float aggressiveness = 0.0f;

        // Throttle aggressiveness
        aggressiveness += dataPoint.getThrottlePosition() * 0.3f;

        // Brake aggressiveness
        aggressiveness += dataPoint.getBrakePosition() * 0.3f;

        // Steering aggressiveness
        aggressiveness += Math.abs(dataPoint.getSteeringAngle()) * 0.2f;

        // RPM aggressiveness
        float rpmRatio = dataPoint.getRpm() / dataPoint.getRedlineRpm();
        aggressiveness += rpmRatio * 0.2f;

        return Math.min(1.0f, aggressiveness);
    }

    private float calculateSmoothness(TelemetryDataPoint dataPoint) {
        // This would require previous data points to calculate smoothness
        // For now, return a placeholder value
        return 0.8f;
    }

    private float calculateConsistency(TelemetryDataPoint dataPoint) {
        // This would require historical data to calculate consistency
        // For now, return a placeholder value
        return 0.7f;
    }

    // Placeholder methods for complex analysis
    private Object generatePerformanceAnalysis(String sessionId) {
        return Map.of("type", "performance", "sessionId", sessionId);
    }

    private Object generateVehicleHealthAnalysis(String sessionId) {
        return Map.of("type", "vehicle_health", "sessionId", sessionId);
    }

    private Object generateDrivingStyleAnalysis(String sessionId) {
        return Map.of("type", "driving_style", "sessionId", sessionId);
    }

    private Object generateSummaryAnalysis(String sessionId) {
        return Map.of("type", "summary", "sessionId", sessionId);
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

    // Data classes for telemetry processing

    public static class TelemetryRecord {
        private String sessionId;
        private Instant timestamp;
        private TelemetryDataPoint telemetryData;
        private TelemetryAnalysis analysis;
        private long ttl; // Time-to-live for DynamoDB

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public TelemetryDataPoint getTelemetryData() { return telemetryData; }
        public void setTelemetryData(TelemetryDataPoint telemetryData) { this.telemetryData = telemetryData; }

        public TelemetryAnalysis getAnalysis() { return analysis; }
        public void setAnalysis(TelemetryAnalysis analysis) { this.analysis = analysis; }

        public long getTtl() { return ttl; }
        public void setTtl(long ttl) { this.ttl = ttl; }
    }

    public static class TelemetryDataPoint {
        private Instant timestamp;
        private float speed; // km/h
        private float rpm;
        private float redlineRpm;
        private int gear;
        private float throttlePosition; // 0.0 to 1.0
        private float brakePosition; // 0.0 to 1.0
        private float steeringAngle; // -1.0 to 1.0
        private float engineTemperature; // Celsius
        private float brakeTemperatureFront; // Celsius
        private float brakeTemperatureRear; // Celsius
        private float[] tireTemperatures; // [FL, FR, RL, RR]
        private float[] tireWear; // [FL, FR, RL, RR]
        private float[] tirePressures; // [FL, FR, RL, RR]
        private float fuelLevel; // 0.0 to 1.0
        private float lateralAcceleration; // G-force
        private float longitudinalAcceleration; // G-force
        private float yawRate; // rad/s
        private boolean escActive;
        private boolean tcsActive;
        private boolean absActive;

        // Getters and setters
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public float getSpeed() { return speed; }
        public void setSpeed(float speed) { this.speed = speed; }

        public float getRpm() { return rpm; }
        public void setRpm(float rpm) { this.rpm = rpm; }

        public float getRedlineRpm() { return redlineRpm; }
        public void setRedlineRpm(float redlineRpm) { this.redlineRpm = redlineRpm; }

        public int getGear() { return gear; }
        public void setGear(int gear) { this.gear = gear; }

        public float getThrottlePosition() { return throttlePosition; }
        public void setThrottlePosition(float throttlePosition) { this.throttlePosition = throttlePosition; }

        public float getBrakePosition() { return brakePosition; }
        public void setBrakePosition(float brakePosition) { this.brakePosition = brakePosition; }

        public float getSteeringAngle() { return steeringAngle; }
        public void setSteeringAngle(float steeringAngle) { this.steeringAngle = steeringAngle; }

        public float getEngineTemperature() { return engineTemperature; }
        public void setEngineTemperature(float engineTemperature) { this.engineTemperature = engineTemperature; }

        public float getBrakeTemperatureFront() { return brakeTemperatureFront; }
        public void setBrakeTemperatureFront(float brakeTemperatureFront) { this.brakeTemperatureFront = brakeTemperatureFront; }

        public float getBrakeTemperatureRear() { return brakeTemperatureRear; }
        public void setBrakeTemperatureRear(float brakeTemperatureRear) { this.brakeTemperatureRear = brakeTemperatureRear; }

        public float[] getTireTemperatures() { return tireTemperatures; }
        public void setTireTemperatures(float[] tireTemperatures) { this.tireTemperatures = tireTemperatures; }

        public float[] getTireWear() { return tireWear; }
        public void setTireWear(float[] tireWear) { this.tireWear = tireWear; }

        public float[] getTirePressures() { return tirePressures; }
        public void setTirePressures(float[] tirePressures) { this.tirePressures = tirePressures; }

        public float getFuelLevel() { return fuelLevel; }
        public void setFuelLevel(float fuelLevel) { this.fuelLevel = fuelLevel; }

        public float getLateralAcceleration() { return lateralAcceleration; }
        public void setLateralAcceleration(float lateralAcceleration) { this.lateralAcceleration = lateralAcceleration; }

        public float getLongitudinalAcceleration() { return longitudinalAcceleration; }
        public void setLongitudinalAcceleration(float longitudinalAcceleration) { this.longitudinalAcceleration = longitudinalAcceleration; }

        public float getYawRate() { return yawRate; }
        public void setYawRate(float yawRate) { this.yawRate = yawRate; }

        public boolean isEscActive() { return escActive; }
        public void setEscActive(boolean escActive) { this.escActive = escActive; }

        public boolean isTcsActive() { return tcsActive; }
        public void setTcsActive(boolean tcsActive) { this.tcsActive = tcsActive; }

        public boolean isAbsActive() { return absActive; }
        public void setAbsActive(boolean absActive) { this.absActive = absActive; }
    }

    // Additional data classes would be defined here...
    // (TelemetryAnalysis, BatchTelemetryRequest, etc.)

    public static class TelemetryAnalysis {
        private float speedEfficiency;
        private float brakeEfficiency;
        private float corneringEfficiency;
        private float engineHealth;
        private float brakeHealth;
        private float tireHealth;
        private float aggressiveness;
        private float smoothness;
        private float consistency;

        // Getters and setters
        public float getSpeedEfficiency() { return speedEfficiency; }
        public void setSpeedEfficiency(float speedEfficiency) { this.speedEfficiency = speedEfficiency; }

        public float getBrakeEfficiency() { return brakeEfficiency; }
        public void setBrakeEfficiency(float brakeEfficiency) { this.brakeEfficiency = brakeEfficiency; }

        public float getCorneringEfficiency() { return corneringEfficiency; }
        public void setCorneringEfficiency(float corneringEfficiency) { this.corneringEfficiency = corneringEfficiency; }

        public float getEngineHealth() { return engineHealth; }
        public void setEngineHealth(float engineHealth) { this.engineHealth = engineHealth; }

        public float getBrakeHealth() { return brakeHealth; }
        public void setBrakeHealth(float brakeHealth) { this.brakeHealth = brakeHealth; }

        public float getTireHealth() { return tireHealth; }
        public void setTireHealth(float tireHealth) { this.tireHealth = tireHealth; }

        public float getAggressiveness() { return aggressiveness; }
        public void setAggressiveness(float aggressiveness) { this.aggressiveness = aggressiveness; }

        public float getSmoothness() { return smoothness; }
        public void setSmoothness(float smoothness) { this.smoothness = smoothness; }

        public float getConsistency() { return consistency; }
        public void setConsistency(float consistency) { this.consistency = consistency; }
    }

    // Request/Response classes
    public static class BatchTelemetryRequest {
        private String sessionId;
        private List<TelemetryDataPoint> telemetryData;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public List<TelemetryDataPoint> getTelemetryData() { return telemetryData; }
        public void setTelemetryData(List<TelemetryDataPoint> telemetryData) { this.telemetryData = telemetryData; }
    }

    public static class BatchTelemetryResponse {
        private int processedCount;
        private SessionTelemetrySummary sessionSummary;
        private boolean success;

        public int getProcessedCount() { return processedCount; }
        public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }

        public SessionTelemetrySummary getSessionSummary() { return sessionSummary; }
        public void setSessionSummary(SessionTelemetrySummary sessionSummary) { this.sessionSummary = sessionSummary; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    public static class RealtimeTelemetryRequest {
        private String sessionId;
        private TelemetryDataPoint telemetryData;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public TelemetryDataPoint getTelemetryData() { return telemetryData; }
        public void setTelemetryData(TelemetryDataPoint telemetryData) { this.telemetryData = telemetryData; }
    }

    public static class RealtimeTelemetryResponse {
        private TelemetryAnalysis analysis;
        private List<String> warnings;
        private Instant timestamp;

        public TelemetryAnalysis getAnalysis() { return analysis; }
        public void setAnalysis(TelemetryAnalysis analysis) { this.analysis = analysis; }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class SessionTelemetrySummary {
        private float maxSpeed;
        private float averageSpeed;
        private float maxRpm;
        private float maxBrakeTemperature;
        private int totalDataPoints;

        public float getMaxSpeed() { return maxSpeed; }
        public void setMaxSpeed(float maxSpeed) { this.maxSpeed = maxSpeed; }

        public float getAverageSpeed() { return averageSpeed; }
        public void setAverageSpeed(float averageSpeed) { this.averageSpeed = averageSpeed; }

        public float getMaxRpm() { return maxRpm; }
        public void setMaxRpm(float maxRpm) { this.maxRpm = maxRpm; }

        public float getMaxBrakeTemperature() { return maxBrakeTemperature; }
        public void setMaxBrakeTemperature(float maxBrakeTemperature) { this.maxBrakeTemperature = maxBrakeTemperature; }

        public int getTotalDataPoints() { return totalDataPoints; }
        public void setTotalDataPoints(int totalDataPoints) { this.totalDataPoints = totalDataPoints; }
    }

    public static class SessionTelemetryResponse {
        private String sessionId;
        private int recordCount;
        private List<TelemetryDataPoint> dataPoints;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public int getRecordCount() { return recordCount; }
        public void setRecordCount(int recordCount) { this.recordCount = recordCount; }

        public List<TelemetryDataPoint> getDataPoints() { return dataPoints; }
        public void setDataPoints(List<TelemetryDataPoint> dataPoints) { this.dataPoints = dataPoints; }
    }

    public static class TelemetryAnalysisResponse {
        private String sessionId;
        private String analysisType;
        private Object performanceAnalysis;
        private Object vehicleHealthAnalysis;
        private Object drivingStyleAnalysis;
        private Object summaryAnalysis;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getAnalysisType() { return analysisType; }
        public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

        public Object getPerformanceAnalysis() { return performanceAnalysis; }
        public void setPerformanceAnalysis(Object performanceAnalysis) { this.performanceAnalysis = performanceAnalysis; }

        public Object getVehicleHealthAnalysis() { return vehicleHealthAnalysis; }
        public void setVehicleHealthAnalysis(Object vehicleHealthAnalysis) { this.vehicleHealthAnalysis = vehicleHealthAnalysis; }

        public Object getDrivingStyleAnalysis() { return drivingStyleAnalysis; }
        public void setDrivingStyleAnalysis(Object drivingStyleAnalysis) { this.drivingStyleAnalysis = drivingStyleAnalysis; }

        public Object getSummaryAnalysis() { return summaryAnalysis; }
        public void setSummaryAnalysis(Object summaryAnalysis) { this.summaryAnalysis = summaryAnalysis; }
    }
}
