package com.fenglin.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.file.mapper.FileMapper;
import com.fenglin.file.mapper.FileUserMapper;
import com.fenglin.file.mapper.FileVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 文件元数据事务边界：RLS 表访问必须在事务内（TenantRlsInterceptor 会 SET LOCAL app.tenant_id）。
 */
@Service
public class FileRegistry {

    private final FileMapper fileMapper;
    private final FileVersionMapper versionMapper;
    private final FileUserMapper userMapper;

    public FileRegistry(FileMapper fileMapper, FileVersionMapper versionMapper, FileUserMapper userMapper) {
        this.fileMapper = fileMapper;
        this.versionMapper = versionMapper;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findReusable(long tenantId, String md5, long size) {
        return fileMapper.findReusable(tenantId, md5, size);
    }

    @Transactional(readOnly = true)
    public long usedBytes(long tenantId) {
        return fileMapper.sumSize(tenantId);
    }

    @Transactional
    public Map<String, Object> register(long tenantId, FileRegistration registration) {
        long id = IdWorker.getId();
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long operatorId = snapshot == null ? null : snapshot.userId();
        String fileNo = "F" + id;
        fileMapper.insert(id, tenantId, fileNo, registration.name(), registration.ext(),
                registration.size(), registration.md5(), registration.bucket(), registration.objectKey(),
                registration.bizType(), registration.bizId(), registration.secLevel(), "AVAILABLE", 0, operatorId);
        versionMapper.insert(IdWorker.getId(), tenantId, id, 1, registration.objectKey(),
                registration.size(), registration.md5(), null, operatorId);
        return fileMapper.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findFile(long tenantId, long fileId) {
        return fileMapper.findById(tenantId, fileId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findVersion(long tenantId, long fileId, int verNo) {
        return versionMapper.find(tenantId, fileId, verNo);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> versions(long tenantId, long fileId) {
        return versionMapper.list(tenantId, fileId);
    }

    @Transactional(readOnly = true)
    public String displayName(long tenantId, long userId) {
        String name = userMapper.findDisplayName(tenantId, userId);
        return name == null ? "用户" + userId : name;
    }

    public record FileRegistration(String name,
                                   String ext,
                                   long size,
                                   String md5,
                                   String bucket,
                                   String objectKey,
                                   String bizType,
                                   String bizId,
                                   int secLevel) {
    }
}
