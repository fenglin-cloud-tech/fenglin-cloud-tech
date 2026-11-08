package com.fenglin.file.storage;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.file.config.FileProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Component
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(FileProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.bucket = properties.bucket();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化文件存储桶: " + bucket, ex);
        }
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "存储写入失败: " + ex.getMessage());
        }
    }

    @Override
    public long size(String objectKey) {
        try {
            return client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build()).size();
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "存储对象不可用: " + objectKey);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (ErrorResponseException ex) {
            return false;
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "存储对象状态查询失败: " + objectKey);
        }
    }

    @Override
    public InputStream openStream(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "存储对象读取失败: " + objectKey);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, int minutes) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(minutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "签名 URL 生成失败: " + ex.getMessage());
        }
    }

    @Override
    public void removeObject(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "存储对象删除失败: " + objectKey);
        }
    }
}
