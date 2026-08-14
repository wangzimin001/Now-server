package com.wangzimin.now.domain;

/**
 * 定义本地开发环境允许的跨域策略。
 *
 * <p>移动端和本地 Web 调试会从回环地址访问后端，生产部署则应由网关提供同源访问。
 * 路径、来源、方法和请求头统一放在枚举中，Web 配置不再散落协议字符串。</p>
 */
public enum CorsPolicy {
    LOCAL_DEVELOPMENT(
            "/api/**",
            new String[] { "http://127.0.0.1:*", "http://localhost:*" },
            new String[] { "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS" },
            new String[] { "*" });

    private final String path;
    private final String[] origins;
    private final String[] methods;
    private final String[] headers;

    /**
     * 创建一个完整的跨域策略。
     *
     * @param path 生效的接口路径
     * @param origins 允许的来源模式
     * @param methods 允许的 HTTP 方法
     * @param headers 允许的请求头
     */
    CorsPolicy(String path, String[] origins, String[] methods, String[] headers) {
        this.path = path;
        this.origins = origins.clone();
        this.methods = methods.clone();
        this.headers = headers.clone();
    }

    /** @return 跨域策略生效路径 */
    public String path() {
        return path;
    }

    /** @return 防御性复制后的来源模式 */
    public String[] origins() {
        return origins.clone();
    }

    /** @return 防御性复制后的 HTTP 方法 */
    public String[] methods() {
        return methods.clone();
    }

    /** @return 防御性复制后的请求头规则 */
    public String[] headers() {
        return headers.clone();
    }
}
