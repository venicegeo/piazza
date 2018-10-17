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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.messaging.handlers.DeleteServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.DescribeServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.ExecuteServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.ListServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.RegisterServiceHandler;
import org.venice.piazza.servicecontroller.messaging.handlers.UpdateServiceHandler;
import org.venice.piazza.servicecontroller.util.CoreServiceProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import model.data.DataResource;
import model.data.DataType;
import model.data.type.GeoJsonDataType;
import model.data.type.TextDataType;
import model.job.Job;
import model.job.metadata.ResourceMetadata;
import model.job.type.ExecuteServiceJob;
import model.job.type.RegisterServiceJob;
import model.service.metadata.ExecuteServiceData;
import model.service.metadata.Service;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Class of unit tests to test the deletion of services
 * 
 * @author mlynum
 */
@RunWith(PowerMockRunner.class)
public class ServiceMessageWorkerTest {
	@InjectMocks
	private ServiceMessageWorker smWorkerMock;

	@Mock
	private PiazzaLogger loggerMock;
	@Mock
	private RegisterServiceHandler rsHandlerMock;
	@Mock
	private ExecuteServiceHandler esHandlerMock;
	@Mock
	private DescribeServiceHandler dsHandlerMock;
	@Mock
	private UpdateServiceHandler usHandlerMock;
	@Mock
	private ListServiceHandler lsHandlerMock;
	@Mock
	private DeleteServiceHandler dlHandlerMock;
	@Mock
	private CoreServiceProperties coreServicePropMock;
	@Mock
	private UUIDFactory uuidFactoryMock;
	@Mock
	private ObjectMapper omMock;
	@Mock
	private DatabaseAccessor accessorMock;
	@Mock
	@Qualifier("UpdateJobsQueue")
	private Queue updateJobsQueue;
	@Mock
	@Qualifier("RequestJobQueue")
	private Queue requestJobQueue;
	@Mock
	private RabbitTemplate rabbitTemplate;

	private Job validJob;
	private ExecuteServiceJob esJob;
	ResourceMetadata rm = null;
	Service service = null;

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

		// Create the executeService Job
		ExecuteServiceJob esJob = new ExecuteServiceJob();
		// Setup valid data
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
		edata.setServiceId(serviceId);
		TextDataType dataType = new TextDataType();
		dataType.mimeType = "application/json";
		List<DataType> dataTypes = new ArrayList<>();
		dataTypes.add(dataType);
		edata.setDataOutput(dataTypes);
		// Now tie the data to the job
		esJob.data = edata;
		validJob = new Job();
		validJob.setJobId("b842aae2-ed70-5c4b-9a65-c45e8cd9060g");
		validJob.setJobType(esJob);

