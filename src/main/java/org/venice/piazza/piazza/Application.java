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

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
@EnableAutoConfiguration
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
		"org.venice.piazza",
		"org.venice.piazza.access",
		"org.venice.piazza.piazza",
		"org.venice.piazza.gateway",
		"org.venice.piazza.idam",
		"org.venice.piazza.ingest",
		"org.venice.piazza.jobmanager",
		"org.venice.piazza.servicecontroller",
		"org.venice.piazza.util",
		"util",
})
public class Application extends SpringBootServletInitializer implements AsyncConfigurer {
	@Value("${thread.count.size}")
	private int threadCountSize;
	@Value("${thread.count.limit}")
	private int threadCountLimit;
	@Value("${SPACE}")
	private String SPACE;

	private static final Logger LOG = LoggerFactory.getLogger(Application.class);

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(Application.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args); // NOSONAR
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
}
