/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
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
package org.venice.piazza.gateway.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.core.Queue;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.piazza.AmqpQueuesConfiguration;
import org.venice.piazza.piazza.Application;
import org.venice.piazza.piazza.DocsConfiguration;
import org.venice.piazza.piazza.HttpConfiguration;

import messaging.job.JobMessageFactory;
import springfox.documentation.spring.web.plugins.Docket;

public class ApplicationTests {
	@InjectMocks
	private Application application;
	
	@InjectMocks
	private AmqpQueuesConfiguration amqpConfig;
	
	@InjectMocks
	private DocsConfiguration docsConfig;
	
	@InjectMocks
	private HttpConfiguration httpConfig;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(application, "SPACE", "unitTest");
	}

	@Test
	public void testRestTemplateCreation() {
		RestTemplate restTemplate = httpConfig.restTemplate();
		assertNotNull(restTemplate);
	}

	@Test
	public void testMapper() {
		Jackson2ObjectMapperBuilder builder = httpConfig.jacksonBuilder();
		assertNotNull(builder);
	}

	@Test
	public void testSwaggerDocs() {
		Docket docket = docsConfig.gatewayApi();
		assertNotNull(docket);
		assertEquals(docket.getGroupName(), "Piazza");
	}

	@Test
	public void testRabbitQueue() {
		Queue queue = amqpConfig.abortJobsQueue();
		assertNotNull(queue);
		assertEquals(queue.getName(), String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.ABORT_JOB_TOPIC_NAME, "unitTest"));
	}

}
