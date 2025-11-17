package com.example.jjikmeok1;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YOLOv5Detector {
    private static final String TAG = "YOLOv5Detector";

    // 모델 설정
    private static final int INPUT_SIZE = 640; // YOLO5의 기본 입력 크기
    private static final float CONFIDENCE_THRESHOLD = 0.5f; // 신뢰도 임계값
    private static final float IOU_THRESHOLD = 0.45f; // NMS IOU 임계값

    private Interpreter tflite;
    private List<String> labels;
    private int numDetections = 25200; // YOLO5s 기준 (조정 필요)
    private int numClasses;

    // GPU 가속 사용 여부
    private GpuDelegate gpuDelegate = null;

    public YOLOv5Detector(Context context) throws IOException {
        // 라벨 파일 로드
        loadLabels(context);

        // 모델 로드
        Interpreter.Options options = new Interpreter.Options();

        // GPU 사용 가능하면 GPU 사용
        CompatibilityList compatList = new CompatibilityList();
        if (compatList.isDelegateSupportedOnThisDevice()) {
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            gpuDelegate = new GpuDelegate();
            options.addDelegate(gpuDelegate);
            Log.d(TAG, "GPU acceleration enabled");
        } else {
            options.setNumThreads(4);
            Log.d(TAG, "GPU not available, using CPU with 4 threads");
        }

        tflite = new Interpreter(loadModelFile(context, "yolov5s-fp16.tflite"), options);

        Log.d(TAG, "Model loaded successfully");
    }

    // 모델 파일 로드
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // 라벨 파일 로드 (labels.txt 파일에서)
    private void loadLabels(Context context) throws IOException {
        labels = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("labels.txt"))
        );
        String line;
        while ((line = reader.readLine()) != null) {
            labels.add(line);
        }
        reader.close();
        numClasses = labels.size();
        Log.d(TAG, "Loaded " + numClasses + " labels");
    }

    // 이미지 분석 메인 함수
    public List<Recognition> detectObjects(Bitmap bitmap) {
        // 1. 이미지 전처리
        ByteBuffer inputBuffer = preprocessImage(bitmap);

        // 2. 모델 추론
        float[][][] output = new float[1][numDetections][numClasses + 5];
        tflite.run(inputBuffer, output);

        // 3. 후처리 (결과 파싱 및 NMS)
        return postprocess(output[0], bitmap.getWidth(), bitmap.getHeight());
    }

    // 이미지 전처리
    private ByteBuffer preprocessImage(Bitmap bitmap) {
        // Bitmap을 INPUT_SIZE x INPUT_SIZE로 리사이즈
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // 정규화 (0-255 -> 0-1)
        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int val = intValues[pixel++];
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // R
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);  // G
                inputBuffer.putFloat((val & 0xFF) / 255.0f);         // B
            }
        }

        return inputBuffer;
    }

    // 결과 후처리
    private List<Recognition> postprocess(float[][] output, int originalWidth, int originalHeight) {
        List<Recognition> recognitions = new ArrayList<>();

        // 각 detection 처리
        for (int i = 0; i < numDetections; i++) {
            float[] detection = output[i];

            // confidence = objectness * class_prob
            float objectness = detection[4];

            if (objectness < CONFIDENCE_THRESHOLD) {
                continue;
            }

            // 가장 높은 클래스 확률 찾기
            float maxClassProb = 0;
            int maxClassIdx = -1;
            for (int c = 0; c < numClasses; c++) {
                float classProb = detection[5 + c];
                if (classProb > maxClassProb) {
                    maxClassProb = classProb;
                    maxClassIdx = c;
                }
            }

            float confidence = objectness * maxClassProb;

            if (confidence < CONFIDENCE_THRESHOLD) {
                continue;
            }

            // Bounding box 좌표 (중심점, 너비, 높이 -> 좌상단, 우하단)
            float cx = detection[0];
            float cy = detection[1];
            float w = detection[2];
            float h = detection[3];

            float left = (cx - w / 2) * originalWidth / INPUT_SIZE;
            float top = (cy - h / 2) * originalHeight / INPUT_SIZE;
            float right = (cx + w / 2) * originalWidth / INPUT_SIZE;
            float bottom = (cy + h / 2) * originalHeight / INPUT_SIZE;

            RectF bbox = new RectF(left, top, right, bottom);

            recognitions.add(new Recognition(
                    maxClassIdx,
                    labels.get(maxClassIdx),
                    confidence,
                    bbox
            ));
        }

        // Non-Maximum Suppression (NMS)
        return nms(recognitions);
    }

    // Non-Maximum Suppression
    private List<Recognition> nms(List<Recognition> recognitions) {
        // confidence 기준으로 정렬
        recognitions.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

        List<Recognition> result = new ArrayList<>();
        boolean[] isSuppressed = new boolean[recognitions.size()];

        for (int i = 0; i < recognitions.size(); i++) {
            if (isSuppressed[i]) continue;

            Recognition current = recognitions.get(i);
            result.add(current);

            // 겹치는 다른 detection들 억제
            for (int j = i + 1; j < recognitions.size(); j++) {
                if (isSuppressed[j]) continue;

                Recognition other = recognitions.get(j);

                // 같은 클래스이고 IOU가 임계값보다 크면 억제
                if (current.getClassId() == other.getClassId()) {
                    float iou = calculateIOU(current.getLocation(), other.getLocation());
                    if (iou > IOU_THRESHOLD) {
                        isSuppressed[j] = true;
                    }
                }
            }
        }

        return result;
    }

    // IOU (Intersection over Union) 계산
    private float calculateIOU(RectF box1, RectF box2) {
        float intersectionLeft = Math.max(box1.left, box2.left);
        float intersectionTop = Math.max(box1.top, box2.top);
        float intersectionRight = Math.min(box1.right, box2.right);
        float intersectionBottom = Math.min(box1.bottom, box2.bottom);

        float intersectionWidth = Math.max(0, intersectionRight - intersectionLeft);
        float intersectionHeight = Math.max(0, intersectionBottom - intersectionTop);
        float intersectionArea = intersectionWidth * intersectionHeight;

        float box1Area = (box1.right - box1.left) * (box1.bottom - box1.top);
        float box2Area = (box2.right - box2.left) * (box2.bottom - box2.top);
        float unionArea = box1Area + box2Area - intersectionArea;

        return intersectionArea / unionArea;
    }

    // Detection 결과를 카운트로 집계
    public Map<String, Integer> countIngredients(List<Recognition> recognitions) {
        Map<String, Integer> counts = new HashMap<>();

        for (Recognition recognition : recognitions) {
            String label = recognition.getTitle();
            counts.put(label, counts.getOrDefault(label, 0) + 1);
        }

        return counts;
    }

    // Recognition 클래스 (detection 결과)
    public static class Recognition {
        private final int classId;
        private final String title;
        private final float confidence;
        private final RectF location;

        public Recognition(int classId, String title, float confidence, RectF location) {
            this.classId = classId;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public int getClassId() {
            return classId;
        }

        public String getTitle() {
            return title;
        }

        public float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return location;
        }

        @Override
        public String toString() {
            return String.format("%s (%.2f%%)", title, confidence * 100);
        }
    }

    // 리소스 해제
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }
}