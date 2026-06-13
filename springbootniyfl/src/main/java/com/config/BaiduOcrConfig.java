package com.config;

import com.utils.BaiduUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 百度 OCR 凭证配置
 * 从 application.yml（baidu.ocr.*）读取凭证和超时参数，注入到 BaiduUtil 静态字段。
 * 凭证通过环境变量覆盖，禁止硬编码到源码。
 */
@Configuration
public class BaiduOcrConfig {

    private static final Logger log = LoggerFactory.getLogger(BaiduOcrConfig.class);

    @Value("${baidu.ocr.app-id:}")
    private String appId;

    @Value("${baidu.ocr.api-key:}")
    private String apiKey;

    @Value("${baidu.ocr.secret-key:}")
    private String secretKey;

    @Value("${baidu.ocr.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${baidu.ocr.socket-timeout:60000}")
    private int socketTimeout;

    @PostConstruct
    public void init() {
        BaiduUtil.init(appId, apiKey, secretKey, connectTimeout, socketTimeout);

        if (appId.isEmpty() || apiKey.isEmpty() || secretKey.isEmpty()) {
            log.warn("百度 OCR 凭证未配置（baidu.ocr.app-id / api-key / secret-key），"
                   + "OCR 识别功能将不可用。如需启用请配置 application.yml 或设置环境变量 "
                   + "BAIDU_OCR_APP_ID / BAIDU_OCR_API_KEY / BAIDU_OCR_SECRET_KEY");
        } else {
            log.info("百度 OCR 凭证已加载（app-id={}...）", appId.substring(0, Math.min(4, appId.length())));
        }
    }
}
