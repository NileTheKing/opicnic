package com.opicnic.opicnic.config;

import com.opicnic.opicnic.storage.AudioStorage;
import com.opicnic.opicnic.storage.InMemoryAudioStorage;
import com.opicnic.opicnic.storage.R2AudioStorage;
import com.opicnic.opicnic.storage.ReadCachingAudioStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@Slf4j
public class StorageConfig {

    // R2_ACCOUNT_ID가 비어 있으면 인메모리로 뜬다 — 키 없는 로컬·CI에서도 앱이 기동되게. 운영은 반드시 R2.
    @Bean
    public AudioStorage audioStorage(@Value("${R2_ACCOUNT_ID:}") String accountId,
                                     @Value("${R2_ACCESS_KEY_ID:}") String accessKeyId,
                                     @Value("${R2_SECRET_ACCESS_KEY:}") String secretAccessKey,
                                     @Value("${R2_BUCKET:opicnic-audio}") String bucket,
                                     @Value("${opicnic.storage.read-cache:false}") boolean readCache) {
        if (accountId.isBlank()) {
            log.warn("[Storage] R2_ACCOUNT_ID 없음 — 인메모리 AudioStorage로 기동. 재시작 시 오디오 유실. 운영에서는 안 됨");
            return new InMemoryAudioStorage();
        }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        var endpoint = URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
        // R2는 리전 개념이 없어 "auto". path-style: 버킷을 호스트가 아니라 경로에 — R2 권장
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        S3Client s3 = S3Client.builder()
                .region(Region.of("auto")).endpointOverride(endpoint)
                .credentialsProvider(credentials).serviceConfiguration(s3Config)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of("auto")).endpointOverride(endpoint)
                .credentialsProvider(credentials).serviceConfiguration(s3Config)
                .build();
        log.info("[Storage] R2 AudioStorage 기동: bucket={} readCache={}", bucket, readCache);
        AudioStorage r2 = new R2AudioStorage(s3, presigner, bucket);
        return readCache ? new ReadCachingAudioStorage(r2) : r2;
    }
}
