package com.example.machinelearningappandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    public static final int PIXELS_IN_IMAGE = 512 * 512;
    public static final float THRESHOLD = 0.8f;
    public static final int NUM_OUTPUT_MASKS = 18;
    public static final String[] MASK_NAMES = new String[] {
            "Skin", "Nose", "Glasses", "Left Eye", "Right Eye", "Left Brow", "Right Brow", "Left Ear", "Right Ear",
            "Mouth", "Upper Lip", "Lower Lip", "Hair", "Hat", "Ear Ring", "Neck Lower", "Neck", "Cloth"
    };

    private Bitmap inputImageBitmap = null;
    private int[] imageOutputs;
    private int currentMaskIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Load PyTorch Model into Module and input image as bitmap
        Module module = null;
        try {
            module = LiteModuleLoader.load(assetFilePath(this,
                    "TestModel.ptl"));
            inputImageBitmap = BitmapFactory.decodeStream(getAssets().open("testImage.jpg"));
        } catch (IOException e) {
            Log.e("MachineLearning", "Error loading Assets", e);
            finish();
        }

        //Setup UI Components
        ImageView imageView = findViewById(R.id.imageView);
        imageView.setImageBitmap(inputImageBitmap);

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
                //Log.println(Log.ERROR, "Loading Image", "Found input that passed threshold");
                imageOutputs[i] = 0xFFFFFFFF;
            } else {
                imageOutputs[i] = 0xFF0000FF;
            }
        }

        //Add functionality to the restart and segment buttons
        final Button restartButton = findViewById(R.id.restartButton);
        final Button segmentButton = findViewById(R.id.segmentButton);
        final TextView imageNameText = findViewById(R.id.imageName);

        restartButton.setOnClickListener(v -> {
            imageView.setImageBitmap(inputImageBitmap);
            currentMaskIndex = 0;
        });

        segmentButton.setOnClickListener(v -> {
            imageView.setImageBitmap(getSegmentationMask());
            segmentButton.setText(getString(R.string.view_next));
            imageNameText.setText("Viewing Mask: " + MASK_NAMES[currentMaskIndex]);
            currentMaskIndex++;
            if(currentMaskIndex == NUM_OUTPUT_MASKS) {
                currentMaskIndex = 0;
            }
        });
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