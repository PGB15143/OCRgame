package com.example.projectocr;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.internal.GsonBuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;



public class Capture extends AppCompatActivity {

    private TextView txtOcrResult; // OCR 결과 창
    private String photoPath; // 사진 경로
    private Button btnCapture2, btnBack; // 버튼들
    private Button btnSave1; // 저장 버튼
    private String imageUriString; // 갤러리 이미지 경로


    private static final String API_KEY = BuildConfig.GOOGLE_VISION_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.capture);

        txtOcrResult = findViewById(R.id.txtOcrResult);
        btnCapture2 = findViewById(R.id.btnCapture2);
        btnBack = findViewById(R.id.btnBack1);
        btnSave1 = findViewById(R.id.btnSave1);

        // MainActivity에서 전달받은 사진,이미지 파일 경로
        photoPath = getIntent().getStringExtra("photoPath");
        imageUriString = getIntent().getStringExtra("imageUri");

        if (photoPath != null) {
            new VisionApiTask(false).execute(photoPath); // false: 파일경로
        } else if (imageUriString != null) {
            new VisionApiTask(true).execute(imageUriString); // true: URI
        } else {
            txtOcrResult.setText("이미지 경로를 찾을 수 없습니다.");
        }

        // chat gpt 넘기기 버튼
        btnCapture2.setOnClickListener(v -> {
            String ocrResult = txtOcrResult.getText().toString();
            Intent intent = new Intent(Capture.this, Capture2.class);
            intent.putExtra("ocrText", ocrResult);

            // 파일 경로(photoPath)와 URI(imageUriString) 중 하나라도 있으면 전달
            if (photoPath != null) {
                intent.putExtra("imagePath", photoPath);
            }
            if (imageUriString != null) {
                intent.putExtra("imageUri", imageUriString);
            }

            startActivity(intent);
        });

        // 바로 저장
        btnSave1.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                saveOcrResultAsTxt();
            }
        });

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(Capture.this, MainActivity.class);
            startActivity(intent);
        });

    }

    // AsyncTask를 사용하여 네트워크 작업을 백그라운드에서 실행
    private class VisionApiTask extends AsyncTask<String, Void, String> {
        private boolean isUriMode;

        public VisionApiTask(boolean isUriMode) {
            this.isUriMode = isUriMode;
        }

        @Override
        protected String doInBackground(String... params) {
            String pathOrUri = params[0];
            try {
                return sendVisionApiRequest(pathOrUri, isUriMode);
            } catch (IOException | JSONException e) {
                Log.e("OCR", "Error processing image", e);
                return "OCR 오류 발생: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            txtOcrResult.setText(result);
        }
    }

    // Google Vision API 요청을 보내는 함수
    private String sendVisionApiRequest(String pathOrUri, boolean isUriMode) throws IOException, JSONException {
        byte[] imageBytes;

        if (isUriMode) {
            Uri imageUri = Uri.parse(pathOrUri);
            ContentResolver resolver = getContentResolver();
            try (InputStream inputStream = resolver.openInputStream(imageUri)) {
                if (inputStream == null) {
                    return "이미지를 읽을 수 없습니다.";
                }
                imageBytes = new byte[inputStream.available()];
                inputStream.read(imageBytes);
            }
        } else {
            File imageFile = new File(pathOrUri);
            if (!imageFile.exists()) {
                return "파일이 존재하지 않습니다.";
            }
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                imageBytes = new byte[(int) imageFile.length()];
                fis.read(imageBytes);
            }
        }

        // Base64 인코딩
        String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        // 요청 JSON 생성
        JSONObject requestJson = new JSONObject();
        JSONArray requestsArray = new JSONArray();
        JSONObject imageJson = new JSONObject();
        JSONObject featureJson = new JSONObject();
        JSONObject requestObject = new JSONObject();

        featureJson.put("type", "DOCUMENT_TEXT_DETECTION");
        imageJson.put("content", base64Image);

        requestObject.put("image", imageJson);
        requestObject.put("features", new JSONArray().put(featureJson));
        requestsArray.put(requestObject);
        requestJson.put("requests", requestsArray);

        Log.d("OCR_REQUEST", "API 요청: " + requestJson.toString());

        // Vision API 호출
        String apiUrl = "https://vision.googleapis.com/v1/images:annotate?key=" + API_KEY;
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestJson.toString().getBytes("UTF-8"));
            os.flush();
        }

        Scanner scanner = new Scanner(connection.getInputStream());
        String responseJson = scanner.useDelimiter("\\A").next();
        scanner.close();
        connection.disconnect();

        return parseOcrResponse(responseJson);
    }

    // Vision API 응답에서 OCR 결과 추출
    private String parseOcrResponse(String responseJson) {
        try {
            Log.d("OCR_RESPONSE", "API 응답: " + responseJson);

            JSONObject jsonResponse = new JSONObject(responseJson);
            JSONArray responses = jsonResponse.getJSONArray("responses");

            if (responses.length() == 0) {
                Log.e("OCR", "OCR 응답이 비어 있음");
                return "OCR 결과가 없습니다.";
            }

            JSONObject firstResponse = responses.getJSONObject(0);

            if (firstResponse.has("textAnnotations")) {
                JSONArray textAnnotations = firstResponse.getJSONArray("textAnnotations");
                if (textAnnotations.length() > 0) {
                    return textAnnotations.getJSONObject(0).getString("description");
                }
            }

            if (firstResponse.has("fullTextAnnotation")) {
                return firstResponse.getJSONObject("fullTextAnnotation").getString("text");
            }

            Log.e("OCR", "textAnnotations와 fullTextAnnotation 둘 다 없음");
        } catch (Exception e) {
                Log.e("OCR", "OCR 응답 처리 중 오류 발생", e);
        }
        return "텍스트를 찾을 수 없습니다.";
    }
    
    // OCR 결과를 Text로 저장
    private void saveOcrResultAsTxt() {
        String ocrText = txtOcrResult.getText().toString().trim();

        if (ocrText.isEmpty()) {
            Toast.makeText(this, "저장할 OCR 결과가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            StringBuilder textBuilder = new StringBuilder();

            String[] lines = ocrText.split("\n");
            for (String line : lines) {
                String[] parts = line.split(" ");
                if (parts.length > 1) {
                    StringBuilder englishWordBuilder = new StringBuilder();
                    StringBuilder meaningBuilder = new StringBuilder();

                    for (String part : parts) {
                        if (part.matches("[a-zA-Z]+")) {
                            if (englishWordBuilder.length() > 0) englishWordBuilder.append("_");
                            englishWordBuilder.append(part);
                        } else {
                            if (meaningBuilder.length() > 0) meaningBuilder.append(" ");
                            meaningBuilder.append(part);
                        }
                    }

                    String englishWord = englishWordBuilder.toString();
                    String meaning = meaningBuilder.toString();

                    textBuilder.append(englishWord).append(" ").append(meaning).append("\n");
                }
            }

            // 파일 저장 형식 Words_yyyyMMdd_HHmmss.txt
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "Words_" + timeStamp + ".txt";
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, fileName);

            FileWriter writer = new FileWriter(file);
            writer.write(textBuilder.toString());
            writer.close();

            Toast.makeText(this, "TXT 파일 저장 완료: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.d("Capture", "TXT 저장 경로: " + file.getAbsolutePath());

            Intent intent = new Intent(Capture.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e("Capture", "TXT 저장 중 오류 발생", e);
            Toast.makeText(this, "TXT 저장 실패!", Toast.LENGTH_SHORT).show();
        }
    }
}
