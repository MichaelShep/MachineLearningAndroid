package com.example.machinelearningappandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

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
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final int CLASSNUM = 21;
    public static final int DOG = 12;
    public static final int PERSON = 15;
    public static final int SHEEP = 17;

    private Bitmap inputImageBitmap = null;
    private Bitmap outputSegmentationMask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Load PyTorch Model into Module and input image as bitmap
        Module module = null;
        try {
            module = LiteModuleLoader.load(assetFilePath(this,
                    "deeplabv3_scripted.ptl"));
            inputImageBitmap = BitmapFactory.decodeStream(getAssets().open("deeplab.jpg"));
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
        final float[] inputs = inputTensor.getDataAsFloatArray();

        //Perform forward pass through the network
        Map<String, IValue> outTensors = module.forward(IValue.from(inputTensor)).toDictStringKey();

        //Get the output data from the model
        final Tensor outputTensor = outTensors.get("out").toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        int width = inputImageBitmap.getWidth();
        int height = inputImageBitmap.getHeight();

        //Convert the output data into the correct image data for the segmentation
        int[] intValues = new int[width * height];
        for (int j = 0; j < width; j++) {
            for (int k = 0; k < height; k++) {
                int maxi = 0, maxj = 0, maxk = 0;
                double maxnum = -100000.0;

                //Perform input processing for each pixel
                for (int i= 0; i < CLASSNUM; i++) {
                    if (outputs[i*(width*height) + j*width + k] > maxnum) {
                        maxnum = outputs[i*(width*height) + j*width + k];
                        maxi = i; maxj = j; maxk = k;
                    }
                }

                //Work out what colour each pixel should be based on processing
                if (maxi == PERSON) {
                    intValues[maxj*width + maxk] = 0xFFFF000;
                } else if(maxi == DOG) {
                    intValues[maxj*width + maxk] = 0xFF00FF00;
                } else if(maxi == SHEEP) {
                    intValues[maxj*width + maxk] = 0xFF0000FF;
                } else {
                    intValues[maxj*width + maxk] = 0xFF000000;
                }
            }
        }

        //Convert the image segmentation information into an actual image
        Bitmap bmpSegmentation = Bitmap.createScaledBitmap(inputImageBitmap, width, height, true);
        outputSegmentationMask = bmpSegmentation.copy(bmpSegmentation.getConfig(), true);
        outputSegmentationMask.setPixels(intValues, 0, outputSegmentationMask.getWidth(),
                0, 0, outputSegmentationMask.getWidth(), outputSegmentationMask.getHeight());

        //Add functionality to the restart and segment buttons
        final Button restartButton = findViewById(R.id.restartButton);
        final Button segmentButton = findViewById(R.id.segmentButton);

        restartButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                imageView.setImageBitmap(inputImageBitmap);
            }
        });

        segmentButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                imageView.setImageBitmap(outputSegmentationMask);
            }
        });
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