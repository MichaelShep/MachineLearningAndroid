package com.example.machinelearningappandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

public class SelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection);
        setTitle("Select Machine Learning Model");

        Button segmentationSelectionButton = findViewById(R.id.segmentation_selection_button);
        Button attributesSelectionButton = findViewById(R.id.attributes_selection_button);
        Button jointSelectionButton = findViewById(R.id.joint_selection_button);

        segmentationSelectionButton.setOnClickListener(v -> {
            //Setup intent to take us to the Segmentation model activity
            Intent intent = new Intent(SelectionActivity.this, CameraActivity.class);
            startActivity(intent);
        });

        attributesSelectionButton.setOnClickListener(v -> Log.println(Log.INFO, "MachineLearningApp", "Attributes Model Selected"));

        jointSelectionButton.setOnClickListener(v -> Log.println(Log.INFO, "MachineLearningApp", "Joint Model Selected"));
    }
}