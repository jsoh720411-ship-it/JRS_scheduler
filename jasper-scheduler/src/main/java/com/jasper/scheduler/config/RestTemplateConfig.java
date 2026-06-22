package com.jasper.scheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${jasper.server.url}")
    private String jasperUrl;

    @Value("${jasper.server.username}")
    private String jasperUsername;

    @Value("${jasper.server.password}")
    private String jasperPassword;

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    public String getJasperUrl()      { return jasperUrl; }
    public String getJasperUsername() { return jasperUsername; }
    public String getJasperPassword() { return jasperPassword; }
}
