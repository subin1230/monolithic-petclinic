package org.springframework.samples.petclinic.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

/**
 * Customer Service 호출을 담당한다.
 * - Primary Path : Customer Service 호출
 * - Fallback Path: 호출이 실패하면 Monolith 호출
 * - Circuit Breaker: 실패가 반복되면 Customer Service 호출 자체를 잠시 차단
 */
@Component
public class CustomerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceClient.class);

    private final RestClient restClient;
    private final String customerServiceUrl;
    private final String monolithUrl;
    private final CircuitBreaker circuitBreaker;

    public CustomerServiceClient(
            RestClient restClient,
            @Value("${services.customer.url}") String customerServiceUrl,
            @Value("${services.monolith.url}") String monolithUrl,
            @Value("${resilience4j.circuitbreaker.instances.customer-service.sliding-window-size}") int slidingWindowSize,
            @Value("${resilience4j.circuitbreaker.instances.customer-service.minimum-number-of-calls}") int minimumNumberOfCalls,
            @Value("${resilience4j.circuitbreaker.instances.customer-service.failure-rate-threshold}") float failureRateThreshold,
            @Value("${resilience4j.circuitbreaker.instances.customer-service.wait-duration-in-open-state}") Duration waitDurationInOpenState,
            @Value("${resilience4j.circuitbreaker.instances.customer-service.permitted-number-of-calls-in-half-open-state}") int permittedCallsInHalfOpenState) {

        this.restClient = restClient;
        this.customerServiceUrl = customerServiceUrl;
        this.monolithUrl = monolithUrl;

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                .build();
        this.circuitBreaker = CircuitBreaker.of("customer-service", config);
    }

    /** Primary Path: Customer Service 호출. 실패하면 Fallback Path(Monolith)로 보낸다. */
    public ResponseEntity<byte[]> call(HttpServletRequest request, byte[] body) {
        try {
            return circuitBreaker.executeSupplier(() -> forward(customerServiceUrl, request, body));
        } catch (Exception e) {
            log.warn("Customer Service 호출 실패 → Monolith로 Fallback (circuit={}, cause={})",
                    circuitBreaker.getState(), e.toString());
            return callMonolith(request, body);
        }
    }

    /** Monolith 호출 (아직 분리되지 않은 기능 + Fallback) */
    public ResponseEntity<byte[]> callMonolith(HttpServletRequest request, byte[] body) {
        return forward(monolithUrl, request, body);
    }

    public String circuitState() {
        return circuitBreaker.getState().name();
    }

    public String pingCustomerService() {
        return ping(customerServiceUrl + "/actuator/health");
    }

    public String pingMonolith() {
        return ping(monolithUrl + "/manage/health");
    }

    /**
     * 받은 요청을 대상 서비스로 그대로 전달하고, 받은 응답을 그대로 돌려준다. (리버스 프록시)
     */
    private ResponseEntity<byte[]> forward(String baseUrl, HttpServletRequest request, byte[] body) {
        String query = request.getQueryString();
        URI uri = URI.create(baseUrl + request.getRequestURI() + (query != null ? "?" + query : ""));

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(uri);
        if (request.getContentType() != null) {
            spec.header(HttpHeaders.CONTENT_TYPE, request.getContentType());
        }
        if (request.getHeader(HttpHeaders.ACCEPT) != null) {
            spec.header(HttpHeaders.ACCEPT, request.getHeader(HttpHeaders.ACCEPT));
        }
        if (body.length > 0) {
            spec.body(body);
        }

        return spec.exchange((req, res) -> {
            // 서버 오류는 '실패'로 보고 Circuit Breaker에 기록되도록 예외를 던진다
            if (res.getStatusCode().is5xxServerError()) {
                throw new IllegalStateException(baseUrl + " 응답 오류: " + res.getStatusCode());
            }

            HttpHeaders headers = new HttpHeaders();
            String contentType = res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (contentType != null) {
                headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            }
            // 리다이렉트 주소에서 내부 서비스 주소를 지워서, 브라우저가 계속 Gateway(8090)로 오게 한다
            String location = res.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location != null) {
                headers.set(HttpHeaders.LOCATION, location.replace(baseUrl, ""));
            }

            byte[] responseBody = res.getBody().readAllBytes();
            return ResponseEntity.status(res.getStatusCode()).headers(headers).body(responseBody);
        });
    }

    private String ping(String url) {
        try {
            return restClient.get().uri(URI.create(url))
                    .exchange((req, res) -> res.getStatusCode().is2xxSuccessful() ? "UP" : "DOWN");
        } catch (Exception e) {
            return "DOWN";
        }
    }
}