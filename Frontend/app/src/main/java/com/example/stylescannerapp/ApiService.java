package com.example.stylescannerapp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {
    @POST("predictimage") // Defines the API endpoint for image prediction
    Call<PredictionResponse> uploadImageAndGetRange(@Body Image image);
}
