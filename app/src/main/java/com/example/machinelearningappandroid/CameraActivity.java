package com.example.machinelearningappandroid;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.yashoid.instacropper.InstaCropperActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    public static final String SAVED_IMAGE_NAME = "face.jpg";

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture = null;
    private final int cameraRequestCode = 10;
    private ExecutorService cameraExecutor;

    private ActivityResultLauncher<Intent> cropActivityResultLauncher;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private String modelTypeString = "";

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        setTitle("Take Photo of Face");

        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            modelTypeString = extras.getString("modelType");
        }

        //Add back button in appBar so that we can go back to selection view
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(this.getDisplay().getRotation()).build();

        //Ensure we have the correct permissions for camera and then start the camera
        if (arePermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    cameraRequestCode);
        }

        final Button cameraCaptureButton = findViewById(R.id.camera_capture_button);
        cameraCaptureButton.setOnClickListener(v -> takePhoto());

        //Make switch button allow the user to select which camera they want to use
        final Button switchCameraButton = findViewById(R.id.switch_button);
        switchCameraButton.setOnClickListener(v -> {
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            }
            startCamera();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();

        //Setup code that will be run when the crop intent is finished running
        cropActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent resultIntent = result.getData();
                        Intent intent = new Intent(CameraActivity.this, MainActivity.class);
                        intent.putExtra("imagePath", resultIntent.getData().toString());
                        intent.putExtra("modelType", this.modelTypeString);
                        if (Build.VERSION.SDK_INT >= 29) {
                            intent.putExtra("fromNewApi", true);
                        } else {
                            intent.putExtra("fromNewApi", false);
                        }
                        startActivity(intent);
                    }
                }
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == cameraRequestCode) {
            if (arePermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    //Starts and sets up the camera
    private void startCamera() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                //Unbind all previous binding before setting up new one
                cameraProvider.unbindAll();
                bindCameraToPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    //Adds a camera object into the preview
    private void bindCameraToPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        PreviewView previewView = findViewById(R.id.previewView);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview);
    }

    //Called whenever the Capture button is pressed
    private void takePhoto() {
        String filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS";
        String fileName = new SimpleDateFormat(filenameFormat, Locale.ENGLISH)
                .format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                String message = "Photo Capture Succeeded: " + fileName + ".jpeg";
                Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();

                Intent intent = InstaCropperActivity.getIntent(getBaseContext(), outputFileResults.getSavedUri(),
                        outputFileResults.getSavedUri(), View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.EXACTLY), 100);
                cropActivityResultLauncher.launch(intent);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(getBaseContext(), "Image Capture Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    //Checks that the user has given us permission to use the camera
    private boolean arePermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}