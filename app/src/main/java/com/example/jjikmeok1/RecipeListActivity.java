package com.example.jjikmeok1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 레시피 검색 결과 목록 화면
 */
public class RecipeListActivity extends AppCompatActivity {

    private static final String TAG = "RecipeListActivity";

    // Views
    private MaterialToolbar toolbar;
    private ChipGroup chipGroupIngredients;
    private RecyclerView recyclerViewRecipes;
    private ProgressBar progressBar;
    private TextView tvEmptyMessage;
    private TextView tvResultCount;

    // Data
    private RecipeCrawler recipeCrawler;
    private RecipeAdapter adapter;
    private List<RecipeCrawler.Recipe> recipeList;
    private ArrayList<String> ingredientList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_list);

        // View 초기화
        initViews();

        // RecyclerView 설정
        setupRecyclerView();

        // 툴바 설정
        setupToolbar();

        // 크롤러 초기화
        recipeCrawler = new RecipeCrawler();

        // Intent에서 재료 리스트 받기
        ingredientList = getIntent().getStringArrayListExtra("ingredients");
        if (ingredientList == null || ingredientList.isEmpty()) {
            // 테스트용 기본 재료
            ingredientList = new ArrayList<>();
            ingredientList.add("토마토");
            ingredientList.add("양파");
        }

        // 재료 Chip 표시
        displayIngredientChips();

        // 레시피 검색 시작
        searchRecipes();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        chipGroupIngredients = findViewById(R.id.chip_group_ingredients);
        recyclerViewRecipes = findViewById(R.id.recycler_view_recipes);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyMessage = findViewById(R.id.tv_empty_message);
        tvResultCount = findViewById(R.id.tv_result_count);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("추천 레시피");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerView() {
        recipeList = new ArrayList<>();
        adapter = new RecipeAdapter(recipeList, recipe -> {
            // 레시피 클릭 시 상세 페이지로 이동 (웹브라우저로 열기)
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(recipe.getUrl()));
            startActivity(browserIntent);

            // 또는 앱 내 상세 화면으로 이동
            // Intent detailIntent = new Intent(this, RecipeDetailActivity.class);
            // detailIntent.putExtra("recipeId", recipe.getId());
            // startActivity(detailIntent);
        });

        recyclerViewRecipes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewRecipes.setAdapter(adapter);
    }

    private void displayIngredientChips() {
        chipGroupIngredients.removeAllViews();

        for (String ingredient : ingredientList) {
            Chip chip = new Chip(this);
            chip.setText(ingredient);
            chip.setChipBackgroundColorResource(R.color.chip_background);
            chip.setCloseIconVisible(false);
            chipGroupIngredients.addView(chip);
        }
    }

    private void searchRecipes() {
        // 로딩 표시
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyMessage.setVisibility(View.GONE);
        recyclerViewRecipes.setVisibility(View.GONE);

        // 레시피 검색
        recipeCrawler.searchByIngredients(ingredientList, new RecipeCrawler.RecipeSearchCallback() {
            @Override
            public void onSuccess(List<RecipeCrawler.Recipe> recipes) {
                progressBar.setVisibility(View.GONE);

                if (recipes.isEmpty()) {
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    tvEmptyMessage.setText("검색 결과가 없습니다.\n다른 재료로 검색해보세요.");
                    tvResultCount.setText("0개의 레시피");
                } else {
                    recyclerViewRecipes.setVisibility(View.VISIBLE);
                    recipeList.clear();
                    recipeList.addAll(recipes);
                    adapter.notifyDataSetChanged();
                    tvResultCount.setText(recipes.size() + "개의 레시피");
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                tvEmptyMessage.setVisibility(View.VISIBLE);
                tvEmptyMessage.setText("오류가 발생했습니다.\n" + errorMessage);
                Toast.makeText(RecipeListActivity.this,
                        "레시피 검색 실패: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recipeCrawler != null) {
            recipeCrawler.shutdown();
        }
    }

    // ==================== RecyclerView Adapter ====================

    /**
     * 레시피 목록 어댑터
     */
    public static class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

        private List<RecipeCrawler.Recipe> recipes;
        private OnRecipeClickListener listener;

        public interface OnRecipeClickListener {
            void onRecipeClick(RecipeCrawler.Recipe recipe);
        }

        public RecipeAdapter(List<RecipeCrawler.Recipe> recipes, OnRecipeClickListener listener) {
            this.recipes = recipes;
            this.listener = listener;
        }

        @NonNull
        @Override
        public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recipe, parent, false);
            return new RecipeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
            RecipeCrawler.Recipe recipe = recipes.get(position);
            holder.bind(recipe, listener);
        }

        @Override
        public int getItemCount() {
            return recipes.size();
        }

        static class RecipeViewHolder extends RecyclerView.ViewHolder {
            ImageView ivRecipeImage;
            TextView tvRecipeTitle;
            TextView tvRecipeAuthor;
            TextView tvRecipeViewCount;

            public RecipeViewHolder(@NonNull View itemView) {
                super(itemView);
                ivRecipeImage = itemView.findViewById(R.id.iv_recipe_image);
                tvRecipeTitle = itemView.findViewById(R.id.tv_recipe_title);
                tvRecipeAuthor = itemView.findViewById(R.id.tv_recipe_author);
                tvRecipeViewCount = itemView.findViewById(R.id.tv_recipe_view_count);
            }

            public void bind(RecipeCrawler.Recipe recipe, OnRecipeClickListener listener) {
                tvRecipeTitle.setText(recipe.getTitle());

                if (recipe.getAuthor() != null && !recipe.getAuthor().isEmpty()) {
                    tvRecipeAuthor.setText(recipe.getAuthor());
                    tvRecipeAuthor.setVisibility(View.VISIBLE);
                } else {
                    tvRecipeAuthor.setVisibility(View.GONE);
                }

                if (recipe.getViewCount() != null && !recipe.getViewCount().isEmpty()) {
                    tvRecipeViewCount.setText(recipe.getViewCount());
                    tvRecipeViewCount.setVisibility(View.VISIBLE);
                } else {
                    tvRecipeViewCount.setVisibility(View.GONE);
                }

                // Glide로 이미지 로드
                if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(recipe.getImageUrl())
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .centerCrop()
                            .into(ivRecipeImage);
                }

                // 클릭 리스너
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onRecipeClick(recipe);
                    }
                });
            }
        }
    }
}