package com.fenglin.finance.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * D3 资产类别、卡片、折旧、处置、盘点、IT 资产 Mapper。
 */
@Mapper
public interface AssetMapper {

    @Insert("""
            INSERT INTO fin_asset_category
                (id, tenant_id, category_code, category_name, useful_life_months, residual_rate,
                 depreciation_method, asset_account_code, depreciation_account_code, expense_account_code,
                 status, created_by)
            VALUES (#{id}, #{tenantId}, #{categoryCode}, #{categoryName}, #{usefulLifeMonths}, #{residualRate},
                    #{depreciationMethod}, #{assetAccountCode}, #{depreciationAccountCode},
                    #{expenseAccountCode}, 'ENABLED', #{userId})
            """)
    void insertCategory(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("categoryCode") String categoryCode, @Param("categoryName") String categoryName,
                        @Param("usefulLifeMonths") int usefulLifeMonths, @Param("residualRate") BigDecimal residualRate,
                        @Param("depreciationMethod") String depreciationMethod,
                        @Param("assetAccountCode") String assetAccountCode,
                        @Param("depreciationAccountCode") String depreciationAccountCode,
                        @Param("expenseAccountCode") String expenseAccountCode, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", category_code AS "categoryCode", category_name AS "categoryName",
                   useful_life_months AS "usefulLifeMonths", residual_rate AS "residualRate",
                   depreciation_method AS "depreciationMethod", asset_account_code AS "assetAccountCode",
                   depreciation_account_code AS "depreciationAccountCode",
                   expense_account_code AS "expenseAccountCode", status AS "status"
            FROM fin_asset_category
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCategory(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", category_code AS "categoryCode", category_name AS "categoryName",
                   useful_life_months AS "usefulLifeMonths", residual_rate AS "residualRate",
                   depreciation_method AS "depreciationMethod", asset_account_code AS "assetAccountCode",
                   depreciation_account_code AS "depreciationAccountCode",
                   expense_account_code AS "expenseAccountCode", status AS "status"
            FROM fin_asset_category
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY category_code
            """)
    List<Map<String, Object>> listCategories(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO fin_asset_card
                (id, tenant_id, asset_no, asset_name, category_id, specification, location, dept_id,
                 custodian_id, enable_date, original_value, accumulated_depreciation, net_value,
                 residual_rate, useful_life_months, depreciation_method, status, it_asset_flag, source,
                 created_by)
            VALUES (#{id}, #{tenantId}, #{assetNo}, #{assetName}, #{categoryId}, #{specification,jdbcType=VARCHAR},
                    #{location,jdbcType=VARCHAR}, #{deptId,jdbcType=BIGINT}, #{custodianId,jdbcType=BIGINT},
                    #{enableDate}, #{originalValue}, 0, #{originalValue}, #{residualRate}, #{usefulLifeMonths},
                    #{depreciationMethod}, 'IN_USE', #{itAssetFlag}, #{source}, #{userId})
            """)
    void insertCard(@Param("id") long id, @Param("tenantId") long tenantId,
                    @Param("assetNo") String assetNo, @Param("assetName") String assetName,
                    @Param("categoryId") long categoryId, @Param("specification") String specification,
                    @Param("location") String location, @Param("deptId") Long deptId,
                    @Param("custodianId") Long custodianId, @Param("enableDate") LocalDate enableDate,
                    @Param("originalValue") BigDecimal originalValue, @Param("residualRate") BigDecimal residualRate,
                    @Param("usefulLifeMonths") int usefulLifeMonths,
                    @Param("depreciationMethod") String depreciationMethod,
                    @Param("itAssetFlag") boolean itAssetFlag, @Param("source") String source,
                    @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", asset_no AS "assetNo", asset_name AS "assetName", category_id AS "categoryId",
                   specification AS "specification", location AS "location", dept_id AS "deptId",
                   custodian_id AS "custodianId", enable_date::text AS "enableDate",
                   original_value AS "originalValue", accumulated_depreciation AS "accumulatedDepreciation",
                   net_value AS "netValue", residual_rate AS "residualRate",
                   useful_life_months AS "usefulLifeMonths", depreciation_method AS "depreciationMethod",
                   status AS "status", it_asset_flag AS "itAssetFlag", source AS "source"
            FROM fin_asset_card
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCard(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", asset_no AS "assetNo", asset_name AS "assetName", category_id AS "categoryId",
                   location AS "location", dept_id AS "deptId", custodian_id AS "custodianId",
                   enable_date::text AS "enableDate", original_value AS "originalValue",
                   accumulated_depreciation AS "accumulatedDepreciation", net_value AS "netValue",
                   depreciation_method AS "depreciationMethod", useful_life_months AS "usefulLifeMonths",
                   status AS "status", it_asset_flag AS "itAssetFlag"
            FROM fin_asset_card
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listCards(@Param("tenantId") long tenantId,
                                        @Param("status") String status,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id AS "id", asset_no AS "assetNo", asset_name AS "assetName", category_id AS "categoryId",
                   enable_date::text AS "enableDate", original_value AS "originalValue",
                   accumulated_depreciation AS "accumulatedDepreciation", net_value AS "netValue",
                   residual_rate AS "residualRate", useful_life_months AS "usefulLifeMonths",
                   depreciation_method AS "depreciationMethod", status AS "status"
            FROM fin_asset_card
            WHERE tenant_id = #{tenantId} AND status <> 'DISPOSED' AND enable_date < #{periodStart}
              AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listDepreciableCards(@Param("tenantId") long tenantId,
                                                   @Param("periodStart") LocalDate periodStart);

    @Update("""
            UPDATE fin_asset_card
            SET accumulated_depreciation = #{accumulated}, net_value = #{netValue}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateCardDepreciation(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("accumulated") BigDecimal accumulated, @Param("netValue") BigDecimal netValue);

    @Update("""
            UPDATE fin_asset_card
            SET status = #{status}, version = version + 1,
                dept_id = COALESCE(#{deptId,jdbcType=BIGINT}, dept_id),
                location = COALESCE(#{location,jdbcType=VARCHAR}, location),
                custodian_id = COALESCE(#{custodianId,jdbcType=BIGINT}, custodian_id)
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateCardStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("status") String status, @Param("deptId") Long deptId,
                         @Param("location") String location, @Param("custodianId") Long custodianId);

    @Select("""
            SELECT count(1) FROM fin_depreciation_schedule
            WHERE tenant_id = #{tenantId} AND asset_id = #{assetId} AND period_code = #{periodCode}
              AND deleted = 0
            """)
    long countSchedule(@Param("tenantId") long tenantId, @Param("assetId") long assetId,
                       @Param("periodCode") String periodCode);

    @Insert("""
            INSERT INTO fin_depreciation_schedule
                (id, tenant_id, batch_no, asset_id, period_code, method, original_value,
                 depreciation_amount, accumulated_after, net_value_after, voucher_id, status, created_by)
            VALUES (#{id}, #{tenantId}, #{batchNo}, #{assetId}, #{periodCode}, #{method}, #{originalValue},
                    #{depreciationAmount}, #{accumulatedAfter}, #{netValueAfter}, #{voucherId,jdbcType=BIGINT},
                    'GENERATED', #{userId})
            """)
    void insertSchedule(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("batchNo") String batchNo, @Param("assetId") long assetId,
                        @Param("periodCode") String periodCode, @Param("method") String method,
                        @Param("originalValue") BigDecimal originalValue,
                        @Param("depreciationAmount") BigDecimal depreciationAmount,
                        @Param("accumulatedAfter") BigDecimal accumulatedAfter,
                        @Param("netValueAfter") BigDecimal netValueAfter, @Param("voucherId") Long voucherId,
                        @Param("userId") long userId);

    @Insert("""
            INSERT INTO fin_asset_change
                (id, tenant_id, change_no, asset_id, change_type, before_json, after_json, gain_loss,
                 voucher_id, status, operator_id, occurred_at, created_by)
            VALUES (#{id}, #{tenantId}, #{changeNo}, #{assetId}, #{changeType},
                    CAST(#{beforeJson} AS jsonb), CAST(#{afterJson} AS jsonb), #{gainLoss},
                    #{voucherId,jdbcType=BIGINT}, 'EFFECTED', #{userId}, now(), #{userId})
            """)
    void insertChange(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("changeNo") String changeNo, @Param("assetId") long assetId,
                      @Param("changeType") String changeType, @Param("beforeJson") String beforeJson,
                      @Param("afterJson") String afterJson, @Param("gainLoss") BigDecimal gainLoss,
                      @Param("voucherId") Long voucherId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO fin_asset_stocktaking
                (id, tenant_id, stocktaking_no, stocktaking_name, scope_type, dept_id, plan_start_date,
                 plan_end_date, status, created_by)
            VALUES (#{id}, #{tenantId}, #{stocktakingNo}, #{stocktakingName}, #{scopeType},
                    #{deptId,jdbcType=BIGINT}, #{planStartDate,jdbcType=DATE}, #{planEndDate,jdbcType=DATE},
                    'IN_PROGRESS', #{userId})
            """)
    void insertStocktaking(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("stocktakingNo") String stocktakingNo,
                           @Param("stocktakingName") String stocktakingName,
                           @Param("scopeType") String scopeType, @Param("deptId") Long deptId,
                           @Param("planStartDate") LocalDate planStartDate,
                           @Param("planEndDate") LocalDate planEndDate, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", stocktaking_no AS "stocktakingNo", stocktaking_name AS "stocktakingName",
                   scope_type AS "scopeType", dept_id AS "deptId", plan_start_date::text AS "planStartDate",
                   plan_end_date::text AS "planEndDate", actual_end_date::text AS "actualEndDate",
                   total_count AS "totalCount", normal_count AS "normalCount",
                   surplus_count AS "surplusCount", deficit_count AS "deficitCount", status AS "status"
            FROM fin_asset_stocktaking
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findStocktaking(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", stocktaking_no AS "stocktakingNo", stocktaking_name AS "stocktakingName",
                   scope_type AS "scopeType", total_count AS "totalCount", normal_count AS "normalCount",
                   surplus_count AS "surplusCount", deficit_count AS "deficitCount", status AS "status"
            FROM fin_asset_stocktaking
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listStocktakings(@Param("tenantId") long tenantId,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id AS "id", asset_no AS "assetNo", asset_name AS "assetName", location AS "location",
                   dept_id AS "deptId", custodian_id AS "custodianId"
            FROM fin_asset_card
            WHERE tenant_id = #{tenantId} AND status <> 'DISPOSED' AND deleted = 0
              AND (CAST(#{deptId,jdbcType=BIGINT} AS bigint) IS NULL OR dept_id = #{deptId})
            ORDER BY id
            """)
    List<Map<String, Object>> listCardsForScope(@Param("tenantId") long tenantId,
                                                @Param("deptId") Long deptId);

    @Insert("""
            INSERT INTO fin_asset_stocktaking_item
                (id, tenant_id, stocktaking_id, asset_id, asset_no, asset_name, expected_location,
                 actual_location, expected_custodian_id, actual_custodian_id, result, remark, counted_at,
                 counted_by, created_by)
            VALUES (#{id}, #{tenantId}, #{stocktakingId}, #{assetId}, #{assetNo}, #{assetName},
                    #{expectedLocation,jdbcType=VARCHAR}, #{expectedLocation,jdbcType=VARCHAR},
                    #{expectedCustodianId,jdbcType=BIGINT}, #{expectedCustodianId,jdbcType=BIGINT},
                    'NOT_FOUND', NULL, NULL, NULL, #{userId})
            ON CONFLICT (tenant_id, stocktaking_id, asset_id) WHERE deleted = 0 DO NOTHING
            """)
    int insertStocktakingItemSnapshot(@Param("id") long id, @Param("tenantId") long tenantId,
                                      @Param("stocktakingId") long stocktakingId, @Param("assetId") long assetId,
                                      @Param("assetNo") String assetNo, @Param("assetName") String assetName,
                                      @Param("expectedLocation") String expectedLocation,
                                      @Param("expectedCustodianId") Long expectedCustodianId,
                                      @Param("userId") long userId);

    @Update("""
            UPDATE fin_asset_stocktaking_item
            SET actual_location = #{actualLocation,jdbcType=VARCHAR},
                actual_custodian_id = #{actualCustodianId,jdbcType=BIGINT},
                result = #{result}, remark = #{remark,jdbcType=VARCHAR}, counted_at = now(),
                counted_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND stocktaking_id = #{stocktakingId} AND asset_id = #{assetId}
              AND deleted = 0
            """)
    int countStocktakingItem(@Param("tenantId") long tenantId, @Param("stocktakingId") long stocktakingId,
                             @Param("assetId") long assetId, @Param("actualLocation") String actualLocation,
                             @Param("actualCustodianId") Long actualCustodianId,
                             @Param("result") String result, @Param("remark") String remark,
                             @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", asset_id AS "assetId", asset_no AS "assetNo", asset_name AS "assetName",
                   expected_location AS "expectedLocation", actual_location AS "actualLocation",
                   expected_custodian_id AS "expectedCustodianId",
                   actual_custodian_id AS "actualCustodianId", result AS "result", remark AS "remark",
                   (extract(epoch FROM counted_at) * 1000)::bigint AS "countedAt"
            FROM fin_asset_stocktaking_item
            WHERE tenant_id = #{tenantId} AND stocktaking_id = #{stocktakingId} AND deleted = 0
            ORDER BY asset_no, id
            """)
    List<Map<String, Object>> listStocktakingItems(@Param("tenantId") long tenantId,
                                                   @Param("stocktakingId") long stocktakingId);

    @Update("""
            UPDATE fin_asset_stocktaking s
            SET total_count = (SELECT count(1) FROM fin_asset_stocktaking_item i
                               WHERE i.tenant_id = s.tenant_id AND i.stocktaking_id = s.id AND i.deleted = 0),
                normal_count = (SELECT count(1) FROM fin_asset_stocktaking_item i
                                WHERE i.tenant_id = s.tenant_id AND i.stocktaking_id = s.id AND i.deleted = 0
                                  AND i.result = 'NORMAL'),
                surplus_count = (SELECT count(1) FROM fin_asset_stocktaking_item i
                                 WHERE i.tenant_id = s.tenant_id AND i.stocktaking_id = s.id AND i.deleted = 0
                                   AND i.result = 'SURPLUS'),
                deficit_count = (SELECT count(1) FROM fin_asset_stocktaking_item i
                                 WHERE i.tenant_id = s.tenant_id AND i.stocktaking_id = s.id AND i.deleted = 0
                                   AND i.result IN ('DEFICIT', 'NOT_FOUND')),
                version = version + 1
            WHERE s.tenant_id = #{tenantId} AND s.id = #{stocktakingId} AND s.deleted = 0
            """)
    int recountStocktaking(@Param("tenantId") long tenantId, @Param("stocktakingId") long stocktakingId);

    @Update("""
            UPDATE fin_asset_stocktaking SET status = 'COMPLETED', actual_end_date = #{actualEndDate},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'IN_PROGRESS' AND deleted = 0
            """)
    int completeStocktaking(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("actualEndDate") LocalDate actualEndDate);

    @Insert("""
            INSERT INTO fin_it_asset
                (id, tenant_id, asset_id, device_type, brand_model, serial_no, ip_address, mac_address,
                 os_version, software_list, borrow_status, cmdb_ci_id, sync_status, created_by)
            VALUES (#{id}, #{tenantId}, #{assetId}, #{deviceType}, #{brandModel,jdbcType=VARCHAR},
                    #{serialNo,jdbcType=VARCHAR}, #{ipAddress,jdbcType=VARCHAR}, #{macAddress,jdbcType=VARCHAR},
                    #{osVersion,jdbcType=VARCHAR}, CAST(#{softwareList,jdbcType=VARCHAR} AS jsonb), 'AVAILABLE',
                    #{cmdbCiId,jdbcType=VARCHAR}, 'PENDING', #{userId})
            """)
    void insertItAsset(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("assetId") long assetId, @Param("deviceType") String deviceType,
                       @Param("brandModel") String brandModel, @Param("serialNo") String serialNo,
                       @Param("ipAddress") String ipAddress, @Param("macAddress") String macAddress,
                       @Param("osVersion") String osVersion, @Param("softwareList") String softwareList,
                       @Param("cmdbCiId") String cmdbCiId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", asset_id AS "assetId", device_type AS "deviceType",
                   brand_model AS "brandModel", serial_no AS "serialNo", ip_address AS "ipAddress",
                   mac_address AS "macAddress", os_version AS "osVersion", software_list::text AS "softwareList",
                   borrow_status AS "borrowStatus", borrower_id AS "borrowerId",
                   borrow_date::text AS "borrowDate", due_date::text AS "dueDate",
                   cmdb_ci_id AS "cmdbCiId", sync_status AS "syncStatus"
            FROM fin_it_asset
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findItAsset(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", asset_id AS "assetId", device_type AS "deviceType",
                   brand_model AS "brandModel", serial_no AS "serialNo", ip_address AS "ipAddress",
                   borrow_status AS "borrowStatus", borrower_id AS "borrowerId", due_date::text AS "dueDate",
                   cmdb_ci_id AS "cmdbCiId", sync_status AS "syncStatus"
            FROM fin_it_asset
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listItAssets(@Param("tenantId") long tenantId,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE fin_it_asset
            SET borrow_status = 'BORROWED', borrower_id = #{borrowerId}, borrow_date = #{borrowDate},
                due_date = #{dueDate}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND borrow_status = 'AVAILABLE' AND deleted = 0
            """)
    int borrowItAsset(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("borrowerId") long borrowerId, @Param("borrowDate") LocalDate borrowDate,
                      @Param("dueDate") LocalDate dueDate);

    @Update("""
            UPDATE fin_it_asset
            SET borrow_status = 'AVAILABLE', borrower_id = NULL, borrow_date = NULL, due_date = NULL,
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND borrow_status = 'BORROWED' AND deleted = 0
            """)
    int returnItAsset(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE fin_it_asset
            SET cmdb_ci_id = #{cmdbCiId}, sync_status = #{syncStatus}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int syncItAsset(@Param("tenantId") long tenantId, @Param("id") long id,
                    @Param("cmdbCiId") String cmdbCiId, @Param("syncStatus") String syncStatus);
}
