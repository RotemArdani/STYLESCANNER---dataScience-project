//package com.example.stylescannerapp;
//
//import android.Manifest.permission;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.location.Address;
//import android.location.Geocoder;
//import android.location.Location;
//import android.net.Uri;
//import android.os.Bundle;
//import android.provider.MediaStore;
//import android.util.Base64;
//import android.util.Log;
//import android.widget.Toast;
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import com.example.stylescannerapp.databinding.ActivityMainBinding;
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationServices;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.util.List;
//import java.util.Locale;
//import retrofit2.Call;
//import retrofit2.Callback;
//import retrofit2.Response;
//import android.app.AlertDialog;
//import android.content.DialogInterface;
//
//
//public class MainActivity extends AppCompatActivity {
//
//    // Tag for logging, helps identify logs related to this class
//    private static final String TAG = "Topaz";
//
//    private ActivityMainBinding binding;
//
//    private Bitmap bitmap; // This variable holds the image captured or selected by the user
//
//    private Location mLocation; // This variable holds the user's current location
//
//    private FusedLocationProviderClient fusedLocationProviderClient; // Helps access device's location services
//
//    // Requests permission to access location
//    private final ActivityResultLauncher<String> requestPermissionLauncher =
//            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
//                    isGranted -> {
//                        if (isGranted) {
//                            getLastLocation(); // If permission granted, get location
//                        } else {
//                            Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show(); // If denied, show a toast message
//                        }
//                    });
//
//    // Handles result from the camera activity
//    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
//            new StartActivityForResult(),
//            result -> {
//                if (result.getResultCode() == RESULT_OK) {
//                    bitmap = (Bitmap) result.getData().getExtras().get("data"); // If image is taken, store it in 'bitmap'
//                    binding.image.setImageBitmap(bitmap); // Display image in the ImageView
//                }
//            });
//
//    // Handles result from gallery activity
//    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
//            new StartActivityForResult(),
//            result -> {
//                if (result.getResultCode() == RESULT_OK) {
//                    try {
//                        Uri uri = result.getData().getData();
//                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri); // Retrieve the image from gallery
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                    binding.image.setImageBitmap(bitmap); // Display the selected image
//                }
//            });
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        binding = ActivityMainBinding.inflate(getLayoutInflater()); // Initialize binding for the layout
//        setContentView(binding.getRoot()); // Set the layout's root view
//
//        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this); // Initialize location services client
//
//        checkLocationPermission(); // Check if location permission is granted
//
//        binding.send.setOnClickListener(v -> {
//            sendPhoto(); // Send the photo when the send button is clicked
//        });
//
//        // Set up the click listener for the "Select Image" button
//        binding.selectImage.setOnClickListener(view -> {
//            showImageSourceDialog();  // Show a dialog to choose between camera or gallery
//        });
//    }
//
//    private void sendPhoto() {
//        // Ensure the location is available before sending the image
//        if (mLocation == null) {
//            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Convert the bitmap to base64 string
//        String base64Image = bitmapToBase64(bitmap);
//
//        // Get the address from the location
//        Address address = getAddressFromLocation(mLocation.getLatitude(), mLocation.getLongitude());
//        if (address != null) {
//            // Create the image object and send it
//            Image image = new Image(base64Image, address.getCountryName(), address.getLocality());
//
//            ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
//            apiService.uploadImageAndGetRange(image).enqueue(new Callback<PredictionResponse>() {
//                @Override
//                public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
//                    if (response.isSuccessful() && response.body() != null) {
//                        PredictionResponse prediction = response.body();
//                        Log.d(TAG, "Min Range: " + prediction.getMinRange() + ", Max Range: " + prediction.getMaxRange());
//                    } else {
//                        Log.e(TAG, "Request Failed. Code: " + response.code());
//                    }
//                }
//
//                @Override
//                public void onFailure(Call<PredictionResponse> call, Throwable t) {
//                    Log.e(TAG, "Error: " + t.getMessage());
//                }
//            });
//        }
//    }
//
//
//    // This method shows a dialog asking the user to choose the image source (camera or gallery)
//    private void showImageSourceDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Select Image Source")  // Dialog title
//                .setItems(new CharSequence[]{"Take Photo", "Choose from Gallery"}, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        if (which == 0) {
//                            openCamera();  // Open the camera if "Take Photo" is selected
//                        } else {
//                            openGallery();  // Open the gallery if "Choose from Gallery" is selected
//                        }
//                    }
//                })
//                .show();
//    }
//    private void openCamera() {
//        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // Create intent to open camera
//        cameraLauncher.launch(intent); // Launch the camera intent
//    }
//
//    private void openGallery() {
//        Intent intent = new Intent();
//        intent.setType("image/*"); // Allow image selection
//        intent.setAction(Intent.ACTION_GET_CONTENT); // Action to select content
//        galleryLauncher.launch(intent); // Launch the gallery intent
//    }
//
//    public static Bitmap base64ToBitmap(String base64String) {
//        if (base64String == null) {
//            return null;
//        }
//        byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT); // Decode base64 string to bytes
//        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length); // Convert bytes to bitmap
//    }
//
//    private String bitmapToBase64(Bitmap bitmap) {
//        ByteArrayOutputStream boas = new ByteArrayOutputStream();
//        return Base64.encodeToString(boas.toByteArray(), Base64.DEFAULT); // Convert bitmap to base64 string
//    }
//
//    private void checkLocationPermission() {
//        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
//                == PackageManager.PERMISSION_GRANTED) {
//            getLastLocation(); // If permission is granted, fetch location
//        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission.ACCESS_COARSE_LOCATION)) {
//            Toast.makeText(this, "Location permission is required to get your location", Toast.LENGTH_LONG).show();
//            requestPermissionLauncher.launch(permission.ACCESS_FINE_LOCATION); // Request permission if needed
//        } else {
//            requestPermissionLauncher.launch(permission.ACCESS_FINE_LOCATION); // Request permission if not granted yet
//        }
//    }
//
//    private void getLastLocation() {
//        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
//        fusedLocationProviderClient.getLastLocation()
//                .addOnCompleteListener(task -> {
//                    if (task.isSuccessful() && task.getResult() != null) {
//                        mLocation = task.getResult();
//                    } else {
//                        Toast.makeText(MainActivity.this, "Failed to get location", Toast.LENGTH_SHORT).show();
//                    }
//                });
//    }
//
//    private Address getAddressFromLocation(double latitude, double longitude) {
//        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
//        try {
//            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
//            if (addresses != null && !addresses.isEmpty()) {
//                return addresses.get(0);
//            } else {
//                Toast.makeText(this, "No address found for the location", Toast.LENGTH_SHORT).show();
//            }
//        } catch (IOException e) {
//            Log.e(TAG, "Error getting address: " + e.getMessage(), e);
//            Toast.makeText(this, "Error getting address: " + e.getMessage(), Toast.LENGTH_LONG).show();
//        }
//        return null;
//    }
//}
package com.example.stylescannerapp;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.stylescannerapp.databinding.ActivityMainBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private ActivityMainBinding binding;
    private Bitmap bitmap;
    private Location currentLocation;
    private FusedLocationProviderClient locationClient;
    private ViewFlipper viewFlipper;

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    bitmap = (Bitmap) result.getData().getExtras().get("data");
                    binding.uploadedImage.setImageBitmap(bitmap);
                    showLoadingScreen();
                    sendPhotoToServer();
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                        binding.uploadedImage.setImageBitmap(bitmap);
                        showLoadingScreen();
                        sendPhotoToServer();
                    } catch (IOException e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        checkAndRequestPermissions();

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        viewFlipper = binding.viewFlipper;
        viewFlipper.setInAnimation(this, R.anim.slide_in_right);
        viewFlipper.setOutAnimation(this, R.anim.slide_out_left);

        checkLocationPermission();

        binding.selectImageButton.setOnClickListener(v -> openImageChooser());
        binding.tryAgainButtonResult.setOnClickListener(v -> {
            showMainScreen();
            Toast.makeText(MainActivity.this, "Let's try again!", Toast.LENGTH_SHORT).show();
        });

        binding.tryAgainButtonLoading.setOnClickListener(v -> {
            showMainScreen();
            Toast.makeText(MainActivity.this, "Let's try again!", Toast.LENGTH_SHORT).show();
        });
    }

    private void openImageChooser() {
        new AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                }).show();
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.CAMERA);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }
    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 200);
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryLauncher.launch(Intent.createChooser(intent, "Select Image"));
    }

    private void sendPhotoToServer() {
        if (currentLocation == null) {
            Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show();
            showMainScreen();
            return;
        }

        if (bitmap == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            showMainScreen();
            return;
        }

        String encodedImage = bitmapToBase64(bitmap);
        Address address = getAddressFromLocation(currentLocation);

        if (address == null) {
            Toast.makeText(this, "Couldn't get address from location", Toast.LENGTH_SHORT).show();
            showMainScreen();
            return;
        }

        Image image = new Image(encodedImage, address.getCountryName(), address.getLocality());

        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);
        apiService.uploadImageAndGetRange(image).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showResultScreen(response.body());
                } else {
                    showLoadingError("Failed to get response from server. Please try again later.");
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                Log.e("Topaz", "Connection failed: " + t.getMessage(), t);
                showLoadingError("Cannot connect right now. \n Please try again in a few minutes.");
            }
        });
    }

    private void showLoadingError(String message) {
        binding.loadingText.setText(message);
        binding.progressBar.setVisibility(View.GONE);
        binding.tryAgainButtonLoading.setVisibility(View.VISIBLE);
        viewFlipper.setDisplayedChild(1);  // Stay on loading screen
    }

    private void showMainScreen() {
        viewFlipper.setDisplayedChild(0);
        binding.loadingText.setText("What a stunning style!\n\nLet's see how much it's worth:");
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tryAgainButtonResult.setVisibility(View.GONE);
        binding.tryAgainButtonLoading.setVisibility(View.GONE);
        binding.uploadedImage.setImageBitmap(null);
        bitmap = null;
    }

    private void showLoadingScreen() {
        viewFlipper.setDisplayedChild(1);
        binding.loadingText.setText("What a stunning style!\nLet's see how much it's worth:");
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tryAgainButtonLoading.setVisibility(View.GONE);
    }


    private void showResultScreen(PredictionResponse prediction) {
        viewFlipper.setDisplayedChild(2);

        String rangeText = "Your Style Value Range:" + prediction.getMinRange() + " - " + prediction.getMaxRange();
        binding.styleValueRange.setText(rangeText);

        if (bitmap != null) {
            binding.resultImage.setImageBitmap(bitmap);
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
            } else {
                Toast.makeText(this, "Could not retrieve location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Address getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            return (addresses != null && !addresses.isEmpty()) ? addresses.get(0) : null;
        } catch (IOException e) {
            Log.e("Topaz", "Geocoder failed", e);
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permissions", permissions[i] + " granted");
                } else {
                    Log.e("Permissions", permissions[i] + " denied");
                }
            }
        } else if (requestCode == 200) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
