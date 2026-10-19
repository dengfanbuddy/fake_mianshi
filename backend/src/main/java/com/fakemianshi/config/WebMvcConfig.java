package com.fakemianshi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC 配置。
 *
 * <p><b>API 前缀：</b>过去后端通过 {@code server.servlet.context-path=/api} 把接口统一暴露为 {@code /api/*}。
 * 为了让打包后能在同一端口同时服务前端静态页面（根路径 {@code /}）与后端接口（{@code /api/*}），
 * 这里移除 context-path，改为给所有 {@link RestController} 补充 {@code /api} 前缀。
 * 前端 axios baseURL 与开发代理仍为 {@code /api}，路径语义保持一致。
 *
 * <p><b>SPA 静态资源：</b>前端构建产物位于 classpath:/static/。真实存在的静态资源（js/css/ico 等）直接返回；
 * 其余非 {@code /api} 的路径回退到 index.html，以支持 Vue Router 的 history 模式深链接与刷新。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                clazz -> AnnotatedElementUtils.hasAnnotation(clazz, RestController.class));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 未知的 /api/* 保持 404 语义；其余路径回退到 SPA 入口（history 模式深链接/刷新）
                        if (!resourcePath.startsWith("api/")) {
                            return new ClassPathResource("static/index.html");
                        }
                        return null;
                    }
                });
    }
}
