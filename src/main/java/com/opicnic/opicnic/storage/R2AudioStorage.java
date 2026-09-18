package com.opicnic.opicnic.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

// Cloudflare R2 = S3 API 호환. 전용 SDK가 없고 AWS S3 SDK에 엔드포인트만 R2로 준다 (StorageConfig).
// presign은 네트워크 호출이 아니다 — 서버가 자기 비밀키로 로컬에서 서명한다. R2가 죽어 있어도 URL은 나온다.
public class R2AudioStorage implements AudioStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public R2AudioStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public URL presignPut(String key, long contentLength, String contentType, Duration ttl) {
        // contentLength·contentType을 PutObjectRequest에 넣으면 서명 헤더에 포함된다 — 클라이언트가 다른
        // 크기/타입으로 PUT하면 R2가 403. presigned POST의 content-length-range와 같은 효과를 PUT으로 낸다.
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket).key(key)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build()).url();
    }

    @Override
    public byte[] read(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    // 테스트·수동 확인용. 운영 경로에서 서버가 바이트를 올리는 일은 없다.
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }
}
