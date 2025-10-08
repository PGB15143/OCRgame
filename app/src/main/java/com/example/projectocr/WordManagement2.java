package com.example.projectocr;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.style.BackgroundColorSpan;

public class WordManagement2 extends AppCompatActivity {

    private EditText editText, searchEditText;
    private Button btnSave, btnBack, btnCopy, btnFindWord;
    private String selectedFileName;
    private File directory;
    private String originalText = ""; // 원본 텍스트 저장용


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.word_management2);

        editText = findViewById(R.id.editText);
        editText.setOnLongClickListener(v -> {
            // 빈 공간에서 길게 눌렀을 때 입력 막기
            return true; // true 리턴하면 기본 롱클릭 동작 차단
        });

        editText.setOnClickListener(v -> {
            // 일반 클릭(짧게 탭) 시에는 입력 가능
            editText.setFocusableInTouchMode(true);
            editText.requestFocus();
        });
        searchEditText = findViewById(R.id.searchEditText);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        btnCopy = findViewById(R.id.btnCopy);
        btnFindWord = findViewById(R.id.btnFindWord);

        directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");

        selectedFileName = getIntent().getStringExtra("selectedFile");

        if (selectedFileName != null) {
            new LoadtxtTask().execute(selectedFileName);
        } else {
            Toast.makeText(this, "파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
        }

        btnSave.setOnClickListener(v -> savetxtFile());

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(WordManagement2.this, WordManagement.class);
            startActivity(intent);
            finish();
        });

        btnCopy.setOnClickListener(v -> showFileSelectionDialog());

        // 찾기 버튼 클릭하면 검색창 표시
        btnFindWord.setOnClickListener(v -> {
            if (searchEditText.getVisibility() == View.GONE) {
                searchEditText.setVisibility(View.VISIBLE);
                searchEditText.requestFocus();
            } else {
                searchEditText.setVisibility(View.GONE);
            }
        });

        // 검색창 입력 이벤트
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                highlightText(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // 찾기 하이라이트 함수
    private void highlightText(String query) {
        if (originalText.isEmpty()) {
            originalText = editText.getText().toString();
        }

        SpannableString spannable = new SpannableString(originalText);

        if (!query.isEmpty()) {
            int index = originalText.toLowerCase().indexOf(query.toLowerCase());
            while (index >= 0) {
                spannable.setSpan(new BackgroundColorSpan(Color.YELLOW),
                        index, index + query.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                index = originalText.toLowerCase().indexOf(query.toLowerCase(), index + 1);
            }
        }

        editText.setText(spannable);
    }

    //
    private void showFileSelectionDialog() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null || files.length == 0) {
            Toast.makeText(this, "txt 파일이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("여기에 복사 후 붙여넣기 할 txt 파일 선택")
                .setItems(fileNames, (dialog, which) -> {
                    File selectedFile = files[which];
                    copytxtFile(selectedFile);
                })
                .show();
    }

    // 파일 복사
    private void copytxtFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder existingText = new StringBuilder(editText.getText().toString());

            String line;
            while ((line = reader.readLine()) != null) {
                existingText.append("\n").append(line);
            }
            reader.close();

            editText.setText(existingText.toString().trim());
            showDeleteFileDialog(file);
        } catch (Exception e) {
            Log.e("WordManagement2", "txt 파일 복사 오류", e);
            Toast.makeText(this, "파일 복사 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    // 복사된 파일 삭제 여부
    private void showDeleteFileDialog(File file) {
        new AlertDialog.Builder(this)
                .setTitle("파일 삭제")
                .setMessage("복사된 txt 파일을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> deleteFile(file))
                .setNegativeButton("취소", null)
                .show();
    }

    // 파일 삭제
    private void deleteFile(File file) {
        if (file.delete()) {
            Toast.makeText(this, "파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }


    private class LoadtxtTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            return loadtxtFile(params[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            originalText = result; // 원본 저장
            editText.setText(result);
        }
    }

    // 텍스트 파일 불러오기
    private String loadtxtFile(String fileName) {
        try {
            File file = new File(directory, fileName);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder text = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            reader.close();

            return text.toString().trim();
        } catch (Exception e) {
            Log.e("WordManagement2", "txt 파일 로드 오류", e);
            return "파일을 불러오는 중 오류 발생";
        }
    }

    // 텍스트 파일 저장
    private void savetxtFile() {
        try {
            File file = new File(directory, selectedFileName);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(editText.getText().toString().trim());
            writer.close();

            Toast.makeText(this, "파일이 저장되었습니다.", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(WordManagement2.this, WordManagement.class);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e("WordManagement2", "txt 파일 저장 오류", e);
            Toast.makeText(this, "파일 저장 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }



}
