package org.springframework.samples.petclinic.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 다른 서비스와 HTTP 통신할 RestClient를 Bean으로 등록한다.
 */
@Configuration
public class RestClientConfig {

    // 상대 서비스가 응답하지 않을 때 무한정 기다리지 않도록 시간 제한을 둔다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                // 리다이렉트 응답은 Gateway가 따라가지 않고 브라우저에게 그대로 전달한다
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}