package com.yamasani.colordetector;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.widget.TextView;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements OnRequestPermissionsResultCallback {

    // view to hold the camera preview
    private PreviewView mCameraPreviewView;
    // view to show color name
    private TextView mColorNameTextView;
    // camera provider future
    private ListenableFuture<ProcessCameraProvider> mCameraProviderFuture;
    // camera provider
    private ProcessCameraProvider mCameraProvider;
    // request code for camera permission
    private static final int PERMISSION_REQUEST_CODE = 1;
    // text to speech engine
    private TextToSpeech mTts;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // get reference to the TextView that shows color name
        mColorNameTextView = findViewById(R.id.colorNameView);
        mCameraPreviewView = findViewById(R.id.previewView);
        // initialize text to speech engine
        mTts = new TextToSpeech(this, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {

            if (mCameraProviderFuture == null) {
                startDetecting();
            }
        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Initialize the camera provider and bind it to the view
     */
    private void startDetecting() {
        // implementing a camerax preview
        mCameraProviderFuture = ProcessCameraProvider.getInstance(this);
        mCameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    mCameraProvider = mCameraProviderFuture.get();
                    bindPreview(mCameraProvider);
                } catch (ExecutionException | InterruptedException e) {

                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                    @NonNull String[] permissions,
                                    @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // if permission is granted, start detecting; else, exit the app
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDetecting();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        if (mCameraProvider != null) {
            mCameraProvider.unbindAll();
            mCameraProvider = null;
            mCameraProviderFuture = null;
        }
        super.onPause();
    }

    /**
     * Set up the camera parameters and attach to the view and analyzer
     * @param cameraProvider
     */
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(mCameraPreviewView.getSurfaceProvider());

        Executor executor = Executors.newSingleThreadExecutor();
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                processImage(imageProxy);
                imageProxy.close();
            }
        });

        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis);
    }

    /**
     * Analyzes the camera image, averages the central color, and speaks out the color name
     * @param imageProxy the image from the camera
     */
    private void processImage(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image image = imageProxy.getImage();
        if (image == null) {
            return;
        }
        // Get the central color of the image
        int centralColor = ColorMapper.getCentralColor(image);

        System.out.println("Color = " + Integer.toHexString(centralColor));

        // Convert the color to a color name
        String colorName = new ColorMapper().getColorName(centralColor);

        // change background color of TextView
        mColorNameTextView.post(() -> {
            mColorNameTextView.setBackgroundColor(centralColor);
            mColorNameTextView.setText(colorName);
        });

        // Speak the color name
        if (!mTts.isSpeaking()) {
            mTts.speak(colorName, TextToSpeech.QUEUE_FLUSH, null, "1");
        }
        // Wait for some time before detecting the color again
        try {
            Thread.sleep(2000);
        } catch(InterruptedException ie) { }
    }
}

