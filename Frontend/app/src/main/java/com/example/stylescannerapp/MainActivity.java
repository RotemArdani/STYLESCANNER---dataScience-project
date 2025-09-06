package com.example.stylescannerapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.stylescannerapp.databinding.ActivityMainBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MainActivity:
 * - Picks image (Camera / Photo Picker / Files).
 * - Obtains location with FusedLocationProvider after runtime permissions.
 * - Sends image + address to backend.
 * - Renders prediction and converts from server base currency to local currency.
 * - Uses ViewFlipper screens: [0]=main, [1]=loading, [2]=result.
 */
public class MainActivity extends AppCompatActivity {

    /** Request code for camera runtime permission (location uses Activity Result API). */
    private static final int CAMERA_PERMISSION_REQUEST = 200;

    /** Launchers for Photo Picker (API 33+), SAF (OpenDocument), and location permissions. */
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private ActivityResultLauncher<String[]> locationPermsLauncher;

    /** Fused location client. */
    private FusedLocationProviderClient fused;

    /** ViewBinding and screen container. */
    private ActivityMainBinding binding;
    private ViewFlipper viewFlipper;

    /** In-memory image and last known location. */
    private Bitmap bitmap;
    private Location currentLocation;

    /** Last country ISO for formatting and last FX state. */
    private String lastCountryIso = "IL";
    private String lastFxCurrency = "ILS";
    private double lastFxRate = 1.0;

    /** Activity Result: camera thumbnail capture flow. */
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Object extra = result.getData().getExtras() != null
                            ? result.getData().getExtras().get("data") : null;
                    if (extra instanceof Bitmap) {
                        bitmap = (Bitmap) extra;
                        binding.uploadedImage.setImageBitmap(bitmap);
                        showLoadingScreen();
                        sendPhotoToServer();
                    } else {
                        Toast.makeText(this, "Camera returned no image", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set ViewFlipper animations.
        viewFlipper = binding.viewFlipper;
        viewFlipper.setInAnimation(this, R.anim.slide_in_right);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_left);

        // Initialize fused location client.
        fused = LocationServices.getFusedLocationProviderClient(this);

        // Register location permission request launcher.
        locationPermsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        startLocationFlow();
                    } else {
                        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Register Photo Picker (API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerLauncher = registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri == null) {
                            Toast.makeText(this, "Selection cancelled", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        handleImageUriFromPicker(uri);
                    }
            );
        }

