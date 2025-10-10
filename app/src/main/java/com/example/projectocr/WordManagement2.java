package com.example.projectocr;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.style.BackgroundColorSpan;

import android.widget.PopupMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class WordManagement2 extends AppCompatActivity {

    private EditText editText, searchEditText;
    private Button btnSave, btnBack;
    private ImageButton menuButton;
    private String selectedFileName;
    private File directory;
    private String originalText = ""; // 원본 텍스트 저장용
    private TextView tvWordCount;



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

        // EditText 내용이 변경될 때마다 단어 수 업데이트
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateWordCount();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchEditText = findViewById(R.id.searchEditText);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);
        menuButton = findViewById(R.id.menuButton);
        tvWordCount = findViewById(R.id.tvWordCount);

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

        // 메뉴 버튼 클릭 시
        menuButton.setOnClickListener(v -> showPopupMenu(v)); // 메뉴 보여주기
        searchEditText.addTextChangedListener(new TextWatcher() {  // 검색창
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                highlightText(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
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

    // 파일 복사할때 파일 리스트 보여주기
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
    // 메뉴 표시
    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenuInflater().inflate(R.menu.word2_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_find) {
                toggleSearchBar();
                return true;
            } else if (id == R.id.menu_copy) {
                showFileSelectionDialog();
                return true;
            } else if (id == R.id.menu_sort) {
                sortAlphabetically();
                return true;
            } else if (id == R.id.menu_overlap) {
                removeDuplicateWords();
                return true;
            } else if (id == R.id.menu_overlapword) {
                highlightDuplicateEnglishWords();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    // 검색창 보이기/숨기기
    private void toggleSearchBar() {
        if (searchEditText.getVisibility() == View.GONE) {
            searchEditText.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
        } else {
            searchEditText.setVisibility(View.GONE);
        }
    }

    // 알파벳 오름차순 정렬
    private void sortAlphabetically() {
        String[] lines = editText.getText().toString().split("\n");
        List<String> wordList = new ArrayList<>(Arrays.asList(lines));
        Collections.sort(wordList, (a, b) -> a.compareToIgnoreCase(b)); // 오름차순

        editText.setText(String.join("\n", wordList));
        Toast.makeText(this, "알파벳 오름차순으로 정렬되었습니다.", Toast.LENGTH_SHORT).show();
    }

    // 중복인 영어단어+뜻 제거
    private void removeDuplicateWords() {
        String[] lines = editText.getText().toString().split("\n"); // 줄 단위 분리
        List<String> wordList = new ArrayList<>();
        for (String line : lines) {
            line = line.trim(); // 공백 제거
            if (!line.isEmpty() && !wordList.contains(line)) {
                wordList.add(line); // 중복 없는 것만 추가
            }
        }
        editText.setText(String.join("\n", wordList)); // 다시 합쳐서 EditText에 출력
        Toast.makeText(this, "중복 단어가 제거되었습니다.", Toast.LENGTH_SHORT).show();
    }

    // 중복된 영어단어 찾기
    private void highlightDuplicateEnglishWords() {
        String text = editText.getText().toString();
        String[] lines = text.split("\n");

        // 영어 단어를 key로, 등장한 줄 인덱스 저장
        Map<String, List<Integer>> wordMap = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+"); // 공백 기준으로 나누기
            String englishWord = parts[0]; // 첫 단어만 영어 단어로 취급

            if (!wordMap.containsKey(englishWord)) {
                wordMap.put(englishWord, new ArrayList<>());
            }
            wordMap.get(englishWord).add(i); // 줄 번호 저장
        }

        // 중복된 단어만 리스트로 추출
        Set<Integer> duplicateLines = new HashSet<>();
        for (Map.Entry<String, List<Integer>> entry : wordMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicateLines.addAll(entry.getValue());
            }
        }

        //  중복 줄 하이라이트
        SpannableString spannable = new SpannableString(text);
        int start = 0;
        for (int i = 0; i < lines.length; i++) {
            int end = start + lines[i].length();
            if (duplicateLines.contains(i)) {
                spannable.setSpan(new BackgroundColorSpan(Color.YELLOW),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = end + 1; // 줄바꿈(\n) 위치 건너뛰기
        }

        editText.setText(spannable);
    }

    // 단어 수 세기
    private void updateWordCount() {
        String text = editText.getText().toString().trim();
        if (text.isEmpty()) {
            tvWordCount.setText("단어 갯수: 0개");
            return;
        }

        // 줄 개수 세기
        String[] lines = text.split("\n");
        int count = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }

        tvWordCount.setText("단어 갯수: " + count + "개");
    }

}
