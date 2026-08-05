package com.agentmesh.core.tool;

import java.lang.annotation.*;

/**
 * Tool 权限注解
 * 用于标注 Tool 是否需要认证和角色权限
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ToolPermission {
    /** 允许调用的角色 */
    String[] roles() default {};
    /** 是否需要认证 */
    boolean requiresAuth() default false;
}