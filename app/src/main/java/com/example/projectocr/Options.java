package com.example.projectocr;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class Options extends AppCompatActivity {

    private EditText baseWeightInput, wrongWeightInput, correctWeightInput;
    private EditText lifeCountInput, optionCountInput, timerInput;
    private Button saveButton;

    private static final String PREFS_NAME = "WeightPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.options);

        baseWeightInput = findViewById(R.id.baseWeightInput);
        wrongWeightInput = findViewById(R.id.wrongWeightInput);
        correctWeightInput = findViewById(R.id.correctWeightInput);
        lifeCountInput = findViewById(R.id.lifeCountInput);
        optionCountInput = findViewById(R.id.optionCountInput);
        timerInput = findViewById(R.id.timerInput);
        saveButton = findViewById(R.id.saveButton);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());
    }

    // 가중치 변수 불러오기
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int base = prefs.getInt("baseWeight", 10);
        int wrong = prefs.getInt("wrongWeight", 3);
        int correct = prefs.getInt("correctWeight", 1);
        int lifeCount = prefs.getInt("lifeCount", 3);
        int optionCount = prefs.getInt("optionCount", 4);
        int timerSeconds = prefs.getInt("timerSeconds", 5);

        baseWeightInput.setText(String.valueOf(base));
        wrongWeightInput.setText(String.valueOf(wrong));
        correctWeightInput.setText(String.valueOf(correct));
        lifeCountInput.setText(String.valueOf(lifeCount));
        optionCountInput.setText(String.valueOf(optionCount));
        timerInput.setText(String.valueOf(timerSeconds));
    }

    // 설정 저장
    private void saveSettings() {
        try {
            int base = Integer.parseInt(baseWeightInput.getText().toString());
            int wrong = Integer.parseInt(wrongWeightInput.getText().toString());
            int correct = Integer.parseInt(correctWeightInput.getText().toString());
            int lifeCount = Integer.parseInt(lifeCountInput.getText().toString());
            int optionCount = Integer.parseInt(optionCountInput.getText().toString());
            int timerSeconds = Integer.parseInt(timerInput.getText().toString());

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("baseWeight", base);
            editor.putInt("wrongWeight", wrong);
            editor.putInt("correctWeight", correct);
            editor.putInt("lifeCount", lifeCount);
            editor.putInt("optionCount", optionCount);
            editor.putInt("timerSeconds", timerSeconds);
            editor.apply();

            Toast.makeText(this, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "모든 값을 올바르게 입력하세요.", Toast.LENGTH_SHORT).show();
        }
    }
}
