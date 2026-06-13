# =====================================================
# 多阶段 Dockerfile — 无人店边缘节点部署
# 基础镜像: eclipse-temurin:8 (JDK 8 兼容)
# =====================================================

FROM eclipse-temurin:8-jre-alpine AS runtime

LABEL maintainer="无人店运维组"
LABEL description="无人智慧超市管理系统"

# 时区 & curl（健康检查用）
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 非 root 运行
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# 复制构建产物（由 CI 预编译）
COPY target/*.jar app.jar

# 上传文件持久化目录
RUN mkdir -p /app/static/upload && chown -R app:app /app
VOLUME ["/app/static/upload"]

USER app

# ===== 环境变量默认值 =====
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    SERVER_CONTEXT_PATH=/springbootniyfl \
    DB_HOST=mysql \
    DB_PORT=3306 \
    DB_NAME=springbootniyfl \
    BAIDU_OCR_CONNECT_TIMEOUT=8000 \
    BAIDU_OCR_SOCKET_TIMEOUT=90000

EXPOSE ${SERVER_PORT}

# 健康检查：每 30s 探一次，连续 3 次失败则 unhealthy
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
    CMD curl -sf http://localhost:${SERVER_PORT}${SERVER_CONTEXT_PATH}/config/list || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
