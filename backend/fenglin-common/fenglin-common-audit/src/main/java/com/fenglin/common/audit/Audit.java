package com.fenglin.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    String eventType();

    String module();

    String action();

    RiskLevel riskLevel() default RiskLevel.NORMAL;

    enum RiskLevel {
        LOW,
        NORMAL,
        HIGH
    }
}
