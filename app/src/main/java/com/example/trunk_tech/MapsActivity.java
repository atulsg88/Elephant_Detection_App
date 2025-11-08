package com.example.trunk_tech;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private TextView timestampText;
    private String systemId;

    // CHANGED: The reference now points to a specific system path
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        timestampText = findViewById(R.id.timestampText);

        // Get the system ID from the intent that started this activity
        if (getIntent().getExtras() != null) {
            systemId = getIntent().getExtras().getString("system_id");
        }

        // If a systemId was passed, set up the database reference
        if (systemId != null && !systemId.isEmpty()) {
            // CHANGED: Point the reference directly to the correct system's data
            dbRef = FirebaseDatabase.getInstance().getReference("detection_systems").child(systemId);
        } else {
            // Handle the case where no system ID was provided
            Toast.makeText(this, "Error: No System ID provided.", Toast.LENGTH_LONG).show();
            finish(); // Close the activity
            return;
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        Toast.makeText(this, "Fetching latest location from database...", Toast.LENGTH_SHORT).show();
        fetchLatestLocationFromFirebase();
    }

    // CHANGED: This function now reads data from the specific system's path
    private void fetchLatestLocationFromFirebase() {
        if (dbRef == null) return;

        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double latitude = snapshot.child("location/latitude").getValue(Double.class);
                    Double longitude = snapshot.child("location/longitude").getValue(Double.class);
                    String time = snapshot.child("last_updated_timestamp").getValue(String.class);

                    if (latitude != null && longitude != null) {
                        updateMapLocation(latitude, longitude, time);
                    } else {
                        Toast.makeText(MapsActivity.this, "Location data not found in database for this system.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MapsActivity.this, "No detection data available for this system.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, "Failed to read database: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateMapLocation(double latitude, double longitude, String time) {
        String displayTime = (time != null) ? "Detected at: " + time : "Time not available";
        String systemTitle = (systemId != null) ? "System: " + systemId : "Elephant Detected!";

        // Create a LatLng object for the elephant's location
        LatLng elephantLocation = new LatLng(latitude, longitude);

        mMap.clear(); // Clear old markers

        // Add a new marker at the location
        mMap.addMarker(new MarkerOptions()
                .position(elephantLocation)
                .title(systemTitle)
                .snippet("Time: " + (time != null ? time : "N/A")));

        // Move the camera to the marker and zoom in
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(elephantLocation, 15f));

        // Update the TextView
        timestampText.setText(displayTime);
    }
}
