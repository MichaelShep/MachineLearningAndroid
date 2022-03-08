package com.example.machinelearningappandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Mac;

public class MainActivity extends AppCompatActivity {
    public static final int PIXELS_IN_IMAGE = 512 * 512;
    public static final String APP_TAG = "MachineLearningApp";
    public static final float SEGMENTATION_THRESHOLD = 0.8f;
    public static final float ATTRIBUTES_THRESHOLD = 0.5f;
    public static final int NUM_OUTPUT_MASKS = 18;
    public static final String[] MASK_NAMES = new String[] {
            "Skin", "Nose", "Glasses", "Left Eye", "Right Eye", "Left Brow", "Right Brow", "Left Ear", "Right Ear",
            "Mouth", "Upper Lip", "Lower Lip", "Hair", "Hat", "Ear Ring", "Neck Lower", "Neck", "Cloth"
    };
    public static final String[] ATTRIBUTES = new String[] {
            "5 O'clock Shadow", "Arched Eyebrows", "Attractive", "Bags Under Eyes", "Bald", "Bangs", "Big Lips",
            "Big Nose", "Black Hair", "Blond Hair", "Blurry", "Brown Hair", "Bushy Eyebrows", "Chubby", "Double Chin",
            "Eyeglasses", "Goatee", "Gray Hair", "Heavy Makeup", "High Cheekbones", "Male", "Mouth Slightly Open",
            "Mustache", "Narrow Eyes", "No Beard", "Oval Face", "Pale Skin", "Pointy Nose", "Receding Hairline",
            "Rosy Cheeks", "Sideburns", "Smiling", "Straight Hair", "Wavy Hair", "Wearing Earrings", "Wearing Hat",
            "Wearing Lipstick", "Wearing Necklace", "Wearing Necktie", "Young"
    };

    private Bitmap inputImageBitmap = null;
    private Module module = null;
    private int[] imageOutputs;
    private ArrayList<String> multiModelAttributes = new ArrayList<>();
    private boolean multiModelSegmentationFinished = false;
    private int currentMaskIndex = 0;
    private ModelType modelType = ModelType.SEGMENTATION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Add back button in appBar so that we can go back to camera view
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Get the file name of the image saved in the CameraView
        Bundle extras = getIntent().getExtras();
        String imagePath = "";
        boolean fromNewApi = false;
        if (extras != null) {
            imagePath = extras.getString("imagePath");
            fromNewApi = extras.getBoolean("fromNewApi");
            String modelTypeString = extras.getString("modelType");
            this.modelType = ModelType.valueOf(modelTypeString);
        }else {
            Log.e(APP_TAG, "No image passed to activity");
            finish();
        }
        inputImageBitmap = loadImageFromStorage(imagePath);

        //Set the title of the page based on which model we are using
        switch(this.modelType) {
            case SEGMENTATION:
                setTitle("Run Segmentation Model");
                break;
            case ATTRIBUTES:
                setTitle("Run Attributes Model");
                break;
            case JOINT:
                setTitle("Run Joint Model");
        }

        if (!fromNewApi) {
            //Rotate image 270 degrees so it is upright
            Matrix matrix = new Matrix();
            matrix.postRotate(270);
            inputImageBitmap = Bitmap.createBitmap(inputImageBitmap, 0, 0,
                    inputImageBitmap.getWidth(), inputImageBitmap.getHeight(), matrix,
                    true);
        }

        //Load PyTorch Model into Module and input image as bitmap
        try {
            switch(this.modelType){
                case SEGMENTATION:
                    module = LiteModuleLoader.load(assetFilePath(this,
                            "segmentation_model.ptl"));
                    break;
                case ATTRIBUTES:
                    module = LiteModuleLoader.load(assetFilePath(this,
                            "attributes_model.ptl"));
                    break;
                case JOINT:
                    module = LiteModuleLoader.load(assetFilePath(this,
                            "multi_model.ptl"));
            }
        } catch (IOException e) {
            Log.e(APP_TAG, "Error loading PyTorch module", e);
            finish();
        }

        //Setup UI Components
        ImageView imageView = findViewById(R.id.imageView);
        imageView.setImageBitmap(inputImageBitmap);

