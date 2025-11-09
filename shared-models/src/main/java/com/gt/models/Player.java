package com.gt.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.util.Map;

@DynamoDbBean
@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {
    private String playerId;
    private String username;
    private String email;
    private int level;
    private long experience;
    private long credits;
    private PlayerStats stats;
    private Map<String, Object> settings;
    private Instant createdAt;
    private Instant lastLoginAt;

    public Player() {}

    public Player(String playerId, String username, String email) {
        this.playerId = playerId;
        this.username = username;
        this.email = email;
        this.level = 1;
        this.experience = 0;
        this.credits = 25000; // Starting credits
        this.stats = new PlayerStats();
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
    }

    @DynamoDbPartitionKey
    @JsonProperty("playerId")
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
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

    public PlayerStats getStats() {
        return stats;
    }

    public void setStats(PlayerStats stats) {
        this.stats = stats;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public void addExperience(long exp) {
        this.experience += exp;
        updateLevel();
    }

    public void addCredits(long amount) {
        this.credits += amount;
    }

    public boolean spendCredits(long amount) {
        if (this.credits >= amount) {
            this.credits -= amount;
            return true;
        }
        return false;
    }

    private void updateLevel() {
        int newLevel = calculateLevel(this.experience);
        if (newLevel > this.level) {
            this.level = newLevel;
            // Award credits for level up
            this.credits += newLevel * 1000;
        }
    }

    private int calculateLevel(long exp) {
        // Simple level calculation: every 10,000 XP = 1 level
        return (int) Math.max(1, exp / 10000 + 1);
    }
}
