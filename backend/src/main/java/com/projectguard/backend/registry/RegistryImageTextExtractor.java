package com.projectguard.backend.registry;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * 카메라로 촬영한 등기부등본 이미지에서 Google Cloud Vision의 문서 텍스트 인식
 * (DOCUMENT_TEXT_DETECTION)으로 텍스트를 추출한다. 표 형태로 촘촘한 문서라 일반
 * TEXT_DETECTION보다 DOCUMENT_TEXT_DETECTION이 더 정확하다 (docs/결정사항.md 1번 참고).
 */
@Component
public class RegistryImageTextExtractor {

    private final String credentialsPath;

    public RegistryImageTextExtractor(@Value("${GOOGLE_APPLICATION_CREDENTIALS:}") String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public String extractText(byte[] imageBytes) throws IOException {
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(loadCredentials()))
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
            Image image = Image.newBuilder().setContent(ByteString.copyFrom(imageBytes)).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            BatchAnnotateImagesResponse response = client.batchAnnotateImages(List.of(request));
            AnnotateImageResponse imageResponse = response.getResponses(0);
            if (imageResponse.hasError()) {
                throw new IOException("OCR 처리 중 오류가 발생했습니다: " + imageResponse.getError().getMessage());
            }
            return imageResponse.getFullTextAnnotation().getText();
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        try (FileInputStream credentialsStream = new FileInputStream(credentialsPath)) {
            return GoogleCredentials.fromStream(credentialsStream);
        }
    }
}
