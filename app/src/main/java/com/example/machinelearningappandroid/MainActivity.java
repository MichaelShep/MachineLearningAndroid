package com.example.machinelearningappandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Mac;

public class MainActivity extends AppCompatActivity {
    public static final int PIXELS_IN_IMAGE = 512 * 512;
    public static final String APP_TAG = "MachineLearningApp";
    public static final float THRESHOLD = 0.8f;
    public static final int NUM_OUTPUT_MASKS = 18;
    public static final String[] MASK_NAMES = new String[] {
            "Skin", "Nose", "Glasses", "Left Eye", "Right Eye", "Left Brow", "Right Brow", "Left Ear", "Right Ear",
            "Mouth", "Upper Lip", "Lower Lip", "Hair", "Hat", "Ear Ring", "Neck Lower", "Neck", "Cloth"
    };

    private Bitmap inputImageBitmap = null;
    private Module module = null;
    private int[] imageOutputs;
    private int currentMaskIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Run Segmentation Model");

        //Add back button in appBar so that we can go back to camera view
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Get the file name of the image saved in the CameraView
        Bundle extras = getIntent().getExtras();
        String imagePath = "";
        boolean fromNewApi = false;
        if (extras != null) {
            imagePath = extras.getString("imagePath");
            fromNewApi = extras.getBoolean("fromNewApi");
        }else {
            Log.e(APP_TAG, "No image passed to activity");
            finish();
        }
        inputImageBitmap = loadImageFromStorage(imagePath, fromNewApi);

        //Load PyTorch Model into Module and input image as bitmap
        try {
            module = LiteModuleLoader.load(assetFilePath(this,
                    "segmentation_model.ptl"));
        } catch (IOException e) {
            Log.e(APP_TAG, "Error loading PyTorch module", e);
            finish();
        }

        //Setup UI Components
        ImageView imageView = findViewById(R.id.imageView);
        imageView.setImageBitmap(inputImageBitmap);

        //Add functionality to the restart and segment buttons
        final Button restartButton = findViewById(R.id.restartButton);
        final Button segmentButton = findViewById(R.id.segmentButton);
        final TextView imageNameText = findViewById(R.id.imageName);

        restartButton.setOnClickListener(v -> {
            segmentButton.setEnabled(true);
            segmentButton.setText(R.string.segment);
            imageView.setImageBitmap(inputImageBitmap);
            currentMaskIndex = 0;
        });

        segmentButton.setOnClickListener(v -> {
            if (currentMaskIndex == 0) {
                Toast.makeText(getBaseContext(), "Performing Segmentation...", Toast.LENGTH_SHORT).show();
                runSegmentationModel();
            }
            imageView.setImageBitmap(getSegmentationMask());
            segmentButton.setText(getString(R.string.view_next));
            imageNameText.setText("Viewing Mask: " + MASK_NAMES[currentMaskIndex]);
            currentMaskIndex++;
            if(currentMaskIndex == NUM_OUTPUT_MASKS) {
                segmentButton.setEnabled(false);
            }
        });
    }

    //Loads a saved image from storage
    private Bitmap loadImageFromStorage(String path, boolean fromNewApi) {
        try {
            Bitmap image = null;
            if (fromNewApi) {
                InputStream stream = getContentResolver().openInputStream(Uri.parse(path));
                image = BitmapFactory.decodeStream(stream);
            } else {
                File f = new File(path, CameraActivity.SAVED_IMAGE_NAME);
                image = BitmapFactory.decodeStream(new FileInputStream(f));
            }
            return image;
        } catch (Exception e) {
            Log.e(APP_TAG, "Error loading image from storage");
            e.printStackTrace();
            finish();
        }

        return null;
    }


    //Runs the segmentation model on the input image
    private void runSegmentationModel() {
        //Sets up input tensors
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(inputImageBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        //Perform forward pass through the network
        final Tensor outTensor = module.forward(IValue.from(inputTensor)).toTensor();

        //Process output into correct image format
        final float[] outputValues = outTensor.getDataAsFloatArray();
        imageOutputs = new int[outputValues.length];
        for (int i = 0; i < outputValues.length; i++) {
            if (outputValues[i] > THRESHOLD) {
                imageOutputs[i] = 0xFFFFFFFF;
            } else {
                imageOutputs[i] = 0xFF0000FF;
            }
        }
    }

    //Gets one of the output masks for the input image based on the current mask index variable
    private Bitmap getSegmentationMask() {
        if (currentMaskIndex >= NUM_OUTPUT_MASKS) {
            return null;
        }

        //Get the output mask at the current index
        Bitmap bmpSegmentation = Bitmap.createScaledBitmap(inputImageBitmap, inputImageBitmap.getWidth(), inputImageBitmap.getHeight(), true);
        Bitmap outputSegmentationMask = bmpSegmentation.copy(bmpSegmentation.getConfig(), true);
        outputSegmentationMask.setPixels(imageOutputs, PIXELS_IN_IMAGE * currentMaskIndex, outputSegmentationMask.getWidth(),
                0, 0, outputSegmentationMask.getWidth(), outputSegmentationMask.getHeight());

        return outputSegmentationMask;
    }

    //Creates a new file in the /files app directory and copies all the data from our asset into it
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                 byte[] buffer = new byte[4 * 1024];
                 int read;
                 while((read = is.read(buffer)) != -1) {
                     os.write(buffer, 0, read);
                 }
                 os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}