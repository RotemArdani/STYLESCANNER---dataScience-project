package com.example.stylescannerapp;

import androidx.annotation.Nullable;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * FX client for exchangerate.host.
 *
 * Auth:
 * - If you have an access key, pass it via the "access_key" query parameter.
 * - If you don’t, pass null and Retrofit will omit the parameter entirely.
 *
 * Example:
 *   GET https://api.exchangerate.host/convert?from=USD&to=ILS&amount=1&access_key=YOUR_KEY
 */
public interface FxApiService {

    @GET("convert")
    Call<FxResponse> convert(
            @Query("from") String from,                 // base currency returned by your server (e.g., "USD")
            @Query("to") String to,                     // target currency for display (e.g., "ILS")
            @Query("amount") double amount,             // set to 1.0 to fetch 1:1 conversion rate
            @Nullable @Query("access_key") String accessKey // optional; pass null to omit entirely
    );
}
