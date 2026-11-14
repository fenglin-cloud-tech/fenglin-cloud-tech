package com.fenglin.common.web;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 必填查询参数的空值校验。
 *
 * <p>Spring 的 {@code @RequestParam(required = true)} 只拦截参数缺失的情况；当请求带上参数名但值为空
 * （{@code ?periodCode=}）时不会报错，空串会一路进入业务逻辑，容易得到"查不到数据"这类静默错误结果。
 * 这里在进入控制器前统一把"必填 String 参数为空串"转成 400 业务错误，避免各服务各写一遍校验。</p>
 */
public class RequiredParamInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
            if (annotation == null || !annotation.required()) {
                continue;
            }
            if (!CharSequence.class.isAssignableFrom(parameter.getParameterType())) {
                continue;
            }
            String name = resolveName(annotation, parameter);
            String[] values = request.getParameterValues(name);
            if (values == null || values.length == 0) {
                continue;
            }
            boolean allBlank = true;
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    allBlank = false;
                    break;
                }
            }
            if (allBlank) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "缺少必填参数: " + name);
            }
        }
        return true;
    }

    private String resolveName(RequestParam annotation, MethodParameter parameter) {
        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }
        if (annotation.value() != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return parameter.getParameterName();
    }
}
