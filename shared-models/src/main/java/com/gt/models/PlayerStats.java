package com.gt.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerStats {
    private int totalRaces;
    private int wins;
    private int podiumFinishes;
    private double totalDistance; // in kilometers
    private long totalRaceTime; // in milliseconds
    private double bestLapTime;
    private String favoriteTrack;
    private String favoriteCar;
    private int dirtRaces;
    private int roadRaces;
    private int ovalRaces;
    private double averagePosition;
    private long crashCount;
    private double cleanRacingPercentage;

    public PlayerStats() {
        this.totalRaces = 0;
        this.wins = 0;
        this.podiumFinishes = 0;
        this.totalDistance = 0.0;
        this.totalRaceTime = 0;
        this.bestLapTime = Double.MAX_VALUE;
        this.favoriteTrack = "";
        this.favoriteCar = "";
        this.dirtRaces = 0;
        this.roadRaces = 0;
        this.ovalRaces = 0;
        this.averagePosition = 0.0;
        this.crashCount = 0;
        this.cleanRacingPercentage = 100.0;
    }

    // Getters and Setters
    public int getTotalRaces() {
        return totalRaces;
    }

    public void setTotalRaces(int totalRaces) {
        this.totalRaces = totalRaces;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getPodiumFinishes() {
        return podiumFinishes;
    }

    public void setPodiumFinishes(int podiumFinishes) {
        this.podiumFinishes = podiumFinishes;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public long getTotalRaceTime() {
        return totalRaceTime;
    }

    public void setTotalRaceTime(long totalRaceTime) {
        this.totalRaceTime = totalRaceTime;
    }

    public double getBestLapTime() {
        return bestLapTime;
    }

    public void setBestLapTime(double bestLapTime) {
        this.bestLapTime = bestLapTime;
    }

    public String getFavoriteTrack() {
        return favoriteTrack;
    }

    public void setFavoriteTrack(String favoriteTrack) {
        this.favoriteTrack = favoriteTrack;
    }

    public String getFavoriteCar() {
        return favoriteCar;
    }

    public void setFavoriteCar(String favoriteCar) {
        this.favoriteCar = favoriteCar;
    }

    public int getDirtRaces() {
        return dirtRaces;
    }

    public void setDirtRaces(int dirtRaces) {
        this.dirtRaces = dirtRaces;
    }

    public int getRoadRaces() {
        return roadRaces;
    }

    public void setRoadRaces(int roadRaces) {
        this.roadRaces = roadRaces;
    }

    public int getOvalRaces() {
        return ovalRaces;
    }

    public void setOvalRaces(int ovalRaces) {
        this.ovalRaces = ovalRaces;
    }

    public double getAveragePosition() {
        return averagePosition;
    }

    public void setAveragePosition(double averagePosition) {
        this.averagePosition = averagePosition;
    }

    public long getCrashCount() {
        return crashCount;
    }

    public void setCrashCount(long crashCount) {
        this.crashCount = crashCount;
    }

    public double getCleanRacingPercentage() {
        return cleanRacingPercentage;
    }

    public void setCleanRacingPercentage(double cleanRacingPercentage) {
        this.cleanRacingPercentage = cleanRacingPercentage;
    }

    // Utility methods
    public double getWinPercentage() {
        return totalRaces > 0 ? (double) wins / totalRaces * 100.0 : 0.0;
    }

    public double getPodiumPercentage() {
        return totalRaces > 0 ? (double) podiumFinishes / totalRaces * 100.0 : 0.0;
    }

    public void updateAfterRace(int position, double lapTime, double raceDistance, long raceTime, boolean crashed, String trackType) {
        this.totalRaces++;

        if (position == 1) {
            this.wins++;
        }

        if (position <= 3) {
            this.podiumFinishes++;
        }

        this.totalDistance += raceDistance;
        this.totalRaceTime += raceTime;

        if (lapTime > 0 && lapTime < this.bestLapTime) {
            this.bestLapTime = lapTime;
        }

        if (crashed) {
            this.crashCount++;
        }

        // Update track type counters
        switch (trackType.toLowerCase()) {
            case "dirt":
                this.dirtRaces++;
                break;
            case "road":
                this.roadRaces++;
                break;
            case "oval":
                this.ovalRaces++;
                break;
        }

        // Recalculate averages
        this.averagePosition = (this.averagePosition * (this.totalRaces - 1) + position) / this.totalRaces;
        this.cleanRacingPercentage = ((double) (this.totalRaces - this.crashCount) / this.totalRaces) * 100.0;
    }
}
