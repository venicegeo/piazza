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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.venice.piazza.jobmanager.database.JobManagerDatabaseAccessor;
import org.venice.piazza.jobmanager.messaging.JobMessager;
import org.venice.piazza.jobmanager.messaging.handler.AbortJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RepeatJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RequestJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.UpdateStatusHandler;
import model.job.JobProgress;
import model.request.PiazzaJobRequest;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

import java.io.IOException;

/**
 * Tests the Job Messager
 * 
 * @author Patrick.Doody
 *
 */
public class MessagerTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private JobManagerDatabaseAccessor accessor;
	@Mock
	private AbortJobHandler abortJobHandler;
	@Mock
	private UpdateStatusHandler updateStatusHandler;
	@Mock
	private RepeatJobHandler repeatJobHandler;
	@Mock
	private RequestJobHandler requestJobHandler;
	@Spy
	private ObjectMapper objectMapper;

	@InjectMocks
	private JobMessager jobMessager;

	/**
	 * Setup tests
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		// Mocking Autowired values
		ReflectionTestUtils.setField(jobMessager, "space", "unit-test");
	}

	/**
	 * Testing that Consumer Records are appropriately handled
	 */
	@Test
	public void testProcessing() throws Exception {
		Mockito.doCallRealMethod().when(objectMapper).readValue(Mockito.anyString(), Mockito.eq(StatusUpdate.class));
		Mockito.doCallRealMethod().when(objectMapper).readValue(Mockito.anyString(), Mockito.eq(PiazzaJobRequest.class));
		ObjectMapper mapper = new ObjectMapper();
		// Create messages to pass to the Messager
		StatusUpdate statusUpdate = new StatusUpdate(StatusUpdate.STATUS_RUNNING, new JobProgress());
		PiazzaJobRequest jobRequest = new PiazzaJobRequest();

		// Verify the Messages are appropriately handled
		Mockito.doNothing().when(updateStatusHandler).process(any(StatusUpdate.class));
		jobMessager.processUpdateMessage(mapper.writeValueAsString(statusUpdate));

		Mockito.doNothing().when(requestJobHandler).process(any(PiazzaJobRequest.class), Mockito.anyString());
		jobMessager.processRequestMessage(mapper.writeValueAsString(jobRequest));

		//Test the exceptions.
		Mockito.doThrow(new IOException()).when(objectMapper).readValue(anyString(), any(Class.class));
		jobMessager.processUpdateMessage(mapper.writeValueAsString(statusUpdate));
		jobMessager.processRequestMessage(mapper.writeValueAsString(jobRequest));
	}

}