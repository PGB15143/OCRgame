package com.example.projectocr;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public class Capture2 extends AppCompatActivity {

    private EditText txtChatGPT; // Chat gpt 응답 텍스트 뷰
    private Button btnSaveDataset; // 저장 버튼
    private String chatGPTResponseTxt; // Chat gpt 응답 텍스트
    private String ocrText; // Capture.java에서 넘어온 OCR 결과
    private String imagePath; // 이미지 경로

    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.capture2);

        txtChatGPT = findViewById(R.id.txtChatGPT);
        btnSaveDataset = findViewById(R.id.btnSaveDataset);

        ocrText = getIntent().getStringExtra("ocrText");
        imagePath = getIntent().getStringExtra("imagePath");

        Log.d("DEBUG_IMAGE", "ocrText=" + ocrText);
        Log.d("DEBUG_IMAGE", "imagePath=" + imagePath);

        if (ocrText == null || ocrText.trim().isEmpty() || ocrText.equals("텍스트를 찾을 수 없습니다.")) {
            txtChatGPT.setText("텍스트를 찾을 수 없습니다.");
        } else {
            new ChatGPTTask().execute(ocrText); // ChatGPT API 호출
        }

        btnSaveDataset.setOnClickListener(v -> {
            String userText = txtChatGPT.getText().toString().trim();
            if (!userText.isEmpty()) {
                new SaveTextTask().execute(userText);
            } else {
                Toast.makeText(this, "입력된 내용이 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ChatGPT API 요청
    private class ChatGPTTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String text = params[0];
            try {
                return sendChatGPTRequest(text);
            } catch (Exception e) {
                Log.e("ChatGPT", "API 요청 오류", e);
                return "오류 발생: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            chatGPTResponseTxt = extractContentFromChatGPTResponse(result);
            txtChatGPT.setText(chatGPTResponseTxt);
        }
    }

    // 텍스트 저장
    private class SaveTextTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            String text = params[0];
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, "WordList_" + timeStamp + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write(text);
                }
                return true;
            } catch (Exception e) {
                Log.e("Capture2", "파일 저장 오류", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(Capture2.this, "단어장이 저장되었습니다.", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Capture2.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(Capture2.this, "단어장 저장 실패", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ChatGPT 요청: 텍스트+이미지 전송
    private String sendChatGPTRequest(String text) throws Exception {
        String apiUrl = "https://api.openai.com/v1/chat/completions";
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "너는 OCR 교정 도우미야. 영어 단어와 뜻을 정확하게 추출하고 교정해줘.");
        messages.put(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");

        JSONArray contentArray = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", "다음 영어 단어 목록에서 오탈자를 수정하고 각 단어와 뜻을 \n단어 뜻 \n단어 뜻 \n형식으로만 출력해줘:\n" + text);
        contentArray.put(textPart);

        // 이미지 추가
        String imagePath = getIntent().getStringExtra("imagePath");
        String imageUriString = getIntent().getStringExtra("imageUri");

        byte[] imageBytes = null;

        try {
            if (imagePath != null) {  // 파일 경로로 접근
                File imgFile = new File(imagePath);
                Log.d("DEBUG_IMAGE", "파일 경로=" + imagePath + ", exists=" + imgFile.exists());
                if (imgFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(imgFile)) {
                        imageBytes = new byte[(int) imgFile.length()];
                        fis.read(imageBytes);
                    }
                }
            } else if (imageUriString != null) {  // URI로 접근
                Uri imageUri = Uri.parse(imageUriString);
                Log.d("DEBUG_IMAGE", "URI 경로=" + imageUriString);
                try (InputStream is = getContentResolver().openInputStream(imageUri)) {
                    if (is != null) {
                        imageBytes = new byte[is.available()];
                        is.read(imageBytes);
                    }
                }
            }

            if (imageBytes != null && imageBytes.length > 0) {
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                JSONObject imgPart = new JSONObject();
                imgPart.put("type", "image_url");

                JSONObject imageUrlObj = new JSONObject();
                imageUrlObj.put("url", "data:image/jpeg;base64," + base64Image);

                imgPart.put("image_url", imageUrlObj); // 객체 형태로 전달
                contentArray.put(imgPart);

                Log.d("ChatGPT_REQUEST", "이미지 포함됨 크기: " + imageBytes.length);
            } else {
                Log.d("ChatGPT_REQUEST", "이미지 없음");
            }


        } catch (Exception e) {
            Log.e("ChatGPT_REQUEST", "이미지 로드 오류", e);
        }

        userMsg.put("content", contentArray);
        messages.put(userMsg);

        JSONObject requestJson = new JSONObject();
        requestJson.put("model", "gpt-5-mini");
        requestJson.put("messages", messages);
        requestJson.put("max_completion_tokens", 20000); // 적절히 제한

        // 이미지 잘 보내졌는지 로그확인용
        String reqStr = requestJson.toString();
        if (reqStr.contains("image_url")) {
            Log.d("ChatGPT_REQUEST", "이미지 포함됨 길이: " + reqStr.length());
            Log.d("ChatGPT_REQUEST", reqStr.substring(0, 500) + "\n...\n" +
                    reqStr.substring(reqStr.length() - 500));
        } else {
            Log.d("ChatGPT_REQUEST", "이미지 없음");
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestJson.toString().getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            Scanner errorScanner = new Scanner(conn.getErrorStream());
            String errorResponse = errorScanner.useDelimiter("\\A").next();
            errorScanner.close();
            return "API 요청 실패: " + errorResponse;
        }

        Scanner scanner = new Scanner(conn.getInputStream());
        String responseJson = scanner.useDelimiter("\\A").next();
        scanner.close();
        conn.disconnect();

        Log.d("ChatGPT_RESPONSE", responseJson);
        return responseJson;
    }

    // Chat gpt의 JSON 응답에서 답변만 뽑아내는 함수 
    private String extractContentFromChatGPTResponse(String jsonString) {
        if (!jsonString.trim().startsWith("{")) return jsonString; // "{"로 시작한다면
        try {
            JSONObject json = new JSONObject(jsonString); // 파싱 시작
            JSONArray choices = json.getJSONArray("choices"); // "choices" 부분에서
            if (choices.length() > 0) { // 응답이 0보다 크면
                JSONObject msg = choices.getJSONObject(0).getJSONObject("message"); // "message" 안에있는
                return msg.getString("content").trim(); // "content" 추출
            }
        } catch (JSONException e) {
            Log.e("Capture2", "ChatGPT 응답 파싱 오류", e);
        }
        return "응답 파싱 실패";
    }
}
