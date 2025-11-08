package com.example.trunk_tech;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText editTextUserInput;
    private Button buttonAction;
    private ProgressBar progressBar;
    private RadioGroup resetRadioGroup;
    private RadioButton radioEmail, radioPhone;
    private TextInputLayout userInputLayout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_pass);

        mAuth = FirebaseAuth.getInstance();
        editTextUserInput = findViewById(R.id.editTextUserInput);
        buttonAction = findViewById(R.id.buttonAction);
        progressBar = findViewById(R.id.progressBar);
        resetRadioGroup = findViewById(R.id.resetRadioGroup);
        radioEmail = findViewById(R.id.radioEmail);
        radioPhone = findViewById(R.id.radioPhone);
        userInputLayout = findViewById(R.id.userInputLayout);

        resetRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioEmail) {
                userInputLayout.setHint("Email Address");
                editTextUserInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                buttonAction.setText("Send Reset Link");
            } else if (checkedId == R.id.radioPhone) {
                userInputLayout.setHint("Mobile Number");
                editTextUserInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                buttonAction.setText("Send OTP");
            }
        });

        buttonAction.setOnClickListener(v -> {
            if (radioEmail.isChecked()) {
                sendPasswordResetEmail();
            } else {
                // Phone OTP logic is more complex and requires server-side setup for production.
                // For this example, we will show a placeholder message.
                Toast.makeText(this, "Phone OTP reset is a premium feature. Please use Email reset.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendPasswordResetEmail() {
        String email = editTextUserInput.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextUserInput.setError("Enter a valid email address");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                        finish(); // Go back to previous screen
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Failed to send reset email: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
