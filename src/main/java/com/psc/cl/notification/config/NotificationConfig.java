package com.psc.cl.notification.config;

import com.psc.cl.notification.service.CloudApiWhatsAppSender;
import com.psc.cl.notification.service.WhatsAppSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Registers the notification module's settings, and the client WhatsApp messages are sent through.
 */
@Configuration
@EnableConfigurationProperties({NotificationProperties.class, WhatsAppProperties.class})
public class NotificationConfig {

    /**
     * Sends WhatsApp messages through Meta's WhatsApp Cloud API, with the configured timeouts so a slow
     * or unreachable API cannot hold a request open.
     */
    @Bean
    public WhatsAppSender whatsAppSender(WhatsAppProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new CloudApiWhatsAppSender(restClient, properties);
    }
}
