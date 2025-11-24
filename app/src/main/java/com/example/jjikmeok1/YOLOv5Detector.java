package com.example.jjikmeok1;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
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
import java.util.List;

public class YOLOv5Detector {
    private static final String TAG = "YOLOv5Detector";

    // 모델 설정
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.3f; // 조금 낮춤
    private static final float IOU_THRESHOLD = 0.45f;

    private Interpreter tflite;
    private List<String> labels;

    // [수정됨] 최신 v5u 모델은 8400개입니다.
    private int numDetections = 8400;
    private int numClasses;

    private GpuDelegate gpuDelegate = null;

    public YOLOv5Detector(Context context) throws IOException {
        loadLabels(context);

        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();
        if (compatList.isDelegateSupportedOnThisDevice()) {
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            gpuDelegate = new GpuDelegate();
            options.addDelegate(gpuDelegate);
        } else {
            options.setNumThreads(4);
        }

        // [확인] 파일 이름이 실제 assets 파일명과 같은지 꼭 확인하세요!
        tflite = new Interpreter(loadModelFile(context, "yolov5su_float32.tflite"), options);

        Log.d(TAG, "Model loaded successfully");
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

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

    public List<Recognition> detectObjects(Bitmap bitmap) {
        ByteBuffer inputBuffer = preprocessImage(bitmap);

        // [수정됨] 최신 모델 출력 형태: [1, 4 + 80, 8400]
        // (Batch, Channels, Anchors) 순서입니다.
        float[][][] output = new float[1][numClasses + 4][numDetections];

        tflite.run(inputBuffer, output);

        return postprocess(output[0], bitmap.getWidth(), bitmap.getHeight());
    }

    private ByteBuffer preprocessImage(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int val = intValues[pixel++];
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                inputBuffer.putFloat((val & 0xFF) / 255.0f);
            }
        }
        return inputBuffer;
    }

    private List<Recognition> postprocess(float[][] output, int originalWidth, int originalHeight) {
        List<Recognition> recognitions = new ArrayList<>();

        // [수정됨] 최신 모델은 데이터가 세로(Column)로 나열되어 있습니다.
        // i는 0부터 8400까지 돕니다.
        for (int i = 0; i < numDetections; i++) {

            // 1. 신뢰도(Confidence) 계산
            // 최신 모델은 별도의 objectness 점수가 없고, 클래스 점수가 곧 신뢰도입니다.
            float maxClassProb = 0;
            int maxClassIdx = -1;

            // j는 0부터 80(클래스 개수)까지
            for (int j = 0; j < numClasses; j++) {
                // output[채널][앵커] 순서 접근
                // 채널 0~3은 좌표, 4부터가 클래스 점수입니다.
                float classProb = output[4 + j][i];
                if (classProb > maxClassProb) {
                    maxClassProb = classProb;
                    maxClassIdx = j;
                }
            }

            float confidence = maxClassProb;

            if (confidence < CONFIDENCE_THRESHOLD) {
                continue;
            }

            // 2. 좌표 계산 (중심점 x, y, w, h)
            float cx = output[0][i];
            float cy = output[1][i];
            float w = output[2][i];
            float h = output[3][i];

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

        return nms(recognitions);
    }

    private List<Recognition> nms(List<Recognition> recognitions) {
        recognitions.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        List<Recognition> result = new ArrayList<>();
        boolean[] isSuppressed = new boolean[recognitions.size()];

        for (int i = 0; i < recognitions.size(); i++) {
            if (isSuppressed[i]) continue;
            Recognition current = recognitions.get(i);
            result.add(current);

            for (int j = i + 1; j < recognitions.size(); j++) {
                if (isSuppressed[j]) continue;
                Recognition other = recognitions.get(j);

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

        public int getClassId() { return classId; }
        public String getTitle() { return title; }
        public float getConfidence() { return confidence; }
        public RectF getLocation() { return location; }
    }

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