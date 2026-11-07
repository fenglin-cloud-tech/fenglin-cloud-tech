package com.fenglin.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口允许的调用端类型。P1 由资源服务器拦截器统一校验；P0 先提供契约。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireClientType {

    ClientType[] value();
}
