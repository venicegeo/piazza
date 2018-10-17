/*******************************************************************************
 * Copyright 2016, RadiantBlue Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *l
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.venice.piazza.servicecontroller.messaging.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import model.data.DataResource;
import model.data.type.GeoJsonDataType;
import model.logger.Severity;
import model.status.StatusUpdate;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.messaging.handlers.ExecuteServiceHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import model.data.DataType;
import model.data.type.BodyDataType;
import model.data.type.TextDataType;
import model.data.type.URLParameterDataType;
import model.job.metadata.ResourceMetadata;
import model.job.type.ExecuteServiceJob;
import model.service.metadata.ExecuteServiceData;
import model.service.metadata.Service;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Testing the ExecuteServiceHandler
 * @author mlynum
 *
 */

@RunWith(PowerMockRunner.class)
public class ExecuteServiceHandlerTest {
	
	@InjectMocks
	private ExecuteServiceHandler executeServiceHandler;
	@Mock 
	private PiazzaLogger loggerMock;
	@Mock
	private DatabaseAccessor accessorMock;
	@Mock
	private Service serviceMock;
	@Mock
	private RabbitTemplate rabbitTemplate;
	@Mock
	private org.springframework.amqp.core.Queue jobQueue;

	private UUIDFactory uuidFactory = new UUIDFactory();
	private ObjectMapper objectMapper = new ObjectMapper();
	
	ResourceMetadata rm = null;
	Service service = null;
	Service movieService = null;
	Service convertService = null;

	@Mock
	RestTemplate restTemplateMock;

	@Before
    public void setup() {
		try {
			whenNew(RestTemplate.class).withNoArguments().thenReturn(restTemplateMock);
		} catch (Exception e) {
		 	// TODO Auto-generated catch block
				e.printStackTrace();
		}
		rm = new ResourceMetadata();
		rm.name = "toUpper Params";
		rm.description = "Service to convert string to uppercase";

		service = new Service();
		service.setMethod ("POST");
		service.setResourceMetadata(rm);
		service.setServiceId("8");
		service.setUrl("http://localhost:8082/string/toUpper");
		
		// Second Service
		rm = new ResourceMetadata();
		rm.name = "convert string to upper or lower";
		rm.description = "Service to convert strings to upper case or lower case";

		convertService = new Service();
		convertService.setMethod ("POST");
		convertService.setResourceMetadata(rm);
		convertService.setServiceId("a842aae2-bd74-4c4b-9a65-c45e8cd9060f");
		convertService.setUrl("http://localhost:8087/jumpstart/string/convert");
		
		// Third Service
		rm = new ResourceMetadata();
		rm.name = "Movie Quote Welcome";
		rm.description = "A web service that welcomes you to pz-servicecontroller";

		movieService = new Service();
		movieService.setMethod ("GET");
		movieService.setResourceMetadata(rm);
		movieService.setServiceId("a842aae2-bd74-4c4b-9a65-c45e8cd9060f");
		movieService.setUrl("http://localhost:8087/jumpstart/moviequotewelcome");
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(this.executeServiceHandler, "uuidFactory", this.uuidFactory);
		ReflectionTestUtils.setField(this.executeServiceHandler, "requestJobQueue", this.jobQueue);
    }
	
