package no.skatteetaten.aurora.openshift.reference.springboot.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

/*
 * An example controller that shows how to do a REST call and how to do an operation with a operations metrics
 * There should be a metric called http_client_requests http_server_requests and operations
 */
@RestController()
public class KeepAliveController implements HealthIndicator {
    public static final int MINIMUM_POST_SIZE = 2000;
    private final String podName;
    private final String auroraVersion;
    private final int postSize;
    private final RestTemplate restTemplate;
    private final Logger logger = LoggerFactory.getLogger(KeepAliveController.class);
    private final int number;
    private final int wait;
    private final String psatPath;

    public KeepAliveController(
        @Value("${pod.name:localhost}") String podName,
        @Value("${aurora.version:local-dev}") String auroraVersion,
        @Value("${keepalive.wait:200}") int wait,
        @Value("${keepalive.number:1}") int number,
        @Value("${keepalive.max.postsizebyte:500000}") int postSize,
        @Value("${pod.psatPath:/tmp/service-name-token}") String psatPath, // TODO Fix default
        RestTemplate restTemplate) {

        this.wait = wait;
        this.number = number;
        this.postSize = postSize;
        this.psatPath = psatPath;
        this.restTemplate = restTemplate;
        this.podName = podName;
        this.auroraVersion = auroraVersion;
    }

    @GetMapping("/keepalive/server")
    public Map<String, Object> serveTest(HttpServletResponse response) {
        logger.info("Received request");
        return Map.of(
            "version", auroraVersion,
            "name", podName
        );
    }

    @PostMapping(value = "/keepalive/post", produces = MediaType.TEXT_PLAIN_VALUE)
    public void post(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int count = 0;
        try (InputStream is = request.getInputStream()) {
            int input = 0;
            while (input != -1) {
                count++;
                input = is.read();
            }
        }
        response.getWriter().println("End - read " + count + " bytes successfully.");
    }

    @GetMapping("/keepalive/clientpost")
    public void clientPost() {
        for (int i = 0; i < number; i++) {
            String requestId = UUID.randomUUID().toString();
            MDC.put("requestId", requestId);

            StopWatch watch = new StopWatch();
            watch.start();
            try {
                Thread.sleep(wait);
                String randomText = "x".repeat(Math.max(MINIMUM_POST_SIZE, new Random().nextInt(postSize)));
                ResponseEntity<String> entity =
                    restTemplate.postForEntity("/keepalive/post", randomText, String.class);
                watch.stop();
                long totalTimeMillis = watch.getTotalTimeMillis();
                List<String> keepAlive = entity.getHeaders().get("Keep-Alive");
                List<String> connection = entity.getHeaders().get("Connection");
                logger.info("response={} server={} time={}ms keepalive={} connection={}", entity.getStatusCodeValue(),
                    podName,
                    totalTimeMillis, keepAlive, connection);
            } catch (Exception e) {
                watch.stop();
                long totalTimeMillis = watch.getTotalTimeMillis();
                logger.warn("Feil skjedde etter tid=" + totalTimeMillis, e);
            }
        }
        logger.info("Done {} posts", number);
    }

    @GetMapping("/keepalive/client")
    public ResponseEntity<String> clientTest() {
        logger.info("Calling endpoint with psat token {} time(s).", number);
        int lastStatus = -1;
        for (int i = 0; i < number; i++) {
            String requestId = UUID.randomUUID().toString();
            MDC.put("requestId", requestId);

            StopWatch watch = new StopWatch();
            watch.start();
            try {
                Thread.sleep(wait);

                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.add("Authorization", "Bearer "+ Files.readString(Paths.get(psatPath)));
                HttpEntity<String> headerEntity = new HttpEntity<>(headers);

                ResponseEntity<JsonNode> entity =
                    restTemplate.exchange("/keepalive/server", HttpMethod.GET, headerEntity, JsonNode.class, Map.of());
                watch.stop();
                long totalTimeMillis = watch.getTotalTimeMillis();
                String clientName = "";
                if (entity.getBody() != null && entity.getBody().get("name") != null) {
                    clientName = entity.getBody().get("name").asText();
                }
                List<String> keepAlive = entity.getHeaders().get("Keep-Alive");
                List<String> connection = entity.getHeaders().get("Connection");
                logger.info("response={} server={} client={} time={}ms keepalive={} connection={}",
                    entity.getStatusCodeValue(),
                    podName, clientName,
                    totalTimeMillis, keepAlive, connection);
                lastStatus = entity.getStatusCodeValue();
            } catch (Exception e) {
                watch.stop();
                long totalTimeMillis = watch.getTotalTimeMillis();
                logger.warn("Feil skjedde etter tid=" + totalTimeMillis, e);
            }
        }
        logger.info("Done {} requests", number);
        return ResponseEntity.status(lastStatus).body("Done "+number+" requests, last with current status code");
    }

    @Override
    public Health health() {
        return Health.up().withDetail("k15321-test", "OK").build();
    }
}
