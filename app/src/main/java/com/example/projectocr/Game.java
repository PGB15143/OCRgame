package com.example.projectocr;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.json.JSONObject;


public class Game extends AppCompatActivity {

    private boolean isPaused = false;
    private Button btnPause;
    private TextView wordText, scoreText, lifeText, comboText, highScoreText;
    private Button[] answerButtons = new Button[4];
    private ProgressBar timerBar;
    private HashMap<String, String> wordMap = new HashMap<>();
    private List<String> wordList = new ArrayList<>();
    private String currentAnswer;
    private String selectedFileName = "";
    private int score = 0;
    private int combo = 0;
    private int life = 3;
    private int highScore = 0;
    private CountDownTimer timer;
    private final long timeLimit = 5000;
    private long timeLeft;
    private List<String> wrongWords = new ArrayList<>();
    private HashMap<String, int[]> wordStats = new HashMap<>(); // key: 단어, value: [맞춘 횟수, 틀린 횟수]
    private String lastWord = null;
    private ImageView[] hearts;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game);

        btnPause = findViewById(R.id.btnPause);
        btnPause.setOnClickListener(v -> pauseGame());


        wordText = findViewById(R.id.wordText);
        scoreText = findViewById(R.id.scoreText);
        comboText = findViewById(R.id.comboText);
        timerBar = findViewById(R.id.timerBar);

        hearts = new ImageView[3];
        hearts[0] = findViewById(R.id.heart1);
        hearts[1] = findViewById(R.id.heart2);
        hearts[2] = findViewById(R.id.heart3);

        answerButtons[0] = findViewById(R.id.answer1);
        answerButtons[1] = findViewById(R.id.answer2);
        answerButtons[2] = findViewById(R.id.answer3);
        answerButtons[3] = findViewById(R.id.answer4);

        updateHearts();
        loadWordsFromTxt();

    }

    // Text 파일에 있는 단어 뽑아오기
    private void loadWordsFromTxt() {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");
            File[] files = dir.listFiles();

            if (files == null || files.length == 0) {
                Toast.makeText(Game.this, "파일이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }  
    
            // 통계 json파일 빼고 리스트 불러오기
            List<String> fileDisplayList = new ArrayList<>();
            List<String> fileNames = new ArrayList<>();

            for (File f : files) {
                String name = f.getName();
                if (!name.toLowerCase().endsWith(".json")) {
                    fileNames.add(name);

                    String[] record = loadHighScoreWithDate(name);
                    String score = record[0];
                    String date = record[1];
                    fileDisplayList.add(name + "\n" + date + (score.equals("0") ? "" : "  |  최고기록: " + score));
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(Game.this);
            builder.setTitle("불러올 파일을 선택하세요");

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1, fileDisplayList) {
                @Override
                public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.view.View view = super.getView(position, convertView, parent);
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);

                    String fullText = fileDisplayList.get(position);
                    String[] lines = fullText.split("\n");
                    text1.setText(lines[0]); // 파일 이름

                    if (lines.length > 1) {
                        text2.setText(lines[1]); // 날짜 + 최고점수
                        text2.setTextColor(android.graphics.Color.parseColor("#888888")); // 회색
                    } else {
                        text2.setText("");
                    }

                    // 텍스트 간격 조금 좁히기
                    text1.setPadding(0, 0, 0, 0);
                    text2.setPadding(0, 0, 0, 0);

                    return view;
                }
            };

            builder.setAdapter(adapter, (dialog, which) -> {
                File selectedFile = new File(dir, fileNames.get(which));
                selectedFileName = selectedFile.getName();

                String[] record = loadHighScoreWithDate(selectedFileName);
                highScore = Integer.parseInt(record[0]);
                if (!wordList.isEmpty()) {
                    highScoreText.setText("최고기록: " + highScore);
                    nextQuestion();
                }


                readWordsFromFile(selectedFile);
            });

            AlertDialog dialog = builder.create();

            // 길게 누르면 통계 보기
            dialog.setOnShowListener(d -> {
                ListView listView = dialog.getListView();
                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    String fileName = fileNames.get(position);
                    showStatsForFile(fileName);
                    return true;
                });
            });

            dialog.setOnCancelListener(d -> {
                Intent intent = new Intent(Game.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });

            dialog.show();


        } catch (Exception e) {
            Toast.makeText(Game.this, "파일 읽기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 텍스트 파일에서 단어 읽기
    private void readWordsFromFile(File file) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split("\\s+"); // 스페이스 단위로 분리
                StringBuilder wordBuilder = new StringBuilder();
                StringBuilder meaningBuilder = new StringBuilder();

                boolean foundKorean = false;
                for (String token : tokens) {
                    if (!foundKorean && token.matches(".*[가-힣].*")) {
                        // 한글이 처음 등장 → 뜻 시작
                        foundKorean = true;
                    }

                    if (foundKorean) {
                        meaningBuilder.append(token).append(" ");
                    } else {
                        wordBuilder.append(token).append(" ");
                    }
                }

                String word = wordBuilder.toString().trim();
                String meaning = meaningBuilder.toString().trim();

                if (!word.isEmpty() && !meaning.isEmpty()) {
                    wordMap.put(word, meaning);
                    wordList.add(word);
                }
            }
            br.close();

            loadStatsJsonIfExists(file.getName());

            if (!wordList.isEmpty()) {
                nextQuestion();
            } else {
                Toast.makeText(this, "파일에 단어가 없습니다.", Toast.LENGTH_SHORT).show();
                finish();
            }

        } catch (Exception e) {
            Toast.makeText(this, "파일 읽기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // 게임 중 다음 단어
    private void nextQuestion() {
        if (timer != null) timer.cancel();
        if (wordList.isEmpty()) {
            Toast.makeText(this, "문제가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String word = getWeightedRandomWord();
        lastWord = word; // 마지막 단어 저장

        currentAnswer = wordMap.get(word);
        wordText.setText(word);

        // 오답 수 - 정답 수 >= 5이면 영어 단어 빨간색, 아니면 기본색
        int[] stats = wordStats.containsKey(word) ? wordStats.get(word) : new int[]{0, 0};
        if (stats[1] - stats[0] >= 5) {
            wordText.setTextColor(getResources().getColor(R.color.red));
        } else {
            wordText.setTextColor(getResources().getColor(R.color.black));
        }

        List<String> options = new ArrayList<>();
        options.add(currentAnswer);

        while (options.size() < 4) {
            String randomWord = wordList.get((int) (Math.random() * wordList.size()));
            String randomMeaning = wordMap.get(randomWord);

            if (randomMeaning == null || options.contains(randomMeaning)) continue;
            if (isMeaningSimilar(currentAnswer, randomMeaning)) continue; // 의미 비슷하면 제외

            options.add(randomMeaning);
        }
        Collections.shuffle(options);

        for (int i = 0; i < 4; i++) {
            String optionWord = options.get(i);
            answerButtons[i].setText(optionWord);
            int finalI = i;
            answerButtons[i].setOnClickListener(v -> checkAnswer(options.get(finalI)));
        }

        timeLeft = timeLimit; // 새로운 문제에서는 다시 5초
        startTimer();
    }

    // 후보 뜻이 정답안에 포함되어 있으면 제외 (ex: 먹다, 빨리 먹다)
    private boolean isMeaningSimilar(String answer, String candidate) {
        answer = answer.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase(); // 특수문자 제거 + 소문자
        candidate = candidate.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();

        return candidate.contains(answer) || answer.contains(candidate);
    }


    // 정답 체크
    private void checkAnswer(String selected) {
        if (!wordStats.containsKey(wordText.getText().toString())) {
            wordStats.put(wordText.getText().toString(), new int[]{0, 0});
        }

        if (selected.equals(currentAnswer)) {
            int bonus = (combo / 10) * 5; // 기존 콤보 보너스

            //오답 많은 단어 추가 보너스 점수
            int extraBonus = 0;
            int[] stats = wordStats.containsKey(wordText.getText().toString()) ?
                    wordStats.get(wordText.getText().toString()) : new int[]{0,0};
            if (stats[1] - stats[0] >= 5) {
                extraBonus = 10;
            }

            score += 10 + bonus + extraBonus;
            combo++;

            wordStats.get(wordText.getText().toString())[0]++; // 맞춘 횟수 증가
        } else {
            life--;
            combo = 0;
            wrongWords.add(wordText.getText().toString());

            wordStats.get(wordText.getText().toString())[1]++; // 틀린 횟수 증가

            if (life == 0) {
                gameOver();
                return;
            }
        }

        updateUI();
        nextQuestion();
    }

    // 실시간 UI 업데이트
    private void updateUI() {
        scoreText.setText("점수: " + score);
        updateHearts();
        comboText.setText("콤보: " + combo);
    }

    // 실시간 하트 업데이트
    private void updateHearts() {
        for (int i = 0; i < hearts.length; i++) {
            if (i < life) {
                hearts[i].setImageResource(R.drawable.heart_full); // 남은 하트
            } else {
                hearts[i].setImageResource(R.drawable.heart_empty); // 잃은 하트
            }
        }
    }

    // 타이머
    private void startTimer() {
        timerBar.setMax((int) timeLimit);
        timerBar.setProgress((int) timeLeft);

        timer = new CountDownTimer(timeLeft, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isPaused) {
                    cancel(); // 멈춰야 하므로 중지
                    return;
                }
                timeLeft = millisUntilFinished;
                timerBar.setProgress((int) timeLeft);
            }

            @Override
            public void onFinish() {
                if (isPaused) return; // 일시정지 상태면 무시

                if (lastWord != null) {
                    if (!wordStats.containsKey(lastWord)) {
                        wordStats.put(lastWord, new int[]{0, 0});
                    }
                    wordStats.get(lastWord)[1]++;
                    if (!wrongWords.contains(lastWord)) {
                        wrongWords.add(lastWord);
                    }
                }
                life--;
                combo = 0;
                if (life == 0) gameOver();
                else {
                    updateUI();
                    nextQuestion();
                }
            }
        }.start();
    }

    // 게임 오버
    private void gameOver() {
        if (timer != null) timer.cancel();
        StringBuilder message = new StringBuilder();
        message.append("게임 오버! 최종 점수: ").append(score).append("\n");

        if (score > highScore) {
            saveHighScore(selectedFileName, score);
            message.append("신기록 갱신!\n");
        } else {
            message.append("최고기록: ").append(highScore).append("\n");
        }

        // 틀린 단어 출력
        if (!wrongWords.isEmpty()) {
            message.append("\n틀린 단어 목록:\n");
            for (String word : wrongWords) {
                message.append(word).append(" - ").append(wordMap.get(word)).append("\n");
            }
        }

        saveWordStatsToJson();

        new AlertDialog.Builder(this)
                .setTitle("게임 결과")
                .setMessage(message.toString())
                .setPositiveButton("확인", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    // 최고 점수와 날짜 불러오기
    private String[] loadHighScoreWithDate(String fileName) {
        try {
            File recordFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/ProjectOCR", "record.txt");
            if (!recordFile.exists()) return new String[]{"0", "정보 없음"};

            BufferedReader br = new BufferedReader(new FileReader(recordFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3 && parts[0].equals(fileName)) {
                    br.close();
                    String score = parts[1];
                    String date = parts[2] + (parts.length > 3 ? " " + parts[3] : "");
                    return new String[]{score, date};
                }
            }
            br.close();
        } catch (Exception e) {
            Log.e("GameDebug", "최고 점수 불러오기 실패", e);
        }
        return new String[]{"0", "정보 없음"};
    }


    // 최고 점수 저장
    private void saveHighScore(String fileName, int score) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");
            if (!dir.exists()) dir.mkdirs();

            File recordFile = new File(dir, "record.txt");

            List<String> lines = new ArrayList<>();
            boolean found = false;
            String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());

            if (recordFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(recordFile));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2 && parts[0].equals(fileName)) {
                        lines.add(fileName + " " + score + " " + now); // update
                        found = true;
                    } else {
                        lines.add(line); // keep
                    }
                }
                br.close();
            }

            if (!found) {
                lines.add(fileName + " " + score + " " + now);
            }

            PrintWriter writer = new PrintWriter(recordFile);
            for (String line : lines) writer.println(line);
            writer.close();

        } catch (Exception e) {
            Log.e("GameDebug", "최고 점수 저장 실패", e);
        }
    }


    // 통계 저장
    private void saveWordStatsToJson() {
        try {
            if (selectedFileName == null || selectedFileName.isEmpty()) {
                Log.e("GameDebug", "저장 실패: selectedFileName이 비어있습니다.");
                return;
            }

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR");
            if (!dir.exists()) dir.mkdirs();

            String jsonFileName = selectedFileName.replaceAll("\\.\\w+$", "") + "_stats.json"; // 확장자 제거 후 _stats.json
            File statFile = new File(dir, jsonFileName);

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\n");

            int count = 0;
            for (String word : wordStats.keySet()) {
                int[] stats = wordStats.get(word);
                String meaning = wordMap.get(word).replace("\"", "\\\""); // JSON escape

                jsonBuilder.append("  \"").append(word).append("\": {\n");
                jsonBuilder.append("    \"meaning\": \"").append(meaning).append("\",\n");
                jsonBuilder.append("    \"correct\": ").append(stats[0]).append(",\n");
                jsonBuilder.append("    \"wrong\": ").append(stats[1]).append("\n");
                jsonBuilder.append("  }");

                if (++count < wordStats.size()) jsonBuilder.append(",\n");
                else jsonBuilder.append("\n");
            }

            jsonBuilder.append("}");

            PrintWriter writer = new PrintWriter(statFile);
            writer.write(jsonBuilder.toString());
            writer.close();

        } catch (Exception e) {
            Log.e("GameDebug", "단어 통계 JSON 저장 실패", e);
        }
    }

    // 통계 불러오기
    private void showStatsForFile(String fileName) {
        try {
            String baseName = fileName.replaceAll("\\.\\w+$", ""); // 확장자 제거
            File statFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/ProjectOCR", baseName + "_stats.json");

            if (!statFile.exists()) {
                Toast.makeText(this, "통계 파일이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader br = new BufferedReader(new FileReader(statFile));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonBuilder.append(line);
            }
            br.close();

            JSONObject json = new JSONObject(jsonBuilder.toString());
            selectedFileName = fileName;

            // 통계 데이터를 리스트에 저장
            List<StatEntry> statsList = new ArrayList<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String word = keys.next();
                JSONObject entry = json.getJSONObject(word);
                String meaning = entry.getString("meaning");
                int correct = entry.getInt("correct");
                int wrong = entry.getInt("wrong");
                statsList.add(new StatEntry(word, meaning, correct, wrong));
            }

            // 오답 많은 순으로 내림차순 정렬
            Collections.sort(statsList, new Comparator<StatEntry>() {
                @Override
                public int compare(StatEntry a, StatEntry b) {
                    return b.wrong - a.wrong; // 오답 많은 순
                }
            });
            // 텍스트 변환
            StringBuilder statsText = new StringBuilder();
            for (StatEntry e : statsList) {
                statsText.append(e.word).append(" (").append(e.meaning).append(")\n")
                        .append("  ✔ 정답: ").append(e.correct)
                        .append(" | ✘ 오답: ").append(e.wrong).append("\n\n");
            }

            // 다이얼로그 생성
            new AlertDialog.Builder(this)
                    .setTitle("통계: " + fileName)
                    .setMessage(statsText.toString())
                    .setPositiveButton("확인", null)
                    .setNegativeButton("기록 초기화", (dialog, which) -> {
                        if (statFile.exists()) {
                            if (statFile.delete()) {
                                Toast.makeText(Game.this, "기록이 초기화되었습니다.", Toast.LENGTH_SHORT).show();
                                wordStats.clear();  // 메모리 상 기록도 제거
                            } else {
                                Toast.makeText(Game.this, "기록 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(Game.this, "기록 파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();

        } catch (Exception e) {
            Log.e("GameDebug", "통계 표시 오류", e);
            Toast.makeText(this, "통계 불러오기 실패", Toast.LENGTH_SHORT).show();
        }
    }

    // 통계 데이터 구조
    private static class StatEntry {
        String word;
        String meaning;
        int correct;
        int wrong;

        StatEntry(String word, String meaning, int correct, int wrong) {
            this.word = word;
            this.meaning = meaning;
            this.correct = correct;
            this.wrong = wrong;
        }
    }

    // 통계 파일이 존재한다면 불러오기
    private void loadStatsJsonIfExists(String fileName) {
        try {
            String baseName = fileName.replaceAll("\\.\\w+$", "");
            File statFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ProjectOCR/" + baseName + "_stats.json");

            if (!statFile.exists()) return;

            BufferedReader br = new BufferedReader(new FileReader(statFile));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonBuilder.append(line);
            }
            br.close();

            JSONObject json = new JSONObject(jsonBuilder.toString());

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String word = keys.next();
                JSONObject entry = json.getJSONObject(word);
                int correct = entry.getInt("correct");
                int wrong = entry.getInt("wrong");

                wordStats.put(word, new int[]{correct, wrong});
            }

        } catch (Exception e) {
            Log.e("GameDebug", "통계 불러오기 실패", e);
        }
    }

    // 다음 단어 가중치 조절
    private String getWeightedRandomWord() {
        List<String> weightedList = new ArrayList<>();

        for (String word : wordList) {
            int[] stats;
            if (wordStats.containsKey(word)) {
                stats = wordStats.get(word);
            } else {
                stats = new int[]{0, 0};
            }

            int correct = stats[0];
            int wrong = stats[1];
            // 가중치 계산: 기본 10 + 오답 수 * 3 - 정답 수
            int weight = Math.max(10, 1 + wrong * 3 - correct);
            // 가중치만큼 리스트에 추가
            for (int i = 0; i < weight; i++) {
                weightedList.add(word);
            }
        }

        if (weightedList.isEmpty()) return wordList.get((int) (Math.random() * wordList.size()));

        // 최대 10회 시도 후 포기 (연속 방지)
        for (int i = 0; i < 10; i++) {
            String candidate = weightedList.get((int) (Math.random() * weightedList.size()));
            if (!candidate.equals(lastWord)) {
                return candidate;
            }
        }
        // 10번 시도했는데도 중복되면 그냥 반환 (불가피한 경우)
        return weightedList.get((int) (Math.random() * weightedList.size()));
    }

    // 게임 일시정지
    private void pauseGame() {
        if (timer != null) {
            timer.cancel(); // 타이머 멈춤
        }
        isPaused = true;

        new AlertDialog.Builder(this)
                .setTitle("일시정지")
                .setMessage("게임이 일시정지 되었습니다.")
                .setCancelable(false)
                .setPositiveButton("계속하기", (dialog, which) -> resumeGame())
                .setNegativeButton("저장하고 나가기", (dialog, which) -> {
                    // 현재 점수 저장 (신기록 갱신)
                    if (!selectedFileName.isEmpty()) {
                        saveHighScore(selectedFileName, score);
                    }

                    // 단어 통계 저장
                    saveWordStatsToJson();

                    // MainActivity로 이동
                    Intent intent = new Intent(Game.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    // 일시정지 후 계속하기
    private void resumeGame() {
        isPaused = false;
        startTimer(); // 남은 시간부터 다시 시작
    }

}