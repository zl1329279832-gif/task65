# 无人店边缘节点部署指南

本文档覆盖：JAR 直部署、Docker 容器部署、环境变量配置、OCR 超时调优。

---

## 1. 前置要求

| 项目 | 最低版本 | 说明 |
|------|---------|------|
| JDK | 1.8 (Temurin) | Spring Boot 2.2.2 要求 |
| MySQL | 5.7 / 8.0 | 主库，边缘节点建议本地部署 |
| Docker | 20.10+ | 容器部署可选 |
| Maven | 3.6+ | 仅构建时需要 |

---

## 2. 环境变量清单

所有敏感配置通过环境变量注入，**禁止写入代码或提交到仓库**。

### 2.1 数据库

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | `127.0.0.1` | MySQL 主机 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `springbootniyfl` | 数据库名 |
| `DB_USERNAME` | `root` | 数据库用户 |
| `DB_PASSWORD` | *(必填)* | 数据库密码 |

### 2.2 百度 OCR 凭证

| 变量 | 必填 | 说明 |
|------|------|------|
| `BAIDU_OCR_APP_ID` | 是 | 百度 AI 开放平台 APP ID |
| `BAIDU_OCR_API_KEY` | 是 | 百度 AI 开放平台 API Key |
| `BAIDU_OCR_SECRET_KEY` | 是 | 百度 AI 开放平台 Secret Key |

### 2.3 OCR 超时（边缘节点重点调优）

无人店通常部署在商场、社区等网络条件不稳定的环境，OCR 调用容易超时。

| 变量 | 默认值 | 建议值 | 说明 |
|------|--------|--------|------|
| `BAIDU_OCR_CONNECT_TIMEOUT` | `5000` | **8000–10000** | TCP 连接建立超时（毫秒）。4G/弱网环境调大 |
| `BAIDU_OCR_SOCKET_TIMEOUT` | `60000` | **90000–120000** | 数据读取超时（毫秒）。大图/高分辨率商品标签调大 |

**调优经验**：
- 商场 WiFi 稳定环境：connect=5000, socket=60000（默认值即可）
- 4G 物联网卡 / 弱网环境：connect=10000, socket=120000
- 条码/二维码识别（小图）：socket=30000 即可
- 商品货架拍照识别（大图）：socket=90000+

### 2.4 服务端口与 Context Path

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_PORT` | `8080` | 应用监听端口 |
| `SERVER_CONTEXT_PATH` | `/springbootniyfl` | URL 前缀，边缘节点可按需改为 `/shop`、`/store01` 等 |

> **context-path 说明**：前端 Vue 项目中的 API 请求路径以 `/springbootniyfl` 为前缀。
> 若修改 context-path，需同步修改前端 `src/main.js` 或 `vue.config.js` 中的 `baseURL`。
> 若使用 Nginx 反向代理，可在 proxy_pass 层做路径重写，无需改 context-path。

### 2.5 JVM 参数

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JAVA_OPTS` | `-Xms256m -Xmx512m` | JVM 堆内存，按边缘节点内存调整 |
| `SPRING_PROFILES_ACTIVE` | `prod` | 激活 prod profile |

---

## 3. JAR 直部署

### 3.1 构建

```bash
# 在 CI 或开发机上构建（prod profile，跑全量测试）
cd springbootniyfl
mvn -P prod clean package

# 产物: target/springbootniyfl-0.0.1-SNAPSHOT.jar
```

### 3.2 部署

```bash
# 将 JAR 复制到边缘节点
scp target/springbootniyfl-0.0.1-SNAPSHOT.jar user@edge-node:/opt/app/

# 在边缘节点上启动
export DB_HOST=127.0.0.1
export DB_PASSWORD='your_secure_password'
export BAIDU_OCR_APP_ID='your_app_id'
export BAIDU_OCR_API_KEY='your_api_key'
export BAIDU_OCR_SECRET_KEY='your_secret_key'
export BAIDU_OCR_CONNECT_TIMEOUT=8000
export BAIDU_OCR_SOCKET_TIMEOUT=90000

nohup java -Xms256m -Xmx512m \
  -Dspring.profiles.active=prod \
  -jar /opt/app/springbootniyfl-0.0.1-SNAPSHOT.jar \
  > /var/log/springbootniyfl.log 2>&1 &
```

### 3.3 systemd 服务（推荐）

```ini
# /etc/systemd/system/springbootniyfl.service
[Unit]
Description=Unmanned Store Backend
After=network.target mysql.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/app

Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_HOST=127.0.0.1
Environment=DB_PASSWORD=your_secure_password
Environment=BAIDU_OCR_APP_ID=your_app_id
Environment=BAIDU_OCR_API_KEY=your_api_key
Environment=BAIDU_OCR_SECRET_KEY=your_secret_key
Environment=BAIDU_OCR_CONNECT_TIMEOUT=8000
Environment=BAIDU_OCR_SOCKET_TIMEOUT=90000
Environment=SERVER_CONTEXT_PATH=/springbootniyfl

ExecStart=/usr/bin/java -Xms256m -Xmx512m \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /opt/app/springbootniyfl-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable springbootniyfl
sudo systemctl start springbootniyfl
sudo journalctl -u springbootniyfl -f   # 查看日志
```

