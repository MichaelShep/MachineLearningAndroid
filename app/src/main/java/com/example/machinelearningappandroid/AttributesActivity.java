package com.example.machinelearningappandroid;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.ArrayList;

public class AttributesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attributes);

        //Add back button in appBar so that we can go back to main view
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Attributes Model Output");

        Bundle extras = getIntent().getExtras();
        ArrayList<String> faceAttributes = new ArrayList<String>();
        if (extras != null) {
            faceAttributes = (ArrayList<String>) extras.get("faceAttributes");
        }

        TextView attributesList = findViewById(R.id.attributes_list_text);

        String attributesText = "";

        for (String attribute: faceAttributes) {
            attributesText += attribute + "\n\n";
        }

        attributesList.setText(attributesText);
    }
}