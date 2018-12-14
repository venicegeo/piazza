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
package org.venice.piazza.jobmanager.test;

import static org.mockito.Mockito.when;

import model.job.Job;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.venice.piazza.jobmanager.database.JobManagerDatabaseAccessor;
import org.venice.piazza.jobmanager.messaging.handler.RequestJobHandler;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import org.springframework.test.util.ReflectionTestUtils;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Handler for requesting Jobs
 * 
 * @author Patrick.Doody
 *
 */
public class RequestHandlerTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private JobManagerDatabaseAccessor accessor;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private RabbitTemplate rabbitTemplate;

	@InjectMocks
	private RequestJobHandler requestJobHandler;

	/**
	 * Initialization
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

        ReflectionTestUtils.setField(requestJobHandler, "logJobPayloadsToConsole", true);
	}

	/**
	 * Test requesting a Job
	 */
	@Test
	public void testRequestJob()  {
		// Mock
		PiazzaJobRequest mockRequest = new PiazzaJobRequest();
		mockRequest.jobType = new RepeatJob("123456");
		when(uuidFactory.getUUID()).thenReturn("654321");

		//Test with an empty id. One should be assigned.
		requestJobHandler.process(mockRequest, "");
		Mockito.verify(this.accessor, Mockito.times(1)).addJob(Mockito.any(Job.class));

		//Test with a random id.
		requestJobHandler.process(mockRequest, "123456");
		Mockito.verify(this.accessor, Mockito.times(2)).addJob(Mockito.any(Job.class));

		//Generate a nullPointer exception. It should not propagate up.
		requestJobHandler.process(mockRequest, null);
	}
}
