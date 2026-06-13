package com;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Boot 启动冒烟测试
 *
 * 验证应用上下文能正常加载（含数据源、MyBatis、百度 OCR 配置等所有 Bean）。
 * 需要 MySQL 数据库和百度 OCR 凭证，因此标记为 integration/smoke。
 *
 * - dev profile: 被 maven-surefire-plugin excludedGroups=integration 跳过
 * - prod profile (CI): 由 GitHub Actions 提供 MySQL service + secrets，正常执行
 *
 * 运行方式：
 *   mvn test -P prod \
 *     -Dspring.datasource.url=jdbc:mysql://localhost:3306/test_db \
 *     -Dspring.datasource.username=root \
 *     -Dspring.datasource.password=secret \
 *     -Dbaidu.ocr.app-id=xxx -Dbaidu.ocr.api-key=xxx -Dbaidu.ocr.secret-key=xxx
 */
@SpringBootTest
@Tag("integration")
@Tag("smoke")
class SpringbootSchemaApplicationTests {

    @Test
    void contextLoads() {
        // 上下文能加载即表示所有 Bean 装配正常，冒烟通过
    }
}
