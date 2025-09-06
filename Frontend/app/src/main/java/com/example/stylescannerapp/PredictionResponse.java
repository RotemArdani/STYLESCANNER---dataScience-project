package com.example.stylescannerapp;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// Model for server response at /predictimage
public class PredictionResponse {
    @SerializedName("items")
    public List<ItemResult> items;

    @SerializedName("total_price")
    private double totalPrice;

    @SerializedName("min_range")
    private double minRange;

    @SerializedName("max_range")
    private double maxRange;

    @SerializedName("note")
    private String note;

    @SerializedName("currency")     public String currency;

    // Getters
    public double getTotalPrice() { return totalPrice; }
    public double getMinRange() { return minRange; }
    public double getMaxRange() { return maxRange; }
    public String getNote() { return note; }
}