	/**
	 * Test that a list of services could be retrieved.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testExecuteServiceSuccess() throws InterruptedException {
		ExecuteServiceJob job = new ExecuteServiceJob();
		// Setup executeServiceData
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
		edata.setServiceId(serviceId);
		// Now tie the data to the job
		job.data = edata;

		String responseServiceString = "Run results";

		ResponseEntity<String> responseEntity = new ResponseEntity<String>(responseServiceString, HttpStatus.OK);

		final ExecuteServiceHandler esMock = Mockito.spy(executeServiceHandler);

		Mockito.doReturn(responseEntity).when(esMock).handle(edata);
		ResponseEntity<String> result = esMock.handle(job);

		assertEquals("The response entity was correct for this describe request", responseEntity, result);
		assertEquals("The response code is 200", HttpStatus.OK, responseEntity.getStatusCode());
		assertEquals("The body of the response is correct", responseEntity.getBody(), responseServiceString);
	}
	
	/**
	 * Test that there is a failure when trying to send in a null job
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testExecuteNullJob() throws InterruptedException {
		ExecuteServiceJob job = null;

		ResponseEntity<String> result = executeServiceHandler.handle(job);
		assertEquals("The response code is 404", HttpStatus.BAD_REQUEST, result.getStatusCode());

	}
	
	/**
	 * tests what happens when the mime type is not specified for the payload
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testHandleWithNoParamsBodyPayloadNoMimeType() throws InterruptedException {
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
		edata.setServiceId(serviceId);

		HashMap<String, DataType> dataInputs = new HashMap<String, DataType>();
		String istring = "The rain in Spain falls mainly in the plain";
		BodyDataType body = new BodyDataType();
		body.content = istring;
		dataInputs.put("Body", body);
		edata.setDataInputs(dataInputs);

		URI uri = URI.create("http://localhost:8087/jumpstart/string/convert");
		// Setup mocks
		Mockito.when(accessorMock.getServiceById(serviceId)).thenReturn(convertService);
		// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(),
		// Severity.INFORMATIONAL);
		Mockito.when(serviceMock.getUrl()).thenReturn(uri.toString());

		ResponseEntity<String> retVal = executeServiceHandler.handle(edata);
		System.out.println(retVal);

		assertEquals(HttpStatus.BAD_REQUEST, retVal.getStatusCode());
		assertTrue("The proper message was returned", retVal.getBody().contains("Body mime type not specified"));

	}
	
	/**
	 * Testing handle without payload
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testHandleWithNoParamsBodyPayload() throws InterruptedException {
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
		edata.setServiceId(serviceId);

		HashMap<String, DataType> dataInputs = new HashMap<String, DataType>();
		String istring = "The rain in Spain falls mainly in the plain";
		BodyDataType body = new BodyDataType();
		body.content = istring;
		dataInputs.put("Body", body);
		body.mimeType = "application/json";
		edata.setDataInputs(dataInputs);

		URI uri = URI.create("http://localhost:8087/jumpstart/string/convert");
		// Setup mocks
		Mockito.when(
				restTemplateMock.postForEntity(Mockito.eq(uri), Mockito.any(Object.class), Mockito.eq(String.class)))
				.thenReturn(new ResponseEntity<String>("testExecuteService", HttpStatus.OK));
		Mockito.when(accessorMock.getServiceById(serviceId)).thenReturn(convertService);
		// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(),
		// Severity.INFORMATIONAL);
		Mockito.when(serviceMock.getUrl()).thenReturn(uri.toString());

		ResponseEntity<String> retVal = executeServiceHandler.handle(edata);
		System.out.println(retVal);

		assertTrue(retVal.getBody().contains("testExecuteService"));
	}
	
	@Test
	public void testHandleWithMapInputsPost() throws InterruptedException {
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "8";
		edata.setServiceId(serviceId);

		HashMap<String, DataType> dataInputs = new HashMap<String, DataType>();
		TextDataType tdt = new TextDataType();
		tdt.content = "Marge";
		dataInputs.put("name", tdt);
		edata.setDataInputs(dataInputs);

		URI uri = URI.create("http://localhost:8082/string/toUpper");
		Mockito.when(serviceMock.getUrl()).thenReturn(uri.toString());
		Mockito.when(accessorMock.getServiceById(serviceId)).thenReturn(service);
		// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(),
		// Severity.INFORMATIONAL);
		when(restTemplateMock.postForEntity(Mockito.eq(uri), Mockito.any(Object.class), Mockito.eq(String.class)))
				.thenReturn(new ResponseEntity<String>("testExecuteService", HttpStatus.FOUND));

		DatabaseAccessor mockDb = mock(DatabaseAccessor.class);
		when(mockDb.getServiceById("8")).thenReturn(service);
		ResponseEntity<String> retVal = executeServiceHandler.handle(edata);
		assertTrue(retVal.getBody().contains("testExecuteService"));
	}

	/**
	 * Tests executing web service with GET method
	 * @throws InterruptedException 
	 */
	@Test
	public void testHandleWithMapInputsGet() throws InterruptedException {
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "a842aae2-bd74-4c4b-9a65-c45e8cd9060f";
		edata.setServiceId(serviceId);
		HashMap<String, DataType> dataInputs = new HashMap<String, DataType>();
		URLParameterDataType tdt = new URLParameterDataType();
		tdt.content = "Marge";

		dataInputs.put("name", tdt);
		edata.setDataInputs(dataInputs);

		ObjectMapper mapper = new ObjectMapper();
		try {
			String tsvc = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(edata);
			System.out.println(tsvc);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		URI uri = URI.create("http://localhost:8087/jumpstart/moviequotewelcome?name=Marge");
		Mockito.when(serviceMock.getUrl()).thenReturn(uri.toString());
		Mockito.when(accessorMock.getServiceById(serviceId)).thenReturn(movieService);
		// Mockito.doNothing().when(loggerMock).log(Mockito.anyString(),
		// Severity.INFORMATIONAL);
		Mockito.when(restTemplateMock.getForEntity(Mockito.eq(uri), Mockito.eq(String.class)))
				.thenReturn(new ResponseEntity<String>("testExecuteService", HttpStatus.FOUND));

		when(accessorMock.getServiceById(serviceId)).thenReturn(movieService);

		ResponseEntity<String> retVal = executeServiceHandler.handle(edata);
		assertTrue(retVal.getBody().contains("testExecuteService"));
	}
	
	/**
	 * Test that the results throws a JSON exception
	 * due to a marshalling error
	 */
	@Test
	public void testThrowException() {
		ExecuteServiceData edata = new ExecuteServiceData();
		String serviceId = "8";
		edata.setServiceId(serviceId);

		HashMap<String, DataType> dataInputs = new HashMap<String, DataType>();
		TextDataType tdt = new TextDataType();
		tdt.content = "Marge";
		dataInputs.put("name", tdt);
		edata.setDataInputs(dataInputs);

		URI uri = URI.create("http://localhost:8082/string/toUpper");

		try {
			final ExecuteServiceHandler esMock = Mockito.spy(executeServiceHandler);

			// Now create the serialized objects to test against
			Map<String, DataType> postObjects = new HashMap<>();
			postObjects.put("name", tdt);
			Mockito.when(serviceMock.getUrl()).thenReturn(uri.toString());
			Mockito.when(accessorMock.getServiceById("8")).thenReturn(service);
			Mockito.doReturn(this.objectMapper).when(esMock).makeObjectMapper();
			//Mockito.when(omMock.writeValueAsString(postObjects)).thenThrow(new JsonMappingException("Test Exception"));
			ResponseEntity<String> retVal = esMock.handle(edata);

			assertEquals("The response code is 400 for BAD_REQUEST", HttpStatus.BAD_REQUEST, retVal.getStatusCode());
//		} catch (JsonProcessingException jpe) {
//			jpe.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Test
	public void testProcessExecutionResult() throws InterruptedException, IOException {

		DataResource da = new DataResource();
		da.dataId = "dr_id";

		String responseServiceString = this.objectMapper.writeValueAsString(da);
		ResponseEntity<String> validResponse = new ResponseEntity<String>(responseServiceString, HttpStatus.OK);
		ResponseEntity<String> nullResponse = new ResponseEntity<String>("null", HttpStatus.OK);



		this.executeServiceHandler.processExecutionResult(
				this.service, (new TextDataType()).getClass().getSimpleName(), StatusUpdate.STATUS_SUCCESS,
				validResponse, "dataId"
				);

		//Null object
		this.executeServiceHandler.processExecutionResult(
				this.service, (new TextDataType()).getClass().getSimpleName(), StatusUpdate.STATUS_SUCCESS,
				nullResponse, "dataId"
		);

		//Force an unlikely null pointer.
		Mockito.doThrow(RuntimeException.class).when(this.loggerMock).log(Mockito.startsWith("The DataResource is not"), Mockito.any());
		this.executeServiceHandler.processExecutionResult(
				this.service, (new TextDataType()).getClass().getSimpleName(), StatusUpdate.STATUS_SUCCESS,
				nullResponse, "dataId"
		);

		//Force an exception with a non-null data object.
		Mockito.doThrow(RuntimeException.class).when(this.loggerMock).log(Mockito.startsWith("The data being sent"), Mockito.any());
		this.executeServiceHandler.processExecutionResult(
				this.service, (new TextDataType()).getClass().getSimpleName(), StatusUpdate.STATUS_SUCCESS,
				validResponse, "dataId"
		);

		//Force an exception with a non-null data object.
		Mockito.doThrow(RuntimeException.class).when(this.loggerMock).log(Mockito.startsWith("The data being sent"), Mockito.any());
		this.executeServiceHandler.processExecutionResult(
				this.service, (new GeoJsonDataType()).getClass().getSimpleName(), StatusUpdate.STATUS_SUCCESS,
				validResponse, "dataId"
		);

		//Null response entity.
		this.executeServiceHandler.processExecutionResult(
				this.service, (new TextDataType()).getClass().getSimpleName(), StatusUpdate.STATUS_SUCCESS,
				null, "dataId"
		);
	}
}