        // Register SAF (OpenDocument) for all API levels.
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) handleImageUriFromSaf(uri); }
        );

        // Main button: open the image source chooser.
        binding.selectImageButton.setOnClickListener(v -> openImageChooser());

        // Result/loading buttons: return to main screen.
        binding.tryAgainButtonResult.setOnClickListener(v -> showMainScreen());
        binding.tryAgainButtonLoading.setOnClickListener(v -> showMainScreen());

        // Start permission → location acquisition flow.
        ensureLocationPerms();
    }

    /** Shows a chooser: Camera / Gallery / Files. */
    private void openImageChooser() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"Camera", "Gallery (Albums)", "Browse device files"}, (d, which) -> {
                    if (which == 0) openCamera();
                    else if (which == 1) openGallery();
                    else browseDeviceFiles();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    /** Requests camera permission if needed and launches camera capture. */
    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
            return;
        }
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    /** Uses Photo Picker on API 33+, otherwise opens SAF (OpenDocument). */
    private void openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build()
            );
        } else {
            openDocumentLauncher.launch(new String[]{"image/*"});
        }
    }

    /** Opens the Files UI (SAF) directly. */
    private void browseDeviceFiles() {
        openDocumentLauncher.launch(new String[]{"image/*"});
    }

    /** Loads an image from Photo Picker result and triggers upload. */
    private void handleImageUriFromPicker(@NonNull Uri uri) {
        try {
            Bitmap bmp = loadBitmapFromUri(uri);
            if (bmp == null) {
                Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
                return;
            }
            bitmap = bmp;
            binding.uploadedImage.setImageBitmap(bmp);
            showLoadingScreen();
            sendPhotoToServer();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
        }
    }

    /** Takes persistable read permission for SAF Uri, decodes image, and triggers upload. */
    private void handleImageUriFromSaf(@NonNull Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
        try {
            bitmap = loadBitmapFromUri(uri);
            if (bitmap == null) {
                Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.uploadedImage.setImageBitmap(bitmap);
            showLoadingScreen();
            sendPhotoToServer();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
        }
    }

    /** Decodes a bitmap from a content Uri (ImageDecoder on API 28+, stream fallback below). */
    private @Nullable Bitmap loadBitmapFromUri(@NonNull Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src);
        } else {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                return android.graphics.BitmapFactory.decodeStream(is);
            }
        }
    }

    /** Requests location permissions if missing and starts fetching a location when granted. */
    private void ensureLocationPerms() {
        boolean fineOk = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseOk = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineOk && !coarseOk) {
            locationPermsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            startLocationFlow();
        }
    }

    /**
     * Attempts to get last known location; if null, requests a one-shot high-accuracy update.
     */
    private void startLocationFlow() {
        boolean fineOk = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseOk = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fineOk && !coarseOk) return;

        fused.getLastLocation().addOnSuccessListener(last -> {
            if (last != null) {
                useLocation(last);
            } else {
                LocationRequest req = new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, 2000)
                        .setMinUpdateIntervalMillis(1000)
                        .setMaxUpdates(1)
                        .build();

                fused.requestLocationUpdates(req, new LocationCallback() {
                    @Override public void onLocationResult(LocationResult res) {
                        if (!res.getLocations().isEmpty()) {
                            useLocation(res.getLastLocation());
                        } else {
                            Toast.makeText(MainActivity.this, "No location yet", Toast.LENGTH_SHORT).show();
                        }
                        fused.removeLocationUpdates(this);
                    }
                }, Looper.getMainLooper());
            }
        });
    }

    /** Stores a location fix and logs coordinates. */
    private void useLocation(@NonNull Location loc) {
        currentLocation = loc;
        Log.d("LOC", "lat=" + loc.getLatitude() + ", lon=" + loc.getLongitude());
    }

    /**
     * Encodes the bitmap to Base64, reverse-geocodes the location to Address,
     * builds the payload, calls the backend, and handles the response.
     */
    private void sendPhotoToServer() {
        if (bitmap == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            showMainScreen();
            return;
        }
        if (currentLocation == null) {
            binding.loadingText.setText("Getting location…");
            startLocationFlow();
            return;
        }

        String encodedImage = bitmapToBase64(bitmap);
        Address address = getAddressFromLocation(currentLocation);

        // Debug log of payload fields.
        logDebugUploadPayload(encodedImage, address, currentLocation);

        if (address == null) {
            Toast.makeText(this, "Couldn't get address from location", Toast.LENGTH_SHORT).show();
            showMainScreen();
            return;
        }

        if (address.getCountryCode() != null) {
            lastCountryIso = address.getCountryCode();
        }

        // Payload model for backend.
        Image image = new Image(
                encodedImage,
                address.getCountryName(),
                address.getLocality()
        );

        // Log basic payload summary.
        Log.i("PayloadCheck",
                "POST /predictimage | country=" + address.getCountryName()
                        + ", locality=" + address.getLocality()
                        + ", iso=" + lastCountryIso
                        + ", base64Len=" + encodedImage.length());

        ApiService api = RetrofitClient.getInstance().create(ApiService.class);
        api.uploadImageAndGetRange(image).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    PredictionResponse prediction = resp.body();

                    // Per-item server debug log.
                    if (prediction.items != null) {
                        for (ItemResult it : prediction.items) {
                            Log.i("ServerItems",
                                    "Detected -> type=" + it.type +
                                            ", color=" + it.color +
                                            ", price=" + it.price);
                        }
                    }

                    // Debug: USD item summary and raw JSON.
                    logItemsUsd(prediction);
                    logRawPredictionJson(prediction);

                    // Render result UI.
                    showResultScreen(prediction);

                } else {
                    try {
                        String rawError = resp.errorBody() != null ? resp.errorBody().string() : "";
                        JSONObject obj = new JSONObject(rawError);
                        String code = obj.optString("code");
                        String errorMsg = obj.optString("error");

                        if ("NO_CLOTHING_DETECTED".equals(code)) {
                            showLoadingError("No clothing item detected. Please upload a clear clothing image.");
                        } else {
                            showLoadingError(errorMsg.isEmpty()
                                    ? "Failed to get response from server. Please try again later."
                                    : errorMsg);
                        }

                        Log.w("ServerError", "HTTP " + resp.code() + " code=" + code + " msg=" + errorMsg);

                    } catch (Exception e) {
                        Log.e("ServerError", "Failed to parse error body", e);
                        showLoadingError("Failed to get response from server. Please try again later.");
                    }
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                Log.e("Network", "Connection failed: " + t.getMessage(), t);
                showLoadingError("Cannot connect to the server right now.\nPlease try again in a few minutes.");
            }
        });
    }

    /** Shows an error message on loading screen and reveals Try Again button. */
    private void showLoadingError(String message) {
        binding.loadingText.setText(message);
        binding.progressBar.setVisibility(android.view.View.GONE);
        binding.tryAgainButtonLoading.setVisibility(android.view.View.VISIBLE);
        viewFlipper.setDisplayedChild(1);
    }

    /** Resets UI to the main screen and clears the last image. */
    private void showMainScreen() {
        viewFlipper.setDisplayedChild(0);
        binding.loadingText.setText("What a stunning style!\n\nLet's see how much it's worth:");
        binding.progressBar.setVisibility(android.view.View.VISIBLE);
        binding.tryAgainButtonResult.setVisibility(android.view.View.GONE);
        binding.tryAgainButtonLoading.setVisibility(android.view.View.GONE);
        binding.uploadedImage.setImageBitmap(null);
        bitmap = null;
    }

    /** Switches to loading screen before network calls. */
    private void showLoadingScreen() {
        viewFlipper.setDisplayedChild(1);
        binding.loadingText.setText("What a stunning style!\nLet's see how much it's worth:");
        binding.progressBar.setVisibility(android.view.View.VISIBLE);
        binding.tryAgainButtonLoading.setVisibility(android.view.View.GONE);
    }

    /**
     * Shows detected items sentence, converts base range to local currency,
     * and renders the final text and image.
     */
    private void showResultScreen(@Nullable PredictionResponse prediction) {
        viewFlipper.setDisplayedChild(2);

        boolean noItems = (prediction == null || prediction.items == null || prediction.items.isEmpty());
        boolean zeros = prediction != null
                && prediction.getMinRange() == 0.0
                && prediction.getMaxRange() == 0.0
                && prediction.getTotalPrice() == 0.0;

        if (noItems || zeros) {
            String msg = "Unable to estimate this image because no clothing items were detected.";
            binding.styleValueRange.setText(msg + "\n\nWant to measure another item? Click Try Again ↓");
            if (bitmap != null) binding.resultImage.setImageBitmap(bitmap);
            binding.tryAgainButtonResult.setVisibility(android.view.View.VISIBLE);
            binding.tryAgainButtonResult.setOnClickListener(v -> showMainScreen());
            return;
        }

        String detectedSentence = buildDetectedSentence(prediction);

        double lowBase = Math.min(prediction.getMinRange(), prediction.getMaxRange());
        double highBase = Math.max(prediction.getMinRange(), prediction.getMaxRange());
        binding.styleValueRange.setText(detectedSentence + "\n\nConverting price…");

        String serverBaseCurrency = normalizeCurrency(
                (prediction != null) ? prediction.currency : null
        );
        Log.i("FX_DEBUG", "Server base currency = " + serverBaseCurrency);

        convertAndRender(prediction, lowBase, highBase, lastCountryIso, serverBaseCurrency);

        if (bitmap != null) binding.resultImage.setImageBitmap(bitmap);

        binding.tryAgainButtonResult.setVisibility(android.view.View.VISIBLE);
        binding.tryAgainButtonResult.setOnClickListener(v -> showMainScreen());
    }

    /**
     * Requests FX rate (amount=1) and renders converted range.
     * If FX fails, renders base currency values.
     */
    private void convertAndRender(PredictionResponse prediction,
                                  double minBase, double maxBase,
                                  String targetCountryIso,
                                  String serverBaseCurrency) {

        String targetCurrencyCode = currencyForCountry(targetCountryIso).getCurrencyCode();

        if (serverBaseCurrency.equalsIgnoreCase(targetCurrencyCode)) {
            lastFxCurrency = targetCurrencyCode;
            lastFxRate = 1.0;

            String rangeLine = "Outfit value range (Min → Max): "
                    + formatCurrencyForCountry(minBase, targetCountryIso)
                    + " - "
                    + formatCurrencyForCountry(maxBase, targetCountryIso);

            String fxInfo = "FX: 1 " + serverBaseCurrency + " = 1.0 " + targetCurrencyCode + ".";

            String finalText = buildDetectedSentence(prediction) + "\n\n" + rangeLine + "\n" + fxInfo
                    + "\n\nWant to measure another item? Click Try Again ↓";
            binding.styleValueRange.setText(finalText);
            return;
        }

        String accessKey = (BuildConfig.EXCHANGERATE_API_KEY == null || BuildConfig.EXCHANGERATE_API_KEY.isEmpty())
                ? null : BuildConfig.EXCHANGERATE_API_KEY;

        FxApiService fx = RetrofitClient.getFxService();
        fx.convert(serverBaseCurrency, targetCurrencyCode, 1.0, accessKey)
                .enqueue(new retrofit2.Callback<FxResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<FxResponse> call, retrofit2.Response<FxResponse> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().result > 0) {
                            lastFxCurrency = targetCurrencyCode;
                            lastFxRate = resp.body().result;

                            double lowTarget  = minBase * lastFxRate;
                            double highTarget = maxBase * lastFxRate;

                            String rangeLine = "Outfit value range (Min → Max): "
                                    + formatCurrencyForCountry(lowTarget, targetCountryIso)
                                    + " - "
                                    + formatCurrencyForCountry(highTarget, targetCountryIso);

                            String fxInfo = "FX: 1 " + serverBaseCurrency + " = "
                                    + formatRate(lastFxRate) + " " + lastFxCurrency;

                            String finalText = buildDetectedSentence(prediction) + "\n\n" + rangeLine + "\n" + fxInfo
                                    + "\n\nWant to measure another item? Click Try Again ↓";
                            binding.styleValueRange.setText(finalText);

                        } else {
                            lastFxCurrency = serverBaseCurrency;
                            lastFxRate = 1.0;

                            String baseIso = isoFromCurrency(serverBaseCurrency);

                            String rangeLine = "Outfit value range (Min → Max): "
                                    + formatCurrencyForCountry(minBase, baseIso)
                                    + " - "
                                    + formatCurrencyForCountry(maxBase, baseIso);

                            String fxInfo = "FX: failed, showing " + serverBaseCurrency;

                            String finalText = buildDetectedSentence(prediction) + "\n\n" + rangeLine + "\n" + fxInfo
                                    + "\n\nWant to measure another item? Click Try Again ↓";
                            binding.styleValueRange.setText(finalText);
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<FxResponse> call, Throwable t) {
                        lastFxCurrency = serverBaseCurrency;
                        lastFxRate = 1.0;

                        String baseIso = isoFromCurrency(serverBaseCurrency);

                        String rangeLine = "Outfit value range (Min → Max): "
                                + formatCurrencyForCountry(minBase, baseIso)
                                + " - "
                                + formatCurrencyForCountry(maxBase, baseIso);

                        String fxInfo = "FX: failed, showing " + serverBaseCurrency;

                        String finalText = buildDetectedSentence(prediction) + "\n\n" + rangeLine + "\n" + fxInfo
                                + "\n\nWant to measure another item? Click Try Again ↓";
                        binding.styleValueRange.setText(finalText);
                    }
                });
    }

    /** Builds a short sentence that lists detected items (color + type). */
    private String buildDetectedSentence(@Nullable PredictionResponse prediction) {
        if (prediction == null || prediction.items == null || prediction.items.isEmpty()) {
            return "I couldn't detect specific items in the image.";
        }
        List<String> parts = new ArrayList<>();
        for (ItemResult it : prediction.items) {
            String color = (it != null && it.color != null) ? cap(it.color) : "Unknown";
            String type  = (it != null && it.type  != null) ? cap(it.type)  : "Item";
            parts.add(color + " " + type);
        }
        if (parts.size() == 1) return "I detect you wore: " + parts.get(0) + ".";
        if (parts.size() == 2) return "I detect you wore: " + parts.get(0) + " and " + parts.get(1) + ".";
        StringBuilder sb = new StringBuilder("I detect you wore: ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i == parts.size() - 1 ? " and " : ", ");
            sb.append(parts.get(i));
        }
        sb.append(".");
        return sb.toString();
    }

    /** Normalizes currency to ISO codes and defaults to USD. */
    private String normalizeCurrency(String c) {
        if (c == null) return "USD";
        String t = c.trim();
        if (t.isEmpty()) return "USD";
        if (t.equals("$")) return "USD";
        if (t.equals("₪")) return "ILS";
        if (t.equals("£")) return "GBP";
        if (t.equals("€")) return "EUR";
        return t.toUpperCase(Locale.ROOT);
    }

    /** Capitalizes first character of a string. */
    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        String lower = s.toLowerCase(Locale.getDefault());
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Formats a value to currency according to a country ISO (e.g., "IL" -> ILS). */
    private String formatCurrencyForCountry(double value, String countryIso) {
        Locale locale = new Locale("", countryIso);
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        nf.setCurrency(currencyForCountry(countryIso));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(value);
    }

    /** Returns Currency by country ISO with ILS fallback. */
    private Currency currencyForCountry(String countryIso) {
        try {
            return Currency.getInstance(new Locale("", countryIso));
        } catch (Exception e) {
            return Currency.getInstance("ILS");
        }
    }

    /** Maps currency code to a representative country ISO (for NumberFormat locale). */
    private String isoFromCurrency(String currencyCode) {
        if (currencyCode == null) return "IL";
        switch (currencyCode.toUpperCase(Locale.ROOT)) {
            case "USD": return "US";
            case "EUR": return "FR";
            case "GBP": return "GB";
            case "ILS": return "IL";
            case "AUD": return "AU";
            case "CAD": return "CA";
            case "JPY": return "JP";
            default:    return "US";
        }
    }

    /** Formats FX rate to a readable string. */
    private String formatRate(double r) {
        return new DecimalFormat("#,##0.####").format(r);
    }

    /** Returns current local timestamp string. */
    private String nowString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
    }

    /** Compresses bitmap to JPEG and encodes as Base64 without line breaks. */
    private String bitmapToBase64(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /** Reverse-geocodes lat/lon to a single Address. */
    private Address getAddressFromLocation(@NonNull Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            return (addresses != null && !addresses.isEmpty()) ? addresses.get(0) : null;
        } catch (IOException e) {
            Log.e("Geocoder", "Failed", e);
            return null;
        }
    }

    /** Camera permission callback (location permissions use Activity Result API). */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ====================== DEBUG HELPERS (code-behavior-only comments) ======================

    /** When true, logs full Base64 image string; when false, logs only head/tail. */
    private static final boolean DEBUG_LOG_FULL_IMAGE = false;
    /** Number of Base64 characters to log from head/tail when full logging is disabled. */
    private static final int DEBUG_IMAGE_HEAD_TAIL = 64;

    /** Returns a safe substring between indices. */
    private String safeSubstr(String s, int start, int end) {
        if (s == null) return null;
        int n = s.length();
        if (start < 0) start = 0;
        if (end > n) end = n;
        if (start >= end) return "";
        return s.substring(start, end);
    }

    /** Computes MD5 hex digest of a string. */
    private String md5Hex(String s) {
        if (s == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** Logs payload fields that are sent to the backend. */
    private void logDebugUploadPayload(String base64, Address address, Location loc) {
        if (!BuildConfig.DEBUG) return;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject jo = new JsonObject();

        if (address != null) {
            jo.addProperty("country", address.getCountryName());
            jo.addProperty("countryCode", address.getCountryCode());
            jo.addProperty("locality", address.getLocality());
        }
        if (loc != null) {
            jo.addProperty("latitude",  loc.getLatitude());
            jo.addProperty("longitude", loc.getLongitude());
        }

        if (base64 != null) {
            jo.addProperty("image_base64_len", base64.length());
            jo.addProperty("image_base64_md5", md5Hex(base64));
            if (DEBUG_LOG_FULL_IMAGE) {
                jo.addProperty("image_base64_full", base64);
            } else {
                String head = safeSubstr(base64, 0, Math.min(DEBUG_IMAGE_HEAD_TAIL, base64.length()));
                String tail = safeSubstr(base64, Math.max(0, base64.length() - DEBUG_IMAGE_HEAD_TAIL), base64.length());
                jo.addProperty("image_base64_head", head);
                jo.addProperty("image_base64_tail", tail);
            }
        }

        Log.i("UPLOAD_PAYLOAD", gson.toJson(jo));
    }

    /** Logs brand/section/type/color/price per item in USD and USD ranges/total. */
    private void logItemsUsd(PredictionResponse p) {
        if (!BuildConfig.DEBUG || p == null) return;
        NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
        try { usd.setCurrency(Currency.getInstance("USD")); } catch (Exception ignore) {}

        Log.i("PRICE_USD", "---- Items (USD) ----");
        if (p.items != null && !p.items.isEmpty()) {
            Gson gson = new Gson();
            int i = 1;
            for (ItemResult it : p.items) {
                JsonObject jo = gson.toJsonTree(it).getAsJsonObject();

                String brand   = (jo.has("brand")   && !jo.get("brand").isJsonNull())   ? jo.get("brand").getAsString()
                        : (jo.has("brandName") && !jo.get("brandName").isJsonNull()) ? jo.get("brandName").getAsString() : null;
                String section = (jo.has("section") && !jo.get("section").isJsonNull()) ? jo.get("section").getAsString() : null;
                String type    = (jo.has("type")    && !jo.get("type").isJsonNull())    ? jo.get("type").getAsString()    : null;
                String color   = (jo.has("color")   && !jo.get("color").isJsonNull())   ? jo.get("color").getAsString()   : null;
                double price   = (jo.has("price")   && !jo.get("price").isJsonNull())   ? jo.get("price").getAsDouble()   : 0.0;

                String label   = (brand  != null ? brand + " " : "")
                        + (color  != null ? cap(color) + " " : "")
                        + (type   != null ? cap(type)  : "Item");

                Log.i("PRICE_USD", i + ") " + label
                        + " | section=" + (section != null ? section : "-")
                        + " | brand="   + (brand   != null ? brand   : "-")
                        + " | type="    + (type    != null ? type    : "-")
                        + " | color="   + (color   != null ? color   : "-")
                        + " | price="   + usd.format(price));
                i++;
            }
        } else {
            Log.i("PRICE_USD", "(no items)");
        }

        double low  = Math.min(p.getMinRange(), p.getMaxRange());
        double high = Math.max(p.getMinRange(), p.getMaxRange());
        Log.i("PRICE_USD", "Range (min→max): " + usd.format(low) + " - " + usd.format(high));
        Log.i("PRICE_USD", "Total: " + usd.format(p.getTotalPrice()));
    }

    /** Logs the full prediction object as pretty JSON. */
    private void logRawPredictionJson(PredictionResponse p) {
        if (!BuildConfig.DEBUG || p == null) return;
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Log.i("PREDICT_RAW", gson.toJson(p));
        } catch (Exception e) {
            Log.w("PREDICT_RAW", "Failed to print raw JSON", e);
        }
    }
}
