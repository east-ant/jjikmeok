package com.example.jjikmeok1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 만개의 레시피 크롤링 클래스
 * 식재료 기반으로 레시피를 검색하고 파싱
 */


public class RecipeCrawler {
    private static final String TAG = "RecipeCrawler";
    private static final String BASE_URL = "https://www.10000recipe.com";
    private static final String SEARCH_URL = BASE_URL + "/recipe/list.html";

    private ExecutorService executor;
    private Handler mainHandler;

    public RecipeCrawler() {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 레시피 검색 결과 콜백 인터페이스
     */
    public interface RecipeSearchCallback {
        void onSuccess(List<Recipe> recipes);
        void onError(String errorMessage);
    }

    /**
     * 레시피 상세 정보 콜백 인터페이스
     */
    public interface RecipeDetailCallback {
        void onSuccess(RecipeDetail recipeDetail);
        void onError(String errorMessage);
    }

    /**
     * 여러 재료로 레시피 검색
     * @param ingredients 재료 리스트 (예: ["토마토", "양파"])
     * @param callback 결과 콜백
     */
    public void searchByIngredients(List<String> ingredients, RecipeSearchCallback callback) {
        executor.execute(() -> {
            try {
                // 재료들을 공백으로 연결하여 검색어 생성
                String query = String.join(" ", ingredients);
                List<Recipe> recipes = performSearch(query);

                mainHandler.post(() -> callback.onSuccess(recipes));
            } catch (Exception e) {
                Log.e(TAG, "검색 중 오류: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * 단일 검색어로 레시피 검색
     * @param query 검색어
     * @param callback 결과 콜백
     */
    public void search(String query, RecipeSearchCallback callback) {
        executor.execute(() -> {
            try {
                List<Recipe> recipes = performSearch(query);
                mainHandler.post(() -> callback.onSuccess(recipes));
            } catch (Exception e) {
                Log.e(TAG, "검색 중 오류: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * 실제 검색 수행 (백그라운드 스레드에서 실행)
     */
    private List<Recipe> performSearch(String query) throws IOException {
        List<Recipe> recipes = new ArrayList<>();

        // URL 인코딩
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String searchUrl = SEARCH_URL + "?q=" + encodedQuery;

        Log.d(TAG, "검색 URL: " + searchUrl);

        // Jsoup으로 HTML 파싱
        Document doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();

        // 레시피 목록 추출 (만개의 레시피 HTML 구조에 맞게 수정 필요)
        // 일반적인 구조: ul.common_sp_list_ul > li
        Elements recipeElements = doc.select("ul.common_sp_list_ul li.common_sp_list_li");

        // 만약 위 셀렉터가 안 되면 다른 구조 시도
        if (recipeElements.isEmpty()) {
            recipeElements = doc.select("div.common_sp_list_wrap li");
        }

        if (recipeElements.isEmpty()) {
            recipeElements = doc.select(".rcp_m_list li");
        }

        Log.d(TAG, "찾은 레시피 수: " + recipeElements.size());

        for (Element element : recipeElements) {
            try {
                Recipe recipe = parseRecipeElement(element);
                if (recipe != null) {
                    recipes.add(recipe);
                }
            } catch (Exception e) {
                Log.e(TAG, "레시피 파싱 오류: " + e.getMessage());
            }
        }

        return recipes;
    }

    /**
     * 개별 레시피 요소 파싱
     */
    private Recipe parseRecipeElement(Element element) {
        Recipe recipe = new Recipe();

        // 링크 추출
        Element linkElement = element.selectFirst("a");
        if (linkElement != null) {
            String href = linkElement.attr("href");
            if (!href.startsWith("http")) {
                href = BASE_URL + href;
            }
            recipe.setUrl(href);

            // URL에서 레시피 ID 추출
            String recipeId = extractRecipeId(href);
            recipe.setId(recipeId);
        }

        // 이미지 URL 추출
        Element imgElement = element.selectFirst("img");
        if (imgElement != null) {
            String imgUrl = imgElement.attr("src");
            if (imgUrl.isEmpty()) {
                imgUrl = imgElement.attr("data-src"); // lazy loading 대응
            }
            recipe.setImageUrl(imgUrl);
        }

        // 제목 추출
        Element titleElement = element.selectFirst(".common_sp_caption_tit");
        if (titleElement == null) {
            titleElement = element.selectFirst("span.ellipsis");
        }
        if (titleElement == null) {
            titleElement = element.selectFirst(".rcp_tit");
        }
        if (titleElement != null) {
            recipe.setTitle(titleElement.text().trim());
        }

        // 작성자 추출
        Element authorElement = element.selectFirst(".common_sp_caption_name");
        if (authorElement == null) {
            authorElement = element.selectFirst(".rcp_name");
        }
        if (authorElement != null) {
            recipe.setAuthor(authorElement.text().trim());
        }

        // 조회수 추출
        Element viewElement = element.selectFirst(".common_sp_caption_rv");
        if (viewElement != null) {
            recipe.setViewCount(viewElement.text().trim());
        }

        // 제목이 없으면 null 반환
        if (recipe.getTitle() == null || recipe.getTitle().isEmpty()) {
            return null;
        }

        return recipe;
    }

    /**
     * URL에서 레시피 ID 추출
     */
    private String extractRecipeId(String url) {
        // URL 형식: /recipe/6889019 또는 https://www.10000recipe.com/recipe/6889019
        String[] parts = url.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) {
                return parts[i];
            }
        }
        return "";
    }

    /**
     * 레시피 상세 정보 가져오기
     * @param recipeId 레시피 ID
     * @param callback 결과 콜백
     */
    public void getRecipeDetail(String recipeId, RecipeDetailCallback callback) {
        executor.execute(() -> {
            try {
                RecipeDetail detail = fetchRecipeDetail(recipeId);
                mainHandler.post(() -> callback.onSuccess(detail));
            } catch (Exception e) {
                Log.e(TAG, "상세 정보 로드 오류: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * 레시피 상세 정보 파싱
     */
    private RecipeDetail fetchRecipeDetail(String recipeId) throws IOException {
        String detailUrl = BASE_URL + "/recipe/" + recipeId;

        Document doc = Jsoup.connect(detailUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();

        RecipeDetail detail = new RecipeDetail();
        detail.setId(recipeId);
        detail.setUrl(detailUrl);

        // 제목
        Element titleElement = doc.selectFirst(".view2_summary h3");
        if (titleElement != null) {
            detail.setTitle(titleElement.text().trim());
        }

        // 대표 이미지
        Element mainImg = doc.selectFirst(".centeredcrop img");
        if (mainImg != null) {
            detail.setMainImageUrl(mainImg.attr("src"));
        }

        // 소개글
        Element introElement = doc.selectFirst(".view2_summary_in");
        if (introElement != null) {
            detail.setIntro(introElement.text().trim());
        }

        // 인분, 조리시간, 난이도
        Elements infoElements = doc.select(".view2_summary_info span");
        for (Element info : infoElements) {
            String text = info.text();
            if (text.contains("인분")) {
                detail.setServings(text);
            } else if (text.contains("분")) {
                detail.setCookTime(text);
            } else if (text.contains("초급") || text.contains("중급") ||
                    text.contains("고급") || text.contains("아무나")) {
                detail.setDifficulty(text);
            }
        }

        // 재료 목록
        List<String> ingredients = new ArrayList<>();
        Elements ingredientElements = doc.select(".ready_ingre3 ul li");
        for (Element ing : ingredientElements) {
            String ingredient = ing.text().trim();
            if (!ingredient.isEmpty()) {
                ingredients.add(ingredient);
            }
        }
        detail.setIngredients(ingredients);

        // 조리 순서
        List<RecipeStep> steps = new ArrayList<>();
        Elements stepElements = doc.select(".view_step_cont");
        Elements stepImgElements = doc.select(".view_step_img img");

        for (int i = 0; i < stepElements.size(); i++) {
            RecipeStep step = new RecipeStep();
            step.setStepNumber(i + 1);
            step.setDescription(stepElements.get(i).text().trim());

            if (i < stepImgElements.size()) {
                step.setImageUrl(stepImgElements.get(i).attr("src"));
            }

            steps.add(step);
        }
        detail.setSteps(steps);

        return detail;
    }

    /**
     * 리소스 해제
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // ==================== 데이터 클래스 ====================

    /**
     * 레시피 검색 결과 (목록용)
     */
    public static class Recipe {
        private String id;
        private String title;
        private String imageUrl;
        private String url;
        private String author;
        private String viewCount;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getViewCount() { return viewCount; }
        public void setViewCount(String viewCount) { this.viewCount = viewCount; }

        @Override
        public String toString() {
            return "Recipe{" +
                    "id='" + id + '\'' +
                    ", title='" + title + '\'' +
                    '}';
        }
    }

    /**
     * 레시피 상세 정보
     */
    public static class RecipeDetail {
        private String id;
        private String url;
        private String title;
        private String mainImageUrl;
        private String intro;
        private String servings;
        private String cookTime;
        private String difficulty;
        private List<String> ingredients;
        private List<RecipeStep> steps;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getMainImageUrl() { return mainImageUrl; }
        public void setMainImageUrl(String mainImageUrl) { this.mainImageUrl = mainImageUrl; }

        public String getIntro() { return intro; }
        public void setIntro(String intro) { this.intro = intro; }

        public String getServings() { return servings; }
        public void setServings(String servings) { this.servings = servings; }

        public String getCookTime() { return cookTime; }
        public void setCookTime(String cookTime) { this.cookTime = cookTime; }

        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

        public List<String> getIngredients() { return ingredients; }
        public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

        public List<RecipeStep> getSteps() { return steps; }
        public void setSteps(List<RecipeStep> steps) { this.steps = steps; }
    }

    /**
     * 조리 순서 단계
     */
    public static class RecipeStep {
        private int stepNumber;
        private String description;
        private String imageUrl;

        public int getStepNumber() { return stepNumber; }
        public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}