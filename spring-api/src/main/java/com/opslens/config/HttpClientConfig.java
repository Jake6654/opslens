package com.opslens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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
}
