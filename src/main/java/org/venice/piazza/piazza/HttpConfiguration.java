package org.venice.piazza.piazza;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpConfiguration {
	@Value("${http.max.total}")
	private int httpMaxTotal;
	@Value("${http.max.route}")
	private int httpMaxRoute;
	
	@Bean
	public Jackson2ObjectMapperBuilder jacksonBuilder() {
		Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
		builder.indentOutput(true);
		return builder;
	}
	
	@Bean
    public HttpClient httpClient() {
        return HttpClientBuilder.create().setMaxConnTotal(httpMaxTotal).setMaxConnPerRoute(httpMaxRoute).build();
    }

	@Bean
	public RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		HttpClient httpClient = HttpClients.custom().setMaxConnTotal(httpMaxTotal).setMaxConnPerRoute(httpMaxRoute)
				.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
					@Override
					public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
						HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
						while (it.hasNext()) {
							HeaderElement headerElement = it.nextElement();
							String param = headerElement.getName();
							String value = headerElement.getValue();
							if (value != null && param.equalsIgnoreCase("timeout")) {
								return Long.parseLong(value) * 1000;
							}
						}
						return (long) 5 * 1000;
					}
				}).setSSLHostnameVerifier(new NoopHostnameVerifier()).build();
		restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
		return restTemplate;
	}
}
