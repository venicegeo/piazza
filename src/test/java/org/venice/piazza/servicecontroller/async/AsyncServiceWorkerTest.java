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
package org.venice.piazza.servicecontroller.async;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.messaging.handlers.ExecuteServiceHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import model.data.DataType;
import model.data.type.TextDataType;
import model.job.result.type.DataResult;
import model.job.result.type.ErrorResult;
import model.job.type.ExecuteServiceJob;
import model.response.JobResponse;
import model.service.async.AsyncServiceInstance;
import model.service.metadata.ExecuteServiceData;
import model.service.metadata.Service;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Unit tests for the asynchronous service worker, which handles the requests for asynchronous services related to
 * polling status, execution, and result handling.
 * 
 * @author Patrick.Doody
 *
 */
public class AsyncServiceWorkerTest {
	@Mock
	private DatabaseAccessor accessor;
	@Mock
	private PiazzaLogger logger;
	@Mock
	private ExecuteServiceHandler executeServiceHandler;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private RabbitTemplate rabbitTemplate;
	@Mock
	private Queue updateJobsQueue;

	@InjectMocks
	private AsynchronousServiceWorker worker;

	private ObjectMapper objectMapper = new ObjectMapper();
	private ExecuteServiceJob mockJob = new ExecuteServiceJob("jobId");
	private AsyncServiceInstance mockInstance = new AsyncServiceInstance();
	private Service mockService = new Service();

	/**
	 * Test initialization
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		// UUID Factory always generates a specific GUID
		when(uuidFactory.getUUID()).thenReturn("123456");

		// Mocking Execute Data
		mockJob.data = new ExecuteServiceData();
		mockJob.data.setServiceId("serviceId");
		List<DataType> dataOutput = new ArrayList<DataType>();
		dataOutput.add(new TextDataType());
		mockJob.data.setDataOutput(dataOutput);

		// Mock an Instance we can use
		mockInstance.setInstanceId("instanceId");
		mockInstance.setJobId("jobId");
		mockInstance.setNumberErrorResponses(0);
		mockInstance.setOutputType("geojson");
		mockInstance.setServiceId("serviceId");
		mockInstance.setStatus(new StatusUpdate(StatusUpdate.STATUS_RUNNING));

		// Mock a Service to grab URL and other things from
		mockService.setUrl("test.local");
		mockService.setMethod("GET");
		mockService.setIsAsynchronous(true);
		mockService.setServiceId("serviceId");

		// Mock the property variables for URL
		ReflectionTestUtils.setField(worker, "STATUS_ENDPOINT", "status");
		ReflectionTestUtils.setField(worker, "RESULTS_ENDPOINT", "results");
		ReflectionTestUtils.setField(worker, "DELETE_ENDPOINT", "delete");
		ReflectionTestUtils.setField(worker, "STATUS_ERROR_LIMIT", 10);
		ReflectionTestUtils.setField(worker, "SPACE", "test");
	}

	/**
	 * Test service execution with successful response
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testExecute() throws JsonProcessingException, InterruptedException {
		// Mock the Response
		JobResponse mockResponse = new JobResponse("instanceId");
		Mockito.doReturn(new ResponseEntity<String>(objectMapper.writeValueAsString(mockResponse), HttpStatus.OK))
				.when(executeServiceHandler).handle(any(ExecuteServiceJob.class));
		// Test
		worker.executeService(mockJob);
		// Verify that the database attempted to insert the newly created Instance
		Mockito.verify(accessor, Mockito.times(1)).addAsyncServiceInstance(any(AsyncServiceInstance.class));
	}

	/**
	 * Test error handling for service returning a 500 error
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testExecutionErrorResponse() throws InterruptedException {
		// Mock an error coming from the execution service
		Mockito.doReturn(new ResponseEntity<String>("Error.", HttpStatus.INTERNAL_SERVER_ERROR)).when(executeServiceHandler)
				.handle(any(ExecuteServiceJob.class));
		// Test
		worker.executeService(mockJob);
		// Verify that the error was processed by the worker
		Mockito.verify(accessor, Mockito.times(1)).deleteAsyncServiceInstance(Mockito.eq(mockJob.getJobId()));
	}

	/**
	 * Tests a Service returning an invalid payload
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testExecutionErrorFormat() throws InterruptedException {
		// Mock the Response
		Mockito.doReturn(new ResponseEntity<String>("Everything is fine.", HttpStatus.OK)).when(executeServiceHandler)
				.handle(any(ExecuteServiceJob.class));
		// Test
		worker.executeService(mockJob);
		// Verify that the error was processed by the worker
		Mockito.verify(accessor, Mockito.times(1)).deleteAsyncServiceInstance(Mockito.eq(mockJob.getJobId()));
	}

	/**
	 * Tests a polling of Status with a RUNNING response
	 */
	@Test
	public void testPollRunningStatus() {
		// Mock
		Mockito.doReturn(mockService).when(accessor).getServiceById(Mockito.eq(mockInstance.getServiceId()));
		String url = String.format("%s/%s/%s", mockService.getUrl(), "status", mockInstance.getInstanceId());
		StatusUpdate mockStatus = new StatusUpdate(StatusUpdate.STATUS_RUNNING);
		Mockito.doReturn(mockStatus).when(restTemplate).getForObject(Mockito.eq(url), Mockito.any());

		// Test
		worker.pollStatus(mockInstance);

		// Verify
		Mockito.verify(accessor, Mockito.times(1)).updateAsyncServiceInstance(Mockito.any(AsyncServiceInstance.class));
	}

