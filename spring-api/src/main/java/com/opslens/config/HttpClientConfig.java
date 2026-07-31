package com.opslens.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Basic flow
 * This class is needed when we send Http requests to FastAPI server
 * User/curl
 *   -> Spring Boot @RestController
 *     -> IncidentService
 *       -> RestClient
 *         -> FastAPI /analyze-log
 */

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient(){
        return RestClient.create();
    }


    // Bean tells Spring to create and manage one HttpClient instance
    // Constructor injection then gives that client to GitHubClient
    @Bean
    public HttpClient httpClient(){
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}

