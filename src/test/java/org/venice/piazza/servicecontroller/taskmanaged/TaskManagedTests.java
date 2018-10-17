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
package org.venice.piazza.servicecontroller.taskmanaged;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.Assert;
import org.springframework.web.client.ResourceAccessException;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import exception.InvalidInputException;
import model.job.Job;
import model.job.type.AbortJob;
import model.job.type.ExecuteServiceJob;
import model.service.metadata.ExecuteServiceData;
import model.service.taskmanaged.ServiceJob;
import model.status.StatusUpdate;
import util.PiazzaLogger;

/**
 * Tests for Task Managed Service Queues
 * 
 * @author Patrick.Doody
 *
 */
public class TaskManagedTests {
	@Spy
	private ObjectMapper objectMapper;
	@Mock
	private DatabaseAccessor accessor;
	@Mock
	private PiazzaLogger piazzaLogger;
	@Mock
	private Queue updateJobsQueue;
	@Mock
	private RabbitTemplate rabbitTemplate;

	@InjectMocks
	private ServiceTaskManager serviceTaskManager;

	/**
	 * Test initialization
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(serviceTaskManager, "SPACE", "UnitTest");
		ReflectionTestUtils.setField(serviceTaskManager, "TIMEOUT_LIMIT_COUNT", 5);
	}

	/**
	 * Tests adding a job to a Service's queue.
	 */
	@Test
	public void testAddJob() throws JsonProcessingException {
		// Mock Data
		ExecuteServiceJob job = new ExecuteServiceJob("job123");
		job.setData(new ExecuteServiceData());
		job.getData().setServiceId("service123");

		// Test, ensure no errors
		serviceTaskManager.addJobToQueue(job);

		// Test Exception handling, ensure exception is handled
		Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonMappingException("Oops"));
	}

	/**
	 * Tests updating Status for a Job, with a null Service found
	 */
	@Test(expected = InvalidInputException.class)
	public void testStatusUpdateError() throws InvalidInputException {
		serviceTaskManager.processStatusUpdate("noServiceHere", "123456", new StatusUpdate());
	}

	/**
	 * Tests updating a Status
	 */
	@Test
	public void testStatusUpdate() throws JsonProcessingException, InvalidInputException {
		// Mock
		StatusUpdate mockUpdate = new StatusUpdate(StatusUpdate.STATUS_RUNNING);
		ServiceJob mockJob = new ServiceJob("job123", "service123");
		Mockito.when(accessor.getServiceJob(Mockito.eq("service123"), Mockito.eq("job123"))).thenReturn(mockJob);

		// Test - Messaging succeeds
		serviceTaskManager.processStatusUpdate("service123", "job123", mockUpdate);

		// Test - Final Status
		mockUpdate = new StatusUpdate(StatusUpdate.STATUS_SUCCESS);
		serviceTaskManager.processStatusUpdate("service123", "job123", mockUpdate);

		// Test - Messaging fails
		Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonMappingException("Oops"));
		serviceTaskManager.processStatusUpdate("service123", "job123", mockUpdate);
	}

	/**
	 * Tests pulling a Job off the queue for a service that doesn't exist; thus causing an exception.
	 */
	@Test(expected = ResourceAccessException.class)
	public void testGetJobError() throws ResourceAccessException, InterruptedException, InvalidInputException {
		// Test - No service job found
		ExecuteServiceJob job = serviceTaskManager.getNextJobFromQueue("service123");
		Assert.isNull(job);

		// Test - No Piazza Job found. Exception should be thrown.
		Mockito.when(accessor.getNextJobInServiceQueue("service123")).thenReturn(new ServiceJob("job123", "service123"));
		serviceTaskManager.getNextJobFromQueue("service123");
	}

	/**
	 * Tests logic for pulling a Job off the queue for a service
	 */
	@Test
	public void testGetJob() throws ResourceAccessException, InterruptedException, InvalidInputException, JsonProcessingException {
		// Mock
		ServiceJob mockServiceJob = new ServiceJob("job123", "service123");
		Mockito.when(accessor.getNextJobInServiceQueue(Mockito.eq("service123"))).thenReturn(mockServiceJob);

		// Test - normal flow, proper Job type
		Job mockJob = new Job();
		mockJob.setJobId("job123");
		mockJob.setJobType(new ExecuteServiceJob("job123"));
		Mockito.when(accessor.getJobById(Mockito.eq("job123"))).thenReturn(mockJob);
		ExecuteServiceJob result = serviceTaskManager.getNextJobFromQueue("service123");

		// Check not null, and proper Job ID
		Assert.isInstanceOf(ExecuteServiceJob.class, result);
		Assert.isTrue(result.getJobId().equals("job123"));

		// Test - Handle JSON Exception
		Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonMappingException("Oops"));
		result = serviceTaskManager.getNextJobFromQueue("service123");

		// Check not null, and proper Job ID
		Assert.isInstanceOf(ExecuteServiceJob.class, result);
		Assert.isTrue(result.getJobId().equals("job123"));
	}

	/**
	 * Tests getting a Job off the queue that has an improper type
	 */
	@Test(expected = InvalidInputException.class)
	public void testGetJobTypeError() throws ResourceAccessException, InterruptedException, JsonProcessingException, InvalidInputException {
		// Mock
		ServiceJob mockServiceJob = new ServiceJob("job123", "service123");
		Mockito.when(accessor.getNextJobInServiceQueue(Mockito.eq("service123"))).thenReturn(mockServiceJob);

		// Test - Handle Message Exception, with Improper Job Type
		Job mockJob = new Job();
		mockJob.setJobId("job123");
		mockJob.setJobType(new AbortJob("job321"));
		Mockito.when(accessor.getJobById(Mockito.eq("job123"))).thenReturn(mockJob);
		Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonMappingException("Oops"));
		serviceTaskManager.getNextJobFromQueue("service123"); // Should throw
	}

	/**
	 * Test logic that processes timed out service jobs. Ensures no exceptions are thrown.
	 */
	@Test
	public void processTimeoutJobs() {
		// Mock
		ServiceJob mockJob = new ServiceJob("job123", "service123");
		mockJob.setTimeouts(0);

		// Test - No Timeout exceeded
		serviceTaskManager.processTimedOutServiceJob("service123", mockJob);

		// Test - Timeout exceeded
		mockJob.setTimeouts(100);
	}
}