        //Add functionality to the restart and segment buttons
        final Button restartButton = findViewById(R.id.restartButton);
        final Button performButton = findViewById(R.id.performButton);
        final TextView imageNameText = findViewById(R.id.imageName);

        restartButton.setOnClickListener(v -> {
            performButton.setEnabled(true);
            performButton.setText(R.string.perform_model);
            imageView.setImageBitmap(inputImageBitmap);
            currentMaskIndex = 0;
        });

        performButton.setOnClickListener(v -> {
            if (this.modelType == ModelType.SEGMENTATION || (this.modelType == ModelType.JOINT && !this.multiModelSegmentationFinished)) {
                if (currentMaskIndex == 0) {
                    Toast.makeText(getBaseContext(), R.string.performing, Toast.LENGTH_SHORT).show();
                    if (this.modelType == ModelType.SEGMENTATION) {
                        runSegmentationModel();
                    } else {
                        runJointModel();
                    }
                }
                imageView.setImageBitmap(getSegmentationMask());
                performButton.setText(getString(R.string.view_next));
                imageNameText.setText("Viewing Mask: " + MASK_NAMES[currentMaskIndex]);
                currentMaskIndex++;
                if (currentMaskIndex == NUM_OUTPUT_MASKS) {
                    if (this.modelType == ModelType.SEGMENTATION) {
                        performButton.setEnabled(false);
                    }
                    else {
                        performButton.setText("View Attributes Information");
                        this.multiModelSegmentationFinished = true;
                    }
                }
            }
            else if(this.modelType == ModelType.JOINT && this.multiModelSegmentationFinished) {
                Intent intent = new Intent(MainActivity.this, AttributesActivity.class);
                intent.putExtra("faceAttributes", this.multiModelAttributes);
                startActivity(intent);
            }
            else {
                runAttributesModel();
            }
        });
    }

    //Loads a saved image from storage
    private Bitmap loadImageFromStorage(String path) {
        try {
            InputStream stream = getContentResolver().openInputStream(Uri.parse(path));
            Bitmap image = BitmapFactory.decodeStream(stream);
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
        getSegmentationOutput(outTensor);
    }

    //Runs the attributes model on the input image
    private void runAttributesModel() {
        //Set up the input tensor
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(inputImageBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        //Perform forward pass through the network
        final Tensor outTensor = module.forward(IValue.from(inputTensor)).toTensor();
        ArrayList<String> faceAttributes = this.getAttributesOutput(outTensor);

        Intent intent = new Intent(MainActivity.this, AttributesActivity.class);
        intent.putExtra("faceAttributes", faceAttributes);
        startActivity(intent);
    }

    private void runJointModel() {
        //Set up the input tensor
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(inputImageBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        //Perform forward pass through the network
        //Get both the segmentation and attributes results from the joint model
        final IValue[] output = module.forward(IValue.from(inputTensor)).toTuple();
        final Tensor segmentationOutput = output[0].toTensor();
        final Tensor attributesOutput = output[1].toTensor();

        //Get both segmentation and attributes outputs
        getSegmentationOutput(segmentationOutput);
        this.multiModelAttributes = this.getAttributesOutput(attributesOutput);
    }

    private void getSegmentationOutput(Tensor segmentationTensor) {
        final float[] segmentationValues = segmentationTensor.getDataAsFloatArray();
        imageOutputs = new int[segmentationValues.length];
        for (int i = 0; i < segmentationValues.length; i++) {
            if (segmentationValues[i] > SEGMENTATION_THRESHOLD) {
                imageOutputs[i] = 0xFFFFFFFF;
            } else {
                imageOutputs[i] = 0xFF0000FF;
            }
        }
    }

    //Converts the tensor output of the attributes model into a list of string containing the attributes mappings
    private ArrayList<String> getAttributesOutput(Tensor attributesTensor) {
        ArrayList<String> outputs = new ArrayList<String>();
        final float[] attributesValues = attributesTensor.getDataAsFloatArray();
        for (int i = 0; i < attributesValues.length; i++) {
            if (attributesValues[i] > ATTRIBUTES_THRESHOLD) {
                outputs.add(this.ATTRIBUTES[i]);
            }
        }
        return outputs;
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