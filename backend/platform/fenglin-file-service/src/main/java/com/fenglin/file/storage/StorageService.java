package com.fenglin.file.storage;

import java.io.InputStream;

/**
 * 存储抽象：MinIO/S3 统一接口（模块设计 7.1 F-FILE-02）。
 */
public interface StorageService {

    void putObject(String objectKey, InputStream stream, long size, String contentType);

    long size(String objectKey);

    boolean exists(String objectKey);

    InputStream openStream(String objectKey);

    String presignedGetUrl(String objectKey, int minutes);

    void removeObject(String objectKey);
}