		MockitoAnnotations.initMocks(this);
	}

	@Test
	/**
	 * Test a null job being passed
	 */
	public void testNullob() {
		try {
			// Test exception by sending an invalid ConsumerMessage
			Future<String> workerFuture = smWorkerMock.run(null, null);
			assertTrue(workerFuture.get() != null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	@Test
	/**
	 * Test an invalid message being received
	 */
	public void testWorkerInvalidPayload() {
		try {
			// Test exception by sending an invalid Job
			Future<String> workerFuture = smWorkerMock.run(createInvalidJobWithoutOuptut(), null);
			assertTrue(workerFuture.get() != null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	@Test
	/**
	 * Test an valid message being received
	 */
	public void testWorkerValidPayload() {
		try {

			ServiceMessageWorker spy = Mockito.spy(smWorkerMock);

			DataResource data = new DataResource();
			TextDataType newDataType = new TextDataType();
			newDataType.content = "This is a result";
			data.dataType = newDataType;
			data.dataId = "b842aae2-ed70-5c4b-9a65-c45e8cd9060g";
			ObjectMapper om = new ObjectMapper();

			String executeResponse = om.writeValueAsString(data);
			ResponseEntity<String> response = new ResponseEntity<>(executeResponse, HttpStatus.OK);

			ExecuteServiceJob jobItem = (ExecuteServiceJob) validJob.getJobType();
			ExecuteServiceData esData = jobItem.data;
			Mockito.when(esHandlerMock.handle(jobItem)).thenReturn(response);
			// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(), Severity.INFORMATIONAL);

			// Test valid Payload
			Future<String> workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);

			// Now try with GeoJson
			ExecuteServiceJob esJob = new ExecuteServiceJob();
			ExecuteServiceData edata = new ExecuteServiceData();
			String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
			edata.setServiceId(serviceId);
			GeoJsonDataType dataType = new GeoJsonDataType();
			dataType.mimeType = "application/vnd.geo+json";
			List<DataType> dataTypes = new ArrayList<>();
			dataTypes.add(dataType);
			edata.setDataOutput(dataTypes);
			// Now tie the data to the job
			esJob.data = edata;
			validJob = new Job();
			validJob.setJobId("b842aae2-ed70-5c4b-9a65-c45e8cd9060g");
			validJob.setJobType(esJob);

			om = new ObjectMapper();

			// Test valid Payload for GeoJSON
			workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Test
	/**
	 * Test an valid message being received
	 */
	public void testOfflineService() {
		try {

			ServiceMessageWorker spy = Mockito.spy(smWorkerMock);

			ExecuteServiceJob jobItem = (ExecuteServiceJob) validJob.getJobType();
			ExecuteServiceData esData = jobItem.data;
			// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(), Severity.INFORMATIONAL);

			// Setup a new OFFLINE service
			rm = new ResourceMetadata();
			rm.name = "toUpper Params";
			rm.description = "Service to convert string to uppercase";
			rm.setAvailability(ResourceMetadata.STATUS_TYPE.OFFLINE.toString());
			service.setResourceMetadata(rm);
			Mockito.when(accessorMock.getServiceById("a842aae2-bd74-4c4b-9a65-c45e8cd9060f")).thenReturn(service);

			// Test valid Payload
			Future<String> workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Test
	/**
	 * Test what happens if a Null is returned from handling the execute
	 */
	public void testNullHandleResult() {
		try {

			ServiceMessageWorker spy = Mockito.spy(smWorkerMock);

			DataResource data = new DataResource();
			TextDataType newDataType = new TextDataType();
			newDataType.content = "This is a result";
			data.dataType = newDataType;
			data.dataId = "b842aae2-ed70-5c4b-9a65-c45e8cd9060g";
			ObjectMapper om = new ObjectMapper();

			String executeResponse = om.writeValueAsString(data);
			ResponseEntity<String> response = new ResponseEntity<>(executeResponse, HttpStatus.OK);

			ExecuteServiceJob jobItem = (ExecuteServiceJob) validJob.getJobType();
			ExecuteServiceData esData = jobItem.data;
			// What happens if the handled executeservice returns a null
			Mockito.when(esHandlerMock.handle(jobItem)).thenReturn(null);
			// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(), Severity.INFORMATIONAL);

			// Test valid Payload
			Future<String> workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	/**
	 * Test an invalid message being received
	 */
	@Test
	public void testBadHTTPResponseServiceResponse() {
		try {

			ServiceMessageWorker spy = Mockito.spy(smWorkerMock);

			DataResource data = new DataResource();
			TextDataType newDataType = new TextDataType();
			newDataType.content = "This is a result";
			data.dataType = newDataType;
			data.dataId = "b842aae2-ed70-5c4b-9a65-c45e8cd9060g";
			ObjectMapper om = new ObjectMapper();

			String executeResponse = om.writeValueAsString(data);
			ResponseEntity<String> response = new ResponseEntity<>(executeResponse, HttpStatus.BAD_REQUEST);

			ExecuteServiceJob jobItem = (ExecuteServiceJob) validJob.getJobType();
			ExecuteServiceData esData = jobItem.data;
			Mockito.when(esHandlerMock.handle(jobItem)).thenReturn(response);
			// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(), Severity.INFORMATIONAL);

			// Test valid Payload
			Future<String> workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	/**
	 * Test creation of headers with media type
	 */
	@Test
	public void testCreateMediaType() {
		smWorkerMock.createMediaType("application/json");
		smWorkerMock.createMediaType("json");
	}

	/**
	 * Test an invalid message being received
	 */
	@Test
	public void testServicResponseObjectMapperError() {
		try {

			ServiceMessageWorker spy = Mockito.spy(smWorkerMock);

			DataResource data = new DataResource();
			TextDataType newDataType = new TextDataType();
			newDataType.content = "This is a result";
			data.dataType = newDataType;
			data.dataId = "b842aae2-ed70-5c4b-9a65-c45e8cd9060g";
			ObjectMapper om = new ObjectMapper();

			String executeResponse = om.writeValueAsString(data);
			ResponseEntity<String> response = new ResponseEntity<>(executeResponse, HttpStatus.OK);

			ExecuteServiceJob jobItem = (ExecuteServiceJob) validJob.getJobType();
			ExecuteServiceData esData = jobItem.data;
			Mockito.when(esHandlerMock.handle(jobItem)).thenReturn(response);
			// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(), Severity.INFORMATIONAL);
			Mockito.when(omMock.readValue(Mockito.anyString(), eq(DataResource.class))).thenReturn(null);

			// Test valid Payload
			Future<String> workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	@Test
	/**
	 * Test some other job that is received
	 */
	public void testInvalidJob() {
		try {

			ServiceMessageWorker spy = Mockito.spy(smWorkerMock);
			RegisterServiceJob rsj = new RegisterServiceJob();
			rsj.setData(service);
			validJob.setJobType(rsj);

			// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(), Severity.INFORMATIONAL);

			// Test valid Payload
			Future<String> workerFuture = spy.run(validJob, null);
			assertTrue(workerFuture.get() != null);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private Job createInvalidJobWithoutOuptut() {

		Job job = new Job();

		// Create the executeService Job
		ExecuteServiceJob esJob = new ExecuteServiceJob();
		// Setup valid data
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
		edata.setServiceId(serviceId);
		// Now tie the data to the job
		esJob.data = edata;
		job = new Job();
		job.setJobType(esJob);

		return job;
	}

}
