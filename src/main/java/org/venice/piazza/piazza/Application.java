/**
 * Copyright 2018, Radiant Solutions, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package org.venice.piazza.piazza;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.venice.piazza.gateway.auth.ExtendedRequestDetails;
import org.venice.piazza.gateway.auth.PiazzaBasicAuthenticationEntryPoint;
import org.venice.piazza.gateway.auth.PiazzaBasicAuthenticationProvider;
import org.venice.piazza.idam.authn.GxAuthenticator;
import org.venice.piazza.idam.authn.PiazzaAuthenticator;
import org.venice.piazza.jobmanager.database.DatabaseAccessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.swagger.annotations.Api;
import messaging.job.JobMessageFactory;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;



/**
 * Main application class.
 * 
 * Sets up the Spring Boot environment to launch BF API.
 * 
 * @version 2.0
 */
@SpringBootApplication
@EnableSwagger2
@Configuration
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableRabbit
@EnableJpaRepositories(basePackages = { 
		"org.venice.piazza.common.hibernate", 
})
@EntityScan(basePackages = { 
		"org.venice.piazza.common.hibernate",
})
@ComponentScan(basePackages = { 
		"org.venice.piazza.access",
		"org.venice.piazza.piazza",
		"org.venice.piazza.gateway",
		"org.venice.piazza.idam",
		"org.venice.piazza.ingest",
		"org.venice.piazza.jobmanager",
		"org.venice.piazza.servicecontroller",
		"util",
})
public class Application extends SpringBootServletInitializer implements AsyncConfigurer {
	@Value("${http.max.total}")
	private int httpMaxTotal;
	@Value("${http.max.route}")
	private int httpMaxRoute;
	@Value("${thread.count.size}")
	private int threadCountSize;
	@Value("${thread.count.limit}")
	private int threadCountLimit;
	@Value("${SPACE}")
	private String SPACE;

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseAccessor.class);

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(Application.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args); // NOSONAR
	}

	@Bean
	public Queue abortJobsQueue() {
		return new Queue(String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.ABORT_JOB_TOPIC_NAME, SPACE), true, false,
				false);
	}

	@Bean
	public Jackson2ObjectMapperBuilder jacksonBuilder() {
		Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
		builder.indentOutput(true);
		return builder;
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

	@Bean
	public Docket gatewayApi() {
		return new Docket(DocumentationType.SWAGGER_2).useDefaultResponseMessages(false).ignoredParameterTypes(Principal.class)
				.groupName("Piazza").apiInfo(apiInfo()).select().apis(RequestHandlerSelectors.withClassAnnotation(Api.class))
				.paths(PathSelectors.any()).build();
	}

	private ApiInfo apiInfo() {
		return new ApiInfoBuilder().title("Gateway API").description("Piazza Core Services API")
				.contact(new Contact("The VeniceGeo Project", "http://radiantblue.com", "venice@radiantblue.com")).version("0.1.0").build();
	}
	
	@Override
	@Bean
	public Executor getAsyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		// Thread pool capped to work optimally at 512MB ram (per the PCF app)
		executor.setCorePoolSize(threadCountSize);
		executor.setMaxPoolSize(threadCountLimit);
		executor.initialize();
		return executor;
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (Throwable ex, Method method, Object... params) -> LOG
				.error("Uncaught Threading exception encountered in {} with details: {}", ex.getMessage(), method.getName());
	}

	@Bean
	public Queue updateJobsQueue() {
		return new Queue(String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.UPDATE_JOB_TOPIC_NAME, SPACE), true, false,
				false);
	}

	@Bean(name = "RequestJobQueue")
	public Queue requestJobQueue() {
		return new Queue(String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.REQUEST_JOB_TOPIC_NAME, SPACE), true, false,
				false);
	}

	@Bean
	public LocalValidatorFactoryBean getLocalValidatorFactoryBean() {
		return new LocalValidatorFactoryBean();
	}

	@Configuration
	protected static class AddCorsHeaders extends WebMvcConfigurerAdapter {
		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(new HandlerInterceptorAdapter() {
				@Override
				public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
					response.setHeader("Access-Control-Allow-Headers", "authorization, content-type");
					response.setHeader("Access-Control-Allow-Origin", "*");
					response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
					response.setHeader("Access-Control-Max-Age", "36000");
					return true;
				}
			});
		}
	}

	@Configuration
	@Profile({ "secure" })
	protected static class ApplicationSecurity extends WebSecurityConfigurerAdapter {
		@Autowired
		private PiazzaBasicAuthenticationProvider basicAuthProvider;
		@Autowired
		private PiazzaBasicAuthenticationEntryPoint basicEntryPoint;

		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth.authenticationProvider(basicAuthProvider);
		}

		@Override
		public void configure(WebSecurity web) throws Exception {
			web.ignoring().antMatchers("/key").antMatchers("/version").antMatchers("/").antMatchers(HttpMethod.OPTIONS)
					.antMatchers("/v2/key");
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http.httpBasic().authenticationEntryPoint(basicEntryPoint).authenticationDetailsSource(authenticationDetailsSource()).and()
					.authorizeRequests().anyRequest().authenticated().and().sessionManagement()
					.sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().csrf().disable();
		}

		/**
		 * Defines the Details that should be passed into the AuthenticationProvider details object.
		 */
		private AuthenticationDetailsSource<HttpServletRequest, ExtendedRequestDetails> authenticationDetailsSource() {
			return new AuthenticationDetailsSource<HttpServletRequest, ExtendedRequestDetails>() {
				@Override
				public ExtendedRequestDetails buildDetails(HttpServletRequest request) {
					return new ExtendedRequestDetails(request);
				}
			};
		}
	}
	
	@Configuration
	@Profile({ "disable-authn" })
	protected static class DisabledConfig {
		@Bean
		public PiazzaAuthenticator piazzaAuthenticator() {
			return null;
		}

		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}
	}

	@Configuration
	@Profile({ "geoaxis" })
	protected static class GxConfig {

		@Value("${http.max.total}")
		private int httpMaxTotal;

		@Value("${http.max.route}")
		private int httpMaxRoute;

		@Value("${JKS_FILE}")
		private String keystoreFileName;

		@Value("${JKS_PASSPHRASE}")
		private String keystorePassphrase;

		@Value("${PZ_PASSPHRASE}")
		private String piazzaKeyPassphrase;

		@Bean
		public PiazzaAuthenticator piazzaAuthenticator() {
			return new GxAuthenticator();
		}

		@Bean
		public RestTemplate restTemplate() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException,
				KeyStoreException, CertificateException, IOException {
			SSLContext sslContext = SSLContexts.custom().loadKeyMaterial(getStore(), piazzaKeyPassphrase.toCharArray())
					.loadTrustMaterial(getStore(), new TrustSelfSignedStrategy()).useProtocol("TLS").build();
			HttpClient httpClient = HttpClientBuilder.create().setMaxConnTotal(httpMaxTotal).setSSLContext(sslContext)
					.setMaxConnPerRoute(httpMaxRoute).setSSLHostnameVerifier(new NoopHostnameVerifier())
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
							return 5L * 1000;
						}
					}).build();

			RestTemplate restTemplate = new RestTemplate();
			restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
			restTemplate.setMessageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter())); // Why is this
																											// required?
			return restTemplate;
		}

		protected KeyStore getStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
			final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream inputStream = getClass().getClassLoader().getResourceAsStream(keystoreFileName);
			try {
				store.load(inputStream, keystorePassphrase.toCharArray());
			} finally {
				inputStream.close();
			}

			return store;
		}
	}
}
