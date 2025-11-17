package com.example.jjikmeok1;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;

public class image_analyze extends AppCompatActivity {

    private ImageView ivUserPhoto;
    private MaterialToolbar toolbar;
    private MaterialButton btnFindRecipe;
    private MaterialButton btnAddIngredient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_analyze);

        // View 초기화
        ivUserPhoto = findViewById(R.id.iv_user_photo);
        toolbar = findViewById(R.id.toolbar);
        btnFindRecipe = findViewById(R.id.btn_find_recipe);
        btnAddIngredient = findViewById(R.id.btn_add_ingredient);

        // 툴바 설정
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // 툴바 뒤로가기 버튼 처리
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Intent로부터 이미지 데이터 받기
        loadImageFromIntent();

        // 레시피 찾기 버튼 클릭 이벤트
        btnFindRecipe.setOnClickListener(v -> {
            // TODO: 레시피 검색 화면으로 이동
            Toast.makeText(this, "레시피를 검색합니다...", Toast.LENGTH_SHORT).show();
        });

        // 재료 추가하기 버튼 클릭 이벤트
        btnAddIngredient.setOnClickListener(v -> {
            // TODO: 재료 추가 다이얼로그 표시
            Toast.makeText(this, "재료 추가 기능", Toast.LENGTH_SHORT).show();
        });

        // EdgeToEdge Insets 설정
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.image_analyze), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // Intent로부터 이미지 로드
    private void loadImageFromIntent() {
        // 카메라에서 온 경우
        boolean fromCamera = getIntent().getBooleanExtra("fromCamera", false);

        if (fromCamera) {
            // 카메라로 촬영한 경우 (현재는 썸네일만 전달됨)
            Toast.makeText(this, "카메라로 촬영한 이미지", Toast.LENGTH_SHORT).show();
            // 실제 앱에서는 파일 경로를 전달받아 처리해야 합니다
        } else {
            // 갤러리에서 선택한 경우
            String imageUriString = getIntent().getStringExtra("imageUri");
            if (imageUriString != null) {
                Uri imageUri = Uri.parse(imageUriString);
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                            getContentResolver(), imageUri
                    );
                    ivUserPhoto.setImageBitmap(bitmap);

                    // TODO: 여기서 AI 이미지 분석 시작
                    analyzeImage(bitmap);

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "이미지를 불러올 수 없습니다",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // AI 이미지 분석 (추후 구현)
    private void analyzeImage(Bitmap bitmap) {
        // TODO: 여기에 AI 이미지 분석 로직 추가
        // 1. 이미지를 서버로 전송하거나
        // 2. 온디바이스 ML 모델로 분석
        // 3. 인식된 재료 목록을 UI에 표시

        Toast.makeText(this, "이미지 분석 중...", Toast.LENGTH_SHORT).show();

        // 분석 완료 후 재료 목록 업데이트
        // updateIngredientList(recognizedIngredients);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}