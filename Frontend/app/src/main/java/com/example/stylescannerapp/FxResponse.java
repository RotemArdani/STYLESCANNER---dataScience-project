// FxResponse.java
package com.example.stylescannerapp;

import com.google.gson.annotations.SerializedName;

public class FxResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("result")      // the converted amount (amount * rate)
    public double result;

    // Optional extra fields that may exist; not always required
    public static class Info {
        @SerializedName("rate")
        public double rate;        // FX rate (from -> to)
    }
    @SerializedName("info")
    public Info info;
}
