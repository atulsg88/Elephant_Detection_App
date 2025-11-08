package com.example.trunk_tech;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.trunk_tech.databinding.FragmentStatusBinding;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class StatusFragment extends Fragment {

    private enum UiState { LOADING, ELEPHANT_DETECTED, ALL_CLEAR, NO_SYSTEMS }
    private FragmentStatusBinding binding;
    private DatabaseReference dbRef;
    private ValueEventListener dbListener;
    private String currentLat, currentLon, currentTimestamp, currentSystemId;
    private static final int SMS_PERMISSION_CODE = 100;
    // IMPORTANT: Add the phone numbers you want to send alerts to
    private static final String[] PHONE_NUMBERS = {"7385484433", "8767758048", "8459897317","9022671135"};
    private boolean smsSentForCurrentAlert = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(getContext(), "Notifications permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Notifications will not be shown.", Toast.LENGTH_SHORT).show();
                }
            });


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // CHANGED: The reference now points to the parent "detection_systems" node
        dbRef = FirebaseDatabase.getInstance().getReference("detection_systems");

        binding.showMapButton.setOnClickListener(v -> {
            if (currentLat != null && currentLon != null) {
                Intent intent = new Intent(requireActivity(), MapsActivity.class);
                // Pass the system ID to the MapsActivity so it knows which system to query
                intent.putExtra("system_id", currentSystemId);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Location data is not available.", Toast.LENGTH_SHORT).show();
            }
        });

        binding.refreshButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Refreshing data...", Toast.LENGTH_SHORT).show();
            if (dbListener != null) {
                dbRef.removeEventListener(dbListener);
            }
            setupDatabaseListener();
        });

        askNotificationPermission();
    }

    @Override
    public void onStart() {
        super.onStart();
        setupDatabaseListener();
    }

    private void setupDatabaseListener() {
        updateUiForState(UiState.LOADING, null, null, null, null, null);
        dbListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // CHANGED: The logic is updated to handle multiple detection systems
                if (snapshot.exists() && snapshot.hasChildren()) {
                    boolean anElephantWasDetected = false;
                    // Loop through all systems (e.g., all Raspberry Pi detectors)
                    for (DataSnapshot systemSnapshot : snapshot.getChildren()) {
                        String status = systemSnapshot.child("detection_status").getValue(String.class);

                        // If we find any system that has detected an elephant, we update the UI
                        if ("Elephant Detected".equals(status)) {
                            String timestamp = systemSnapshot.child("last_updated_timestamp").getValue(String.class);
                            Double lat = systemSnapshot.child("location/latitude").getValue(Double.class);
                            Double lon = systemSnapshot.child("location/longitude").getValue(Double.class);
                            String systemId = systemSnapshot.child("system_id").getValue(String.class);

                            // Store the details of the first detected elephant alert
                            currentLat = (lat != null) ? String.valueOf(lat) : null;
                            currentLon = (lon != null) ? String.valueOf(lon) : null;
                            currentTimestamp = timestamp;
                            currentSystemId = systemId;

                            updateUiForState(UiState.ELEPHANT_DETECTED, status, lat, lon, timestamp, systemId);

                            // Check if we need to send an SMS for this specific alert
                            if (!smsSentForCurrentAlert) {
                                checkAndSendSms(currentLat, currentLon);
                            }
                            anElephantWasDetected = true;
                            break; // Exit the loop since we found an active alert
                        }
                    }

                    // If we looped through all systems and none had an "Elephant Detected" status
                    if (!anElephantWasDetected) {
                        // We can just show the status of the first system for general info
                        DataSnapshot firstSystem = snapshot.getChildren().iterator().next();
                        String status = firstSystem.child("detection_status").getValue(String.class);
                        String timestamp = firstSystem.child("last_updated_timestamp").getValue(String.class);
                        String systemId = firstSystem.child("system_id").getValue(String.class);
                        updateUiForState(UiState.ALL_CLEAR, status, null, null, timestamp, systemId);
                        smsSentForCurrentAlert = false; // Reset SMS flag
                    }
                } else {
                    // This case handles when there are no systems reporting data
                    updateUiForState(UiState.NO_SYSTEMS, "No active systems", null, null, null, null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                updateUiForState(UiState.ALL_CLEAR, "Database Error", null, null, null, null);
            }
        };
        dbRef.addValueEventListener(dbListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (dbListener != null) {
            dbRef.removeEventListener(dbListener);
        }
    }

    private void updateUiForState(UiState state, String status, Double lat, Double lon, String timestamp, String systemId) {
        if (binding == null) return;
        binding.progressBar.setVisibility(View.GONE);

        String systemText = (systemId != null) ? "System ID: " + systemId : "System ID: --";
        binding.systemIdText.setText(systemText);

        switch (state) {
            case LOADING:
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.detectionStatusText.setText("Connecting to systems...");
                binding.latitudeText.setText("Latitude: --");
                binding.longitudeText.setText("Longitude: --");
                binding.timestampText.setText("Last Updated: --");
                binding.showMapButton.setVisibility(View.GONE);
                break;
            case ELEPHANT_DETECTED:
                binding.detectionStatusText.setText(status);
                binding.latitudeText.setText("Latitude: " + (lat != null ? lat : "--"));
                binding.longitudeText.setText("Longitude: " + (lon != null ? lon : "--"));
                binding.timestampText.setText("Time: " + (timestamp != null ? timestamp : "--"));
                binding.showMapButton.setVisibility(View.VISIBLE);
                break;
            case ALL_CLEAR:
            case NO_SYSTEMS:
                binding.detectionStatusText.setText(status != null ? status : "All Clear");
                binding.latitudeText.setText("Latitude: --");
                binding.longitudeText.setText("Longitude: --");
                binding.timestampText.setText("Time: " + (timestamp != null ? timestamp : "--"));
                binding.showMapButton.setVisibility(View.GONE);
                break;
        }
    }

    private void checkAndSendSms(String lat, String lon) {
        if (lat == null || lon == null) return;
        if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            String mapLink = "http://maps.google.com/?q=" + lat + "," + lon;
            String message = "Alert: Elephant detected. View on map: " + mapLink;
            sendSms(message);
        } else {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
    }

    private void sendSms(String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            for (String phoneNumber : PHONE_NUMBERS) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Log.d("SMS_SEND", "SMS sent to " + phoneNumber);
            }
            Toast.makeText(getContext(), "SMS Alerts Sent!", Toast.LENGTH_LONG).show();
            smsSentForCurrentAlert = true;
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to send SMS.", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentLat != null && currentLon != null) {
                    String mapLink = "http://maps.google.com/?q=" + currentLat + "," + currentLon;
                    String message = "Alert: Elephant detected. View on map: " + mapLink;
                    sendSms(message);
                }
            } else {
                Toast.makeText(getContext(), "SMS Permission Denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
