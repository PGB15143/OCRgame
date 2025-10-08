package com.example.projectocr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private Uri photoUri;
    private File photoFile;
    private static final int REQUEST_PICK_IMAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSaveWords = findViewById(R.id.btnSaveWords);
        Button btnDocument = findViewById(R.id.btnDocument);
        Button btnGame = findViewById(R.id.btnGame);
        Button btnWordManagement = findViewById(R.id.btnWordManagement);
        Button btnSentence = findViewById(R.id.btnSentence);
        Button btnOption = findViewById(R.id.btnOption);
        Button btnExit = findViewById(R.id.btnExit);
        checkPermission();

        btnSaveWords.setOnClickListener(v -> {
            dispatchTakePictureIntent();
        });

        btnDocument.setOnClickListener(v -> {
            openGallery();
        });

        btnGame.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Game.class);
            startActivity(intent);
        });

        btnWordManagement.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WordManagement.class);
            startActivity(intent);
        });

        btnSentence.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Capture.class);
            intent.putExtra("isSentenceMode", true); // 문장 모드
            dispatchTakePictureIntent();  // 사진 찍는 건 기존 로직
        });


        btnExit.setOnClickListener(v -> {
            finish(); // 앱 종료
        });

    }

    // 권한 체크
    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 (Marshmallow) 이상
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                // Android 9 (Pie) 이하
                String[] permission_list = {
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };

                for (String permission : permission_list) {
                    if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                        requestPermissions(permission_list, 0);
                        return;
                    }
                }

            } else {
                // Android 10(Q) 이상 → WRITE_EXTERNAL_STORAGE는 무시됨
                String[] permission_list = {
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };

                for (String permission : permission_list) {
                    if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                        requestPermissions(permission_list, 0);
                        return;
                    }
                }
            }
        }
    }

    // 갤러리 여는 메서드 추가
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    // 카메라 실행 및 사진 저장
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                photoFile = createImageFile();
                if (photoFile != null) {
                    photoUri = FileProvider.getUriForFile(this,
                            getApplicationContext().getPackageName() + ".provider", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 사진을 로컬에 저장
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    // 사진 촬영 후 Capture.java 이동
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 사진 촬영 후
            Intent intent = new Intent(this, Capture.class);
            intent.putExtra("photoPath", photoFile.getAbsolutePath());
            startActivity(intent);
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            // 갤러리에서 이미지 선택 후
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                Intent intent = new Intent(this, Capture.class);
                intent.putExtra("imageUri", selectedImageUri.toString()); //URI를 String으로 넘기기
                startActivity(intent);
            }
        }
    }

}

