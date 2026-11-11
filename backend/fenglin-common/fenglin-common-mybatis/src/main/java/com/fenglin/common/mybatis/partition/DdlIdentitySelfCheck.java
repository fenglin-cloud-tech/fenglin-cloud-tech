package com.fenglin.common.mybatis.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

/**
 * DDL 身份启动自检的 fail-fast 出口：任一条件不满足即记录 ERROR 并退出进程，不允许只打日志继续运行。
 */
public final class DdlIdentitySelfCheck {

    private static final Logger log = LoggerFactory.getLogger(DdlIdentitySelfCheck.class);

    private static final int EXIT_CODE = 1;

    private DdlIdentitySelfCheck() {
    }

    public static void verifyOrExit(PartitionMaintenanceSupport support, String schema,
                                    ApplicationContext applicationContext, String serviceName) {
        try {
            support.verifyIdentity(schema);
        } catch (RuntimeException ex) {
            log.error("[{}] DDL 身份自检失败，服务退出（不降级运行）：{}", serviceName, ex.getMessage(), ex);
            int exitCode = SpringApplication.exit(applicationContext, () -> EXIT_CODE);
            System.exit(exitCode);
        }
    }
}
