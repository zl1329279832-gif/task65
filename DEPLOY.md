# 无人店边缘节点部署指南

## 1. 环境要求

- JDK 1.8+
- MySQL 5.7+
- 网络可达百度 AI 平台（`aip.baidubce.com`）

## 2. 环境变量配置

部署前必须设置以下环境变量（**禁止将凭证写入代码或配置文件提交到仓库**）：

### 百度 OCR 凭证（必需）

```bash
export BAIDU_OCR_APP_ID=你的AppId
export BAIDU_OCR_API_KEY=你的ApiKey
export BAIDU_OCR_SECRET_KEY=你的SecretKey
```

### OCR 超时调优（可选）

边缘节点网络条件可能较差，可适当放大超时：

```bash
# 连接超时，默认 5000ms，弱网建议 10000ms
export BAIDU_OCR_CONN_TIMEOUT=10000

# 读取超时，默认 60000ms，弱网建议 120000ms
export BAIDU_OCR_SOCK_TIMEOUT=120000
```

### 数据库（按需覆盖）

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://数据库地址:3306/springbootniyfl?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8
export SPRING_DATASOURCE_USERNAME=数据库用户
export SPRING_DATASOURCE_PASSWORD=数据库密码
```

## 3. context-path 配置

默认 context-path 为 `/springbootniyfl`。如需按门店自定义（例如 `/store-001`），有两种方式：

### 方式 A：环境变量

```bash
export SERVER_SERVLET_CONTEXT_PATH=/store-001
```

### 方式 B：启动参数

```bash
java -jar springbootniyfl.jar --server.servlet.context-path=/store-001
```

端口同理：

```bash
export SERVER_PORT=9090
# 或
java -jar springbootniyfl.jar --server.port=9090
```

## 4. 构建

### 开发环境（跳过需密钥的集成测试）

```bash
cd springbootniyfl
mvn clean package -P dev
```

### 生产环境（必须通过全部单元测试 + 冒烟测试）

```bash
cd springbootniyfl
mvn clean package -P prod
```

### WAR 包（部署到外部 Tomcat）

```bash
cd springbootniyfl
mvn clean package -P prod -f pom-war.xml
```

## 5. 启动

### JAR 方式（推荐）

```bash
java -jar target/springbootniyfl-0.0.1-SNAPSHOT.jar
```

带 Spring profile 激活：

```bash
java -jar target/springbootniyfl-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Docker 方式

```bash
# 构建镜像
docker build -t springbootniyfl:latest .

# 运行容器
docker run -d \
  --name springbootniyfl \
  -p 8080:8080 \
  -e BAIDU_OCR_APP_ID=你的AppId \
  -e BAIDU_OCR_API_KEY=你的ApiKey \
  -e BAIDU_OCR_SECRET_KEY=你的SecretKey \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://db-host:3306/springbootniyfl?useUnicode=true\&characterEncoding=utf-8\&serverTimezone=GMT%2B8 \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=密码 \
  -e SERVER_SERVLET_CONTEXT_PATH=/store-001 \
  springbootniyfl:latest
```

## 6. 健康检查

启动后验证：

```bash
curl http://localhost:8080/springbootniyfl/
```

> 若自定义了 context-path，替换路径中的 `/springbootniyfl` 为实际值。

## 7. CI/CD

GitHub Actions 流水线已配置在 `.github/workflows/ci.yml`：

- push/PR 到 `master` 自动触发
- 使用 `prod` profile 构建并运行测试
- Maven 依赖缓存加速
- 百度凭证通过 GitHub Secrets 注入

### 需要配置的 GitHub Secrets

| Secret 名称 | 说明 |
|---|---|
| `BAIDU_OCR_APP_ID` | 百度 OCR 应用 ID |
| `BAIDU_OCR_API_KEY` | 百度 OCR API Key |
| `BAIDU_OCR_SECRET_KEY` | 百度 OCR Secret Key |
| `DOCKER_REGISTRY` | Docker 镜像仓库地址（可选，仅 Docker 推送时需要） |
| `DOCKER_USERNAME` | Docker 仓库用户名（可选） |
| `DOCKER_PASSWORD` | Docker 仓库密码（可选） |

## 8. 边缘节点注意事项

- 每台边缘节点应通过环境变量或启动参数独立配置 `context-path` 和数据库连接
- 百度 OCR 有 QPS 限制，多节点共用同一 AppId 时注意并发控量
- 建议通过 systemd 或 supervisor 托管 Java 进程，确保崩溃自动重启
- 日志默认输出到 stdout，可通过 `--logging.file.path=/var/log/springbootniyfl` 持久化
