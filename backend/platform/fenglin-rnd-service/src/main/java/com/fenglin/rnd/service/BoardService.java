package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.BoardMapper;
import com.fenglin.rnd.mapper.DefectMapper;
import com.fenglin.rnd.mapper.ProductMapper;
import com.fenglin.rnd.mapper.RequirementMapper;
import com.fenglin.rnd.mapper.TaskMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.BoardColumnRequest;
import com.fenglin.rnd.web.dto.RndDtos.BoardColumnView;
import com.fenglin.rnd.web.dto.RndDtos.BoardCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.BoardView;
import com.fenglin.rnd.web.dto.RndDtos.MoveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * E1 看板：列 WIP 限制与卡片移动联动工作项状态。
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardMapper boardMapper;
    private final TaskMapper taskMapper;
    private final DefectMapper defectMapper;
    private final RequirementMapper requirementMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final RndAudit audit;
    private final ObjectMapper objectMapper;

    public BoardService(BoardMapper boardMapper, TaskMapper taskMapper, DefectMapper defectMapper,
                        RequirementMapper requirementMapper, ProductMapper productMapper,
                        ProductService productService, RndAudit audit, ObjectMapper objectMapper) {
        this.boardMapper = boardMapper;
        this.taskMapper = taskMapper;
        this.defectMapper = defectMapper;
        this.requirementMapper = requirementMapper;
        this.productMapper = productMapper;
        this.productService = productService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BoardView create(BoardCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        long id = IdWorker.getId();
        boardMapper.insertBoard(id, tenantId, request.productId(),
                RndContext.requireText(request.name(), "name"),
                request.boardType() == null ? "scrum" : request.boardType(), request.sprintId(), userId);
        List<BoardColumnRequest> columns = request.columns();
        if (columns == null || columns.isEmpty()) {
            columns = List.of(
                    new BoardColumnRequest("待处理", List.of("todo"), null),
                    new BoardColumnRequest("进行中", List.of("doing"), 5),
                    new BoardColumnRequest("已完成", List.of("done"), null));
        }
        int sort = 1;
        for (BoardColumnRequest column : columns) {
            boardMapper.insertColumn(IdWorker.getId(), tenantId, id, column.columnName(),
                    writeJson(column.statusCodes() == null ? List.of() : column.statusCodes()),
                    column.wipLimit(), sort++, userId);
        }
        audit.publish("BOARD_CREATED", "create", "devops_board", id, "low");
        return get(id);
    }

    public BoardView get(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> board = boardMapper.findBoard(tenantId, id);
        if (board == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        long productId = Rows.lng(board, "productId");
        List<BoardColumnView> columns = boardMapper.listColumns(tenantId, id, productId,
                        Rows.lng(board, "sprintId"))
                .stream()
                .map(row -> new BoardColumnView(Rows.lng(row, "id"), Rows.str(row, "columnName"),
                        readJson(Rows.str(row, "statusCodes")), Rows.intg(row, "wipLimit"),
                        Rows.intg(row, "sortNo"), Rows.intg(row, "itemCount")))
                .toList();
        return new BoardView(id, productId, Rows.str(board, "name"), Rows.str(board, "boardType"),
                Rows.lng(board, "sprintId"), columns);
    }

    @Transactional
    public BoardView move(long boardId, MoveRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        get(boardId);
        Map<String, Object> column = boardMapper.findColumn(tenantId, request.columnId());
        if (column == null || request.bizId() == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        List<String> statuses = readJson(Rows.str(column, "statusCodes"));
        if (statuses.isEmpty()) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        Integer wipLimit = Rows.intg(column, "wipLimit");
        Integer itemCount = Rows.intg(column, "itemCount");
        if (wipLimit != null && itemCount != null && itemCount >= wipLimit) {
            throw new BizException(RndErrorCode.STATE_CONFLICT);
        }
        String target = statuses.get(0);
        String bizType = request.bizType() == null ? "task" : request.bizType();
        switch (bizType) {
            case "task" -> taskMapper.updateStatus(tenantId, request.bizId(), target, userId);
            case "defect" -> defectMapper.updateStatus(tenantId, request.bizId(), target, userId);
            case "requirement" -> moveRequirement(tenantId, request.bizId(), target, userId);
            default -> throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        audit.publish("BOARD_CARD_MOVED", "move", "devops_board", boardId, "low");
        return get(boardId);
    }

    private void moveRequirement(long tenantId, long requirementId, String target, long userId) {
        Map<String, Object> requirement = requirementMapper.findRequirement(tenantId, requirementId);
        if (requirement == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        String code = switch (target) {
            case "todo" -> "TODO";
            case "doing" -> "DOING";
            case "done" -> "DONE";
            default -> target;
        };
        Map<String, Object> status = productMapper.findStatus(tenantId, Rows.lng(requirement, "productId"), code);
        if (status == null) {
            throw new BizException(RndErrorCode.REQUIREMENT_STATUS_INVALID);
        }
        requirementMapper.updateStatus(tenantId, requirementId, Rows.lng(status, "id"), target, userId);
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException ex) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
    }
}
