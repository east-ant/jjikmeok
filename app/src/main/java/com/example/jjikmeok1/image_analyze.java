package com.example.jjikmeok1;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class image_analyze extends AppCompatActivity {

    private ImageView ivUserPhoto;
    private MaterialToolbar toolbar;
    private MaterialButton btnFindRecipe;
    private MaterialButton btnAddIngredient;

    // (추가 1) 결과 보여줄 텍스트뷰 (xml에 추가 필요, 없으면 Toast로만 확인)
    private TextView tvResult;

    // (추가 2) AI 탐지기 선언
    private YOLOv5Detector detector;
    private List<String> detectedIngredientNames = new ArrayList<>(); // 찾은 재료 이름 저장

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_analyze);

        ivUserPhoto = findViewById(R.id.iv_user_photo);
        toolbar = findViewById(R.id.toolbar);
        btnFindRecipe = findViewById(R.id.btn_find_recipe);
        btnAddIngredient = findViewById(R.id.btn_add_ingredient);

        // (참고) layout xml에 TextView를 추가했다면 아래 주석 해제
        // tvResult = findViewById(R.id.tv_result);

        // (추가 3) YOLO Detector 초기화
        try {
            detector = new YOLOv5Detector(this);
            Log.d("ImageAnalyze", "Detector loaded successfully");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "AI 모델 로드 실패", Toast.LENGTH_SHORT).show();
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        loadImageFromIntent();


        // 레시피 찾기 버튼 클릭 이벤트
        btnFindRecipe.setOnClickListener(v -> {
            // 인식된 재료 리스트 (YOLO에서 받은 결과)
            ArrayList<String> ingredients = new ArrayList<>();

            // TODO: 실제로는 YOLO 인식 결과에서 재료 가져오기
            // 지금은 하드코딩된 예시
            ingredients.add("토마토");
            ingredients.add("양파");
            ingredients.add("마늘");

            // RecipeListActivity로 이동
            Intent intent = new Intent(image_analyze.this, RecipeListActivity.class);
            intent.putStringArrayListExtra("ingredients", ingredients);
            startActivity(intent);
        });

        btnAddIngredient.setOnClickListener(v -> {
            Toast.makeText(this, "재료 추가 기능", Toast.LENGTH_SHORT).show();
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.image_analyze), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadImageFromIntent() {
        boolean fromCamera = getIntent().getBooleanExtra("fromCamera", false);
        if (fromCamera) {
            // 카메라 데이터 처리 (썸네일 or 파일 로드)
            Bundle extras = getIntent().getExtras();
            if(extras != null) {
                Bitmap bitmap = (Bitmap) extras.get("data"); // 썸네일
                ivUserPhoto.setImageBitmap(bitmap);
                if (bitmap != null) analyzeImage(bitmap);
            }
        } else {
            String imageUriString = getIntent().getStringExtra("imageUri");
            if (imageUriString != null) {
                Uri imageUri = Uri.parse(imageUriString);
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    ivUserPhoto.setImageBitmap(bitmap);

                    // 이미지가 로드되면 바로 분석 시작
                    analyzeImage(bitmap);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // (핵심) AI 분석 함수 구현
    private void analyzeImage(Bitmap bitmap) {
        if (detector == null) return;

        // 1. YOLO 실행 (인식된 객체 리스트 반환)
        List<YOLOv5Detector.Recognition> results = detector.detectObjects(bitmap);

        // 2. 결과 처리
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.RED);
        paint.setTextSize(40);

        detectedIngredientNames.clear(); // 기존 목록 초기화

        for (YOLOv5Detector.Recognition res : results) {
            // 박스 그리기
            canvas.drawRect(res.getLocation(), paint);

            // 텍스트 그리기 (이름 + 확률)
            canvas.drawText(res.getTitle() + " " + String.format("%.1f%%", res.getConfidence() * 100),
                    res.getLocation().left, res.getLocation().top, paint);

            // 재료 이름 목록에 추가
            detectedIngredientNames.add(res.getTitle());
        }

        // 3. 화면 업데이트
        ivUserPhoto.setImageBitmap(mutableBitmap); // 박스 그려진 이미지로 교체

        String resultText = "발견된 재료: " + detectedIngredientNames.toString();
        Toast.makeText(this, resultText, Toast.LENGTH_LONG).show();

        // 만약 TextView가 있다면:
        // tvResult.setText(resultText);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) detector.close(); // 메모리 해제
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}