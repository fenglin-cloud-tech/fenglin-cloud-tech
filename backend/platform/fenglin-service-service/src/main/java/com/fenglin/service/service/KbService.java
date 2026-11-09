package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 知识库：分类、文章、搜索、反馈与推荐。
 */
@Service
@Transactional
public class KbService {

    private final ConfigMapper configMapper;
    private final CommonsMapper commonsMapper;
    private final ServiceAudit audit;

    public KbService(ConfigMapper configMapper, CommonsMapper commonsMapper, ServiceAudit audit) {
        this.configMapper = configMapper;
        this.commonsMapper = commonsMapper;
        this.audit = audit;
    }

    public List<Map<String, Object>> categories() {
        return configMapper.listKbCategories(ServiceContext.tenantId());
    }

    public Map<String, Object> createCategory(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long id = IdWorker.getId();
        configMapper.insertKbCategory(id, tenantId,
                ServiceContext.requireText(ServiceContext.string(body.get("name"))),
                body.get("parent_id") == null ? null : ServiceContext.requireLong(body.get("parent_id")),
                body.get("sort_no") == null ? 0 : (int) ServiceContext.requireLong(body.get("sort_no")),
                ServiceContext.userId());
        return ServiceMaps.of("id", id);
    }

    public PageResult<Map<String, Object>> list(String status, Long categoryId, String keyword,
                                                Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = configMapper.listArticles(tenantId, blank(status), categoryId,
                blank(keyword), limit, offset);
        long total = configMapper.countArticles(tenantId, blank(status), categoryId, blank(keyword));
        return PageResult.of(rows, total, ServiceContext.page(page), limit);
    }

    public Map<String, Object> detail(long id) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> article = configMapper.findArticle(tenantId, id);
        if (article == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PUBLISHED".equals(Rows.str(article, "status"))) {
            throw new BizException(ServiceErrorCode.KB_NOT_PUBLISHED);
        }
        configMapper.increaseView(tenantId, id);
        return ServiceMaps.parse(article, "keywords");
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long id = IdWorker.getId();
        int versionNo = body.get("version_no") == null ? 1
                : (int) ServiceContext.requireLong(body.get("version_no"));
        String status = body.get("status") == null ? "DRAFT" : ServiceContext.string(body.get("status")).toUpperCase();
        Timestamp publishedAt = "PUBLISHED".equals(status) ? commonsMapper.now() : null;
        configMapper.insertArticle(id, tenantId,
                body.get("category_id") == null ? null : ServiceContext.requireLong(body.get("category_id")),
                ServiceContext.requireText(ServiceContext.string(body.get("title"))),
                ServiceContext.string(body.get("summary")),
                ServiceContext.requireText(ServiceContext.string(body.get("content"))),
                ServiceMaps.json(body.get("keywords")), versionNo,
                body.get("visibility") == null ? "PUBLIC" : ServiceContext.string(body.get("visibility")),
                status, userId, publishedAt, "kb-" + id);
        audit.low("kb.save", "kb_article", id);
        return ServiceMaps.of("id", id, "status", status);
    }

    @Transactional
    public Map<String, Object> feedback(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> article = configMapper.findArticle(tenantId, id);
        if (article == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean helpful = Boolean.TRUE.equals(body.get("helpful"));
        configMapper.insertKbFeedback(IdWorker.getId(), tenantId, id, userId, helpful,
                ServiceContext.string(body.get("reason")),
                body.get("ticket_id") == null ? null : ServiceContext.requireLong(body.get("ticket_id")));
        if (helpful) {
            configMapper.increaseHelpful(tenantId, id);
        }
        return ServiceMaps.of("article_id", id, "helpful", helpful);
    }

    public List<Map<String, Object>> suggest(Long ticketId, String keyword, Integer limit) {
        long tenantId = ServiceContext.tenantId();
        String text = ServiceContext.string(keyword);
        List<Map<String, Object>> rows = configMapper.suggestArticles(tenantId,
                text == null ? "" : text, limit == null ? 5 : Math.min(Math.max(limit, 1), 20));
        return ServiceMaps.parseAll(rows, "keywords");
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
