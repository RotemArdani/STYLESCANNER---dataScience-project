package com.example.stylescannerapp;

import com.google.gson.annotations.SerializedName;

// Represents a single detected item from server
public class ItemResult {
    @SerializedName("type")
    public String type;

    @SerializedName("color")
    public String color;

    @SerializedName("price")
    public double price;
}