---

## 4. Docker 容器部署

### 4.1 构建镜像

```bash
# 先编译 JAR
cd springbootniyfl && mvn -P prod clean package -DskipTests

# 构建镜像
docker build -t springbootniyfl:latest .
```

### 4.2 运行容器

```bash
docker run -d \
  --name springbootniyfl \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=mysql-host \
  -e DB_PASSWORD=your_secure_password \
  -e BAIDU_OCR_APP_ID=your_app_id \
  -e BAIDU_OCR_API_KEY=your_api_key \
  -e BAIDU_OCR_SECRET_KEY=your_secret_key \
  -e BAIDU_OCR_CONNECT_TIMEOUT=8000 \
  -e BAIDU_OCR_SOCKET_TIMEOUT=90000 \
  -v /data/upload:/app/static/upload \
  springbootniyfl:latest
```

### 4.3 docker-compose（含 MySQL）

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: springbootniyfl
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: springbootniyfl:latest
    depends_on:
      mysql:
        condition: service_healthy
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: mysql
      DB_PORT: "3306"
      DB_NAME: springbootniyfl
      DB_USERNAME: root
      DB_PASSWORD: ${DB_ROOT_PASSWORD}
      BAIDU_OCR_APP_ID: ${BAIDU_OCR_APP_ID}
      BAIDU_OCR_API_KEY: ${BAIDU_OCR_API_KEY}
      BAIDU_OCR_SECRET_KEY: ${BAIDU_OCR_SECRET_KEY}
      BAIDU_OCR_CONNECT_TIMEOUT: "8000"
      BAIDU_OCR_SOCKET_TIMEOUT: "90000"
    volumes:
      - upload_data:/app/static/upload
    restart: unless-stopped

volumes:
  mysql_data:
  upload_data:
```

---

## 5. Nginx 反向代理（可选）

边缘节点若需对外提供 HTTPS 或统一入口：

```nginx
server {
    listen 443 ssl;
    server_name store01.example.com;

    ssl_certificate     /etc/nginx/ssl/store01.pem;
    ssl_certificate_key /etc/nginx/ssl/store01.key;

    # 保持 context-path 不变，直接透传
    location /springbootniyfl/ {
        proxy_pass http://127.0.0.1:8080/springbootniyfl/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # OCR 图片上传可能较大，放大 body
        client_max_body_size 300m;
    }
}
```

---

## 6. 健康检查 & 监控

| 端点 | 用途 |
|------|------|
| `GET /springbootniyfl/config/list` | 系统配置列表（Dockerfile HEALTHCHECK 用） |
| `GET /springbootniyfl/news/list` | 无鉴权接口，可用作存活探针 |

```bash
# 手动探活
curl -sf http://localhost:8080/springbootniyfl/config/list && echo OK || echo FAIL
```

---

## 7. GitHub Actions Secrets 配置

在仓库 **Settings → Secrets and variables → Actions** 中添加：

| Secret 名称 | 说明 |
|-------------|------|
| `BAIDU_OCR_APP_ID` | 百度 AI APP ID |
| `BAIDU_OCR_API_KEY` | 百度 AI API Key |
| `BAIDU_OCR_SECRET_KEY` | 百度 AI Secret Key |
| `DOCKERHUB_USERNAME` | Docker Hub 用户名（可选，推送镜像用） |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token（可选，推送镜像用） |

---

## 8. 故障排查

### OCR 调用超时

```
java.net.SocketTimeoutException: Read timed out
```

**处理**：调大 `BAIDU_OCR_SOCKET_TIMEOUT`，检查边缘节点网络连通性。

```bash
# 测试到百度 API 的网络延迟
curl -o /dev/null -s -w "time_connect: %{time_connect}\ntime_total: %{time_total}\n" \
  https://aip.baidubce.com/oauth/2.0/token
```

### 数据库连接失败

```
Communications link failure
```

**处理**：检查 `DB_HOST` 是否可达，MySQL 是否允许远程连接，防火墙规则。

### 凭证未配置

```
IllegalStateException: 百度 OCR 凭证未配置
```

**处理**：确认环境变量 `BAIDU_OCR_APP_ID` / `BAIDU_OCR_API_KEY` / `BAIDU_OCR_SECRET_KEY` 已设置。
若使用 systemd，检查 Environment 行；若使用 Docker，检查 `-e` 参数或 `.env` 文件。
