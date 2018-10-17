/*******************************************************************************
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
 *******************************************************************************/
package org.venice.piazza.servicecontroller.messaging;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.util.CoreServiceProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import model.job.Job;
import model.job.metadata.ResourceMetadata;
import model.request.PiazzaJobRequest;
import model.service.metadata.Service;
import util.PiazzaLogger;

public class ServiceMessageThreadManagerTest {
	@InjectMocks
	private ServiceMessageThreadManager smtManager;

	@Mock
	private PiazzaLogger loggerMock;
	@Mock
	private DatabaseAccessor accessorMock;
	@Mock
	private Service serviceMock;
	@Mock
	private CoreServiceProperties propertiesMock;
	@Mock
	private ServiceMessageWorker serviceMessageWorker;
	@Spy
	private ObjectMapper mapper;

	ResourceMetadata rm = null;
	Service service = null;
	Service movieService = null;
	Service convertService = null;

	@Before
	/**
	 * Called for each test setup
	 */
	public void setup() {
		// Setup a Service with some Resource Metadata
		rm = new ResourceMetadata();
		rm.name = "toUpper Params";
		rm.description = "Service to convert string to uppercase";

		service = new Service();
		service.setMethod("POST");
		service.setResourceMetadata(rm);
		service.setUrl("http://localhost:8082/string/toUpper");
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(smtManager, "SPACE", "unittest");
	}

	/**
	 * Test polling of consumer records when nothing is returned
	 */
	@Test
	public void testPolling() throws JsonProcessingException {
		// Mock Job
		Job mockJob = new Job(new PiazzaJobRequest(), "123456");
		// Test Polling
		smtManager.processServiceExecutionJob(new ObjectMapper().writeValueAsString(mockJob));

	}
}
