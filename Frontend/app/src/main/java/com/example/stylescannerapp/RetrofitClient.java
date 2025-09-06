package com.example.stylescannerapp;

import android.util.Log;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * RetrofitClient manages two Retrofit instances:
 * 1) Flask backend (local dev server running on the host machine)
 * 2) exchangerate.host API (FX)
 *
 * Design choice:
 * - We DO NOT inject the FX access key in an interceptor.
 *   The key is passed explicitly via the FxApiService method (as a query param "access_key").
 *   This avoids mixing different providers’ auth styles and prevents accidental "apikey" misuse.
 */
public final class RetrofitClient {

    // Emulator-to-host alias: http://10.0.2.2 points to localhost of the HOST machine.
    private static final String BASE_URL = "http://10.0.2.2:5000/";

    // Official base URL for exchangerate.host. Must end with a slash.
    private static final String FX_URL   = "https://api.exchangerate.host/";

    // Singletons reused across the app
    private static Retrofit backendRetrofit;
    private static Retrofit fxRetrofit;

    private RetrofitClient() {
        // Utility class; no instances.
    }

    /** Returns a singleton Retrofit for the Flask backend. */
    public static Retrofit getInstance() {
        if (backendRetrofit == null) {
            backendRetrofit = buildRetrofit(BASE_URL);
        }
        return backendRetrofit;
    }

    /** Returns a typed service for the FX API (exchangerate.host). */
    public static FxApiService getFxService() {
        if (fxRetrofit == null) {
            fxRetrofit = buildRetrofit(FX_URL);
        }
        return fxRetrofit.create(FxApiService.class);
    }

    /**
     * Builds a Retrofit instance with:
     * - Reasonable timeouts (image uploads may take longer).
     * - Logging (BODY on debug builds only).
     * - No API key injection here: the request method adds "access_key" if present.
     */
    private static Retrofit buildRetrofit(String baseUrl) {
        // Log every HTTP line in debug builds; turn off in release to avoid leaking data.
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(msg -> Log.d("Retrofit", msg));
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)  // time to connect to server
                .readTimeout(60, TimeUnit.SECONDS)     // time to read the full response
                .writeTimeout(60, TimeUnit.SECONDS)    // time to upload request body (images)
                .addInterceptor(logging)
                .retryOnConnectionFailure(true)        // auto-retry on transient network errors
                .build();

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()) // JSON <-> POJO
                .client(client)
                .build();
    }
}
