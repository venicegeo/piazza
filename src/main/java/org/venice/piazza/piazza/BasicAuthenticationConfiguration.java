package org.venice.piazza.piazza;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.access.deploy.geoserver.AuthHeaders;
import org.venice.piazza.access.deploy.geoserver.BasicAuthHeaders;

@Configuration
@Profile({"basic-geoserver-auth"}) 
public class BasicAuthenticationConfiguration {

    @Value("${http.max.total}")
    private int httpMaxTotal;

    @Value("${http.max.route}")
    private int httpMaxRoute;

    @Bean
    public HttpClient httpClient() {
        return HttpClientBuilder.create().setMaxConnTotal(httpMaxTotal).setMaxConnPerRoute(httpMaxRoute).build();
    }

    @Bean
    public RestTemplate restTemplate(@Autowired HttpClient httpClient) {
        final RestTemplate restTemplate = new RestTemplate();
		restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
        return restTemplate;
    }

    @Bean
    public AuthHeaders authHeaders() {
        return new BasicAuthHeaders();
    }
}