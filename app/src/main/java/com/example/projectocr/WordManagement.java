package com.example.projectocr;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.InputStreamReader;


public class WordManagement extends AppCompatActivity {

    private ListView listView;
    private Button btnAddFile, btnBack;
    private ArrayList<String> fileList;
    private WordAdapter adapter;
    private Button btnDelete;
    private File directory;
    private Button btnMergeFiles;
    private Button btnImportFile;
    private ActivityResultLauncher<Intent> filePickerLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.word_management);

        listView = findViewById(R.id.listView);
        btnAddFile = findViewById(R.id.btnAddFile);
        btnBack = findViewById(R.id.btnBack3);
        btnImportFile = findViewById(R.id.btnImportFile);

        directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");

        loadFileList();  // 파일 목록 불러오기

        btnAddFile.setOnClickListener(v -> addNewFile());
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(WordManagement.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // 현재 WordManagement 종료
        });

        btnDelete = findViewById(R.id.btnDelete);

        btnDelete.setOnClickListener(v -> deleteSelectedFiles());


        btnMergeFiles = findViewById(R.id.btnMergeFiles);
        if (btnMergeFiles == null) {
            Toast.makeText(this, "btnMergeFiles 찾을 수 없음", Toast.LENGTH_SHORT).show();
        }
        btnMergeFiles.setOnClickListener(v -> mergeSelectedFiles());

        // ✅ 파일 선택 후 결과 받기
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        importTextFile(uri);
                    }
                }
        );

        // 버튼 눌렀을 때 파일 탐색기 열기
        btnImportFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/plain");  // txt 파일만 선택
            filePickerLauncher.launch(intent);
        });

        // 리스트 아이템을 길게 누르면 파일 이름 변경
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            renameFile(position);
            return true;
        });

        // 리스트 아이템을 짧게 누르면 파일 열기
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedFileName = fileList.get(position);
            Intent intent = new Intent(WordManagement.this, WordManagement2.class);
            intent.putExtra("selectedFile", selectedFileName);
            startActivity(intent);
        });

    }

    // 파일 이름 변경 메서드 (리스트뷰 길게 누를 때 호출됨)
    private void renameFile(int position) {
        if (position == -1) {
            Toast.makeText(this, "이름을 변경할 파일을 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedFile = fileList.get(position);
        File oldFile = new File(directory, selectedFile);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("새 파일 이름을 입력하세요");

        new AlertDialog.Builder(this)
                .setTitle("파일 이름 변경")
                .setMessage("새로운 파일 이름을 입력하세요:")
                .setView(input)
                .setPositiveButton("변경", (dialog, which) -> {
                    String newFileName = input.getText().toString().trim();

                    if (newFileName.isEmpty()) {
                        Toast.makeText(this, "파일 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newFileName.endsWith(".txt")) {
                        newFileName += ".txt";
                    }

                    File newFile = new File(directory, newFileName);

                    if (newFile.exists()) {
                        Toast.makeText(this, "같은 이름의 파일이 이미 존재합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (oldFile.renameTo(newFile)) {
                        updateRecordFileName(selectedFile, newFileName);
                        Toast.makeText(this, "파일 이름이 변경되었습니다.", Toast.LENGTH_SHORT).show();
                        loadFileList(); // 리스트 갱신
                    } else {
                        Toast.makeText(this, "파일 이름 변경 실패", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 최고기록 저장파일 업데이트
    private void updateRecordFileName(String oldName, String newName) {
        File recordFile = new File(directory, "record.json");
        if (!recordFile.exists()) return;

        try {
            // JSON 파일 읽기
            BufferedReader reader = new BufferedReader(new FileReader(recordFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            org.json.JSONObject json = new org.json.JSONObject(sb.toString());
            org.json.JSONObject updatedJson = new org.json.JSONObject();

            // names()는 JSONArray를 반환하므로 반복문 수정
            org.json.JSONArray keys = json.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    org.json.JSONObject entry = json.getJSONObject(key);
                    String fileName = entry.optString("fileName", "");

                    // 파일 이름이 일치하면 변경
                    if (fileName.equals(oldName)) {
                        entry.put("fileName", newName);
                    }

                    updatedJson.put(key, entry);
                }
            }

            // JSON 파일 다시 저장
            java.io.PrintWriter pw = new java.io.PrintWriter(recordFile);
            pw.write(updatedJson.toString(2)); // 보기 좋게 저장 (들여쓰기 2칸)
            pw.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // txt 파일 리스트 불러오기
    private void loadFileList() {
        fileList = new ArrayList<>();
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".txt")) {
                        fileList.add(file.getName());
                    }
                }
            }
        }

        adapter = new WordAdapter(this, fileList);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    }

    // 빈 txt 파일 추가
    private void addNewFile() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("파일 이름을 입력하세요");

        new AlertDialog.Builder(this)
                .setTitle("새 파일 추가")
                .setMessage("새로운 파일 이름을 입력하세요:")
                .setView(input)
                .setPositiveButton("추가", (dialog, which) -> {
                    String fileName = input.getText().toString().trim();

                    if (fileName.isEmpty()) {
                        Toast.makeText(this, "파일 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!fileName.endsWith(".txt")) {
                        fileName += ".txt";
                    }

                    File newFile = new File(directory, fileName);

                    if (newFile.exists()) {
                        Toast.makeText(this, "같은 이름의 파일이 이미 존재합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        FileWriter writer = new FileWriter(newFile);
                        writer.write("{}\n");
                        writer.close();
                        Toast.makeText(this, "파일이 추가되었습니다.", Toast.LENGTH_SHORT).show();
                        loadFileList();
                    } catch (IOException e) {
                        Toast.makeText(this, "파일 생성 실패", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 파일 합치기
    private void mergeSelectedFiles() {
        ArrayList<String> selectedFiles = adapter.getSelectedFiles();

        if (selectedFiles.size() < 2) {
            Toast.makeText(this, "두 개 이상의 파일을 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("새 파일 이름을 입력하세요");

        new AlertDialog.Builder(this)
                .setTitle("파일 이름")
                .setView(input)
                .setPositiveButton("합치기", (dialog, which) -> {
                    String mergedFileName = input.getText().toString().trim();
                    if (mergedFileName.isEmpty()) {
                        Toast.makeText(this, "파일 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!mergedFileName.endsWith(".txt")) {
                        mergedFileName += ".txt";
                    }

                    File mergedFile = new File(directory, mergedFileName);
                    if (mergedFile.exists()) {
                        Toast.makeText(this, "같은 이름의 파일이 이미 존재합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(mergedFile));
                        for (String fileName : selectedFiles) {
                            File file = new File(directory, fileName);
                            BufferedReader reader = new BufferedReader(new FileReader(file));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                writer.write(line);
                                writer.newLine();
                            }
                            reader.close();
                        }
                        writer.close();
                        loadFileList();        // 파일 목록 새로 불러오기
                        listView.clearChoices(); // 리스트뷰 체크 초기화
                        Toast.makeText(this, "파일 합치기 완료!", Toast.LENGTH_SHORT).show();
                        loadFileList();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "파일 합치기 실패", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();

    }

    // 파일 삭제
    private void deleteSelectedFiles() {
        ArrayList<String> selectedFiles = adapter.getSelectedFiles();  // WordAdapter 안의 체크된 파일을 가져오기

        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "삭제할 파일을 선택하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("파일 삭제")
                .setMessage("선택한 파일을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    for (String fileName : selectedFiles) {
                        File file = new File(directory, fileName);
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                    Toast.makeText(this, "파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    loadFileList();        // 파일 목록 새로고침
                })
                .setNegativeButton("취소", null)
                .show();
    }
    // 선택된 파일을 ProjectOCR 폴더에 원래 이름으로 복사
    private void importTextFile(Uri uri) {
        try {
            // 원래 파일명 가져오기
            String fileName = getFileNameFromUri(uri);
            if (fileName == null || !fileName.endsWith(".txt")) {
                Toast.makeText(this, "TXT 파일만 불러올 수 있습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            File newFile = new File(directory, fileName);
            if (!directory.exists()) directory.mkdirs();

            // 같은 이름의 파일이 이미 있으면 덮어쓰기 방지
            if (newFile.exists()) {
                Toast.makeText(this, "같은 이름의 파일이 이미 존재합니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
            BufferedWriter writer = new BufferedWriter(new FileWriter(newFile));

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }

            reader.close();
            writer.close();

            Toast.makeText(this, "파일이 불러와졌습니다: " + fileName, Toast.LENGTH_SHORT).show();
            loadFileList(); // 리스트 새로고침

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "파일 불러오기 실패", Toast.LENGTH_SHORT).show();
        }
    }

    // Uri에서 원래 파일 이름 얻기
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

}