	/**
	 * Tests a polling of Status with a ERROR response
	 */
	@Test
	public void testPollErrorStatus() {
		// Mock - return an Error Status
		Mockito.doReturn(mockService).when(accessor).getServiceById(Mockito.eq(mockInstance.getServiceId()));
		String url = String.format("%s/%s/%s", mockService.getUrl(), "status", mockInstance.getInstanceId());
		StatusUpdate mockStatus = new StatusUpdate(StatusUpdate.STATUS_ERROR);
		mockStatus.setResult(new ErrorResult("Uh Oh", "Things went wrong."));
		Mockito.doReturn(mockStatus).when(restTemplate).getForObject(Mockito.eq(url), Mockito.eq(StatusUpdate.class));

		// Test
		worker.pollStatus(mockInstance);

		// Verify
		Mockito.verify(accessor, Mockito.times(1)).deleteAsyncServiceInstance(Mockito.eq(mockInstance.getJobId()));
	}

	/**
	 * Tests a polling of Status with a SUCCESS response
	 */
	@Test
	public void testPollSuccessStatus() throws RestClientException, JsonProcessingException {
		// Mock - return an Error Status
		Mockito.doReturn(mockService).when(accessor).getServiceById(Mockito.eq(mockInstance.getServiceId()));
		String url = String.format("%s/%s/%s", mockService.getUrl(), "status", mockInstance.getInstanceId());
		StatusUpdate mockStatus = new StatusUpdate(StatusUpdate.STATUS_SUCCESS);
		// Mock the status fetch
		Mockito.doReturn(mockStatus).when(restTemplate).getForObject(Mockito.eq(url), Mockito.eq(StatusUpdate.class));
		// Mock the result fetch
		String getResultUrl = String.format("%s/%s/%s", mockService.getUrl(), "results", mockInstance.getServiceId());
		Mockito.doReturn(new ResponseEntity<String>(objectMapper.writeValueAsString(new DataResult()), HttpStatus.OK)).when(restTemplate)
				.getForEntity(Mockito.eq(getResultUrl), Mockito.eq(String.class));

		// Test
		worker.pollStatus(mockInstance);

		// Verify
		Mockito.verify(accessor, Mockito.times(1)).deleteAsyncServiceInstance(Mockito.eq(mockInstance.getJobId()));
	}

	/**
	 * Tests updating the failure count of an Instance
	 */
	@Test
	public void testFailureCount() {
		// Mock an instance that is not above the threshold
		AsyncServiceInstance instance = new AsyncServiceInstance();
		instance.setInstanceId("instanceId");
		instance.setJobId("jobId");
		instance.setNumberErrorResponses(0);
		instance.setOutputType("geojson");
		instance.setServiceId("serviceId");
		instance.setStatus(new StatusUpdate(StatusUpdate.STATUS_RUNNING));
		worker.updateFailureCount(instance);

		// Mock an instance that is above the threshold
		instance.setNumberErrorResponses(15);
		worker.updateFailureCount(instance);
	}

	/**
	 * Tests cancelling an instance
	 */
	@Test
	public void testCancellationStatus() {
		// Mock
		Mockito.doReturn(mockService).when(accessor).getServiceById(Mockito.eq(mockService.getServiceId()));

		// Test
		worker.sendCancellationStatus(mockInstance);

		// Test when an error is encountered from the service
		Mockito.doThrow(new RestClientException("Error")).when(restTemplate).delete(Mockito.any());
		worker.sendCancellationStatus(mockInstance);
	}

}
