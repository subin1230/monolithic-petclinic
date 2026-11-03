package org.springframework.samples.petclinic.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.gateway.client.CustomerServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 라우팅 허브. 모든 요청을 받아 알맞은 서비스로 전달한다.
 */
@RestController
public class GatewayController {

    private final CustomerServiceClient customerClient;
    private final boolean useCustomerService;

    public GatewayController(CustomerServiceClient customerClient,
                             @Value("${feature.toggle.use-customer-service}") boolean useCustomerService) {
        this.customerClient = customerClient;
        this.useCustomerService = useCustomerService;
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> route(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        byte[] payload = (body != null) ? body : new byte[0];
        String path = request.getRequestURI();

        // 1. Owner/Pet 요청: Feature Flag에 따라 분기
        if (useCustomerService && isCustomerPath(path)) {
            return customerClient.call(request, payload);
        }
        // 2. Vet/Visit/메인 페이지/CSS 등: 아직 분리되지 않았으니 항상 Monolith
        return customerClient.callMonolith(request, payload);
    }

    private boolean isCustomerPath(String path) {
        // URL은 /owners 아래에 있지만 방문 기록(Visit)은 아직 Monolith 소속
        if (path.contains("/visits")) {
            return false;
        }
        return path.equals("/owners") || path.startsWith("/owners/");
    }

    /** 3. Health Check: 시스템 상태 정보 */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("gateway", "UP");
        status.put("customerService", customerClient.pingCustomerService());
        status.put("monolith", customerClient.pingMonolith());
        status.put("circuitBreaker", customerClient.circuitState());
        status.put("featureToggle", useCustomerService ? "Customer Service" : "Monolith");
        return status;
    }

    @GetMapping("/architecture")
    public Map<String, String> architecture() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/owners/**", useCustomerService ? "Customer Service (8081), 실패 시 Monolith" : "Monolith (8080)");
        routes.put("/owners/*/pets/*/visits/**", "Monolith (8080)");
        routes.put("/vets, /management/**, /", "Monolith (8080)");
        routes.put("/resources/**, /webjars/**", "Monolith (8080)");
        return routes;
    }
}