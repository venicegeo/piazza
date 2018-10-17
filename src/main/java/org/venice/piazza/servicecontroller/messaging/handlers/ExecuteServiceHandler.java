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
package org.venice.piazza.servicecontroller.messaging.handlers;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import messaging.job.JobMessageFactory;
import model.data.DataResource;
import model.data.DataType;
import model.data.type.BodyDataType;
import model.data.type.GeoJsonDataType;
import model.data.type.TextDataType;
import model.data.type.URLParameterDataType;
import model.job.PiazzaJobType;
import model.job.result.type.DataResult;
import model.job.type.ExecuteServiceJob;
import model.job.type.IngestJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import model.service.metadata.ExecuteServiceData;
import model.service.metadata.Service;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Handler for handling executeService requests. This handler is used when execute-service messages are received or
 * when clients utilize the ServiceController service.
 * 
 * @author mlynum & Sonny.Saniev
 * @version 1.0
 */
@Component
public class ExecuteServiceHandler implements PiazzaJobHandler {
	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	private RestTemplate template;
	@Autowired
	@Qualifier("RequestJobQueue")
	private Queue requestJobQueue;
	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Value("${SPACE}")
	private String SPACE; //NOSONAR

	private static final Logger LOG = LoggerFactory.getLogger(ExecuteServiceHandler.class);
	private static final String MIME_TYPE = "application/json";

	/**
	 * Handler for handling execute service requests. This method will execute a service given the resourceId and return
	 * a response to the job manager. (non-Javadoc)
	 * 
	 * @throws InterruptedException
	 * @see org.venice.piazza.servicecontroller.messaging.handlers.Handler#handle(model.job.PiazzaJobType)
	 */
	@Override
	public ResponseEntity<String> handle(PiazzaJobType jobRequest) throws InterruptedException {
		logger.log("Executing a Service.", Severity.DEBUG);

		ExecuteServiceJob job = (ExecuteServiceJob) jobRequest;
		// Check to see if this is a valid request
		if (job != null) {
			// Get the ResourceMetadata
			ExecuteServiceData esData = job.data;
			ResponseEntity<String> handleResult = handle(esData);
			ResponseEntity<String> result = new ResponseEntity<>(handleResult.getBody(), handleResult.getStatusCode());
			logger.log("The result is " + result, Severity.DEBUG);

			// TODO Use the result, send a message with the resource Id and jobId
			return result;
		} else {
			logger.log("Job is null", Severity.ERROR);
			return new ResponseEntity<>("Job is null", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Handles requests to execute a service. TODO this needs to change to leverage pz-jbcommon ExecuteServiceMessage
	 * after it builds.
	 * 
	 * @param message
	 * @return the Response as a String
	 * @throws InterruptedException
	 */
	public ResponseEntity<String> handle(ExecuteServiceData data) throws InterruptedException {
		logger.log(String.format("Beginning execution of Service ID %s", data.getServiceId()), Severity.INFORMATIONAL);
		String serviceId = data.getServiceId();
		Service sMetadata = null;
		ObjectMapper objectMapper = new ObjectMapper();

		try {
			// Accessor throws exception if can't find service
			sMetadata = accessor.getServiceById(serviceId);

			String result = objectMapper.writeValueAsString(sMetadata);
			logger.log(result, Severity.INFORMATIONAL);
		} catch (ResourceAccessException | JsonProcessingException ex) {
			LOG.error("Exception occurred", ex);
		}

		if (sMetadata != null) {
			return processServiceMetadata(sMetadata, data);
		} else {
			logger.log(String.format("The service was NOT found id %s", data.getServiceId()), Severity.ERROR,
					new AuditElement("serviceController", "notFound", data.getServiceId()));
			return new ResponseEntity<>("Service Id " + data.getServiceId() + " not found", HttpStatus.NOT_FOUND);
		}
	}

	private ResponseEntity<String> processServiceMetadata(final Service sMetadata, final ExecuteServiceData data)
			throws InterruptedException {
		// Default request mimeType application/json
		ObjectMapper objectMapper = new ObjectMapper();
		String requestMimeType = MIME_TYPE;

		String rawURL = sMetadata.getUrl();
		logger.log(String.format("Executing Service with URL %s with ID %s", rawURL, data.getServiceId()), Severity.INFORMATIONAL);
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(rawURL);

		Map<String, DataType> postObjects = new HashMap<>();
		Iterator<Entry<String, DataType>> it = data.getDataInputs().entrySet().iterator();
		String postString = "";

		while (it.hasNext()) {
			Entry<String, DataType> entry = it.next();
			String inputName = entry.getKey();
			logger.log("The parameter is " + inputName, Severity.DEBUG);

			if (entry.getValue() instanceof URLParameterDataType) {
				String paramValue = ((URLParameterDataType) entry.getValue()).getContent();

				builder = processURLParameterDataTypeMetadata(paramValue, inputName, sMetadata, builder);
			} else if (entry.getValue() instanceof BodyDataType) {
				BodyDataType bdt = (BodyDataType) entry.getValue();
				postString = bdt.getContent();
				requestMimeType = bdt.getMimeType();
				if ((requestMimeType == null) || (requestMimeType.length() == 0)) {
					logger.log("Body mime type not specified", Severity.ERROR);
					return new ResponseEntity<>("Body mime type not specified", HttpStatus.BAD_REQUEST);
				}
			} else {
				// Default behavior for other inputs, put them in list of objects
				// which are transformed into JSON consistent with default requestMimeType
				logger.log("inputName =" + inputName + "entry Value=" + entry.getValue(), Severity.INFORMATIONAL);
				postObjects.put(inputName, entry.getValue());
			}
		}

		logger.log("Final Builder URL" + builder.toUriString(), Severity.INFORMATIONAL);
		if (postObjects.size() > 0) {

			if (postString.length() > 0) {
				logger.log("String Input not consistent with other Inputs", Severity.ERROR);
				return new ResponseEntity<>("String Input not consistent with other Inputs", HttpStatus.BAD_REQUEST);
			}

			try {
				postString = objectMapper.writeValueAsString(postObjects);
			} catch (JsonProcessingException e) {
				LOG.error("Json processing error occurred", e);
				logger.log(e.getMessage(), Severity.ERROR);
				return new ResponseEntity<>("Could not marshal post requests", HttpStatus.BAD_REQUEST);
			}
		}

		logger.log(String.format("Triggered execution of service %s", sMetadata.getServiceId()), Severity.INFORMATIONAL,
				new AuditElement("serviceController", "executingExternalService", sMetadata.getServiceId()));

		return executeJob(sMetadata.getMethod(), requestMimeType, builder.toUriString(), postString);
	}

	private UriComponentsBuilder processURLParameterDataTypeMetadata(final String paramValue, final String inputName,
			final Service sMetadata, final UriComponentsBuilder builder) {

		UriComponentsBuilder localBuilder = builder;

		if (inputName.length() == 0) {
			logger.log("sMetadata.getResourceMeta=" + sMetadata.getResourceMetadata(), Severity.DEBUG);
			localBuilder = UriComponentsBuilder.fromHttpUrl(sMetadata.getUrl() + "?" + paramValue);
			logger.log("Builder URL is " + localBuilder.toUriString(), Severity.DEBUG);
		} else {
			localBuilder.queryParam(inputName, paramValue);
			logger.log("Input Name=" + inputName + " paramValue=" + paramValue, Severity.DEBUG);
		}

		return localBuilder;
	}

	private ResponseEntity<String> executeJob(final String method, final String requestMimeType, final String uri, final String postString)
			throws InterruptedException {

		URI url = URI.create(uri);
		if ("GET".equals(method)) {
			logger.log("GetForEntity URL=" + url, Severity.INFORMATIONAL);
			// execute job
			return template.getForEntity(url, String.class);
		} else if ("POST".equals(method)) {
			HttpHeaders headers = new HttpHeaders();
			// Set the mimeType of the request
			MediaType mediaType = createMediaType(requestMimeType);
			headers.setContentType(mediaType);
			HttpEntity<String> requestEntity = makeHttpEntity(headers, postString);

			logger.log("PostForEntity URL=" + url, Severity.INFORMATIONAL);
			try {
				return template.postForEntity(url, requestEntity, String.class);
			} catch (HttpServerErrorException hex) {
				LOG.info(String.format("Server Error for URL %s:  %s", url, hex.getResponseBodyAsString()), hex);
				throw new InterruptedException(hex.getResponseBodyAsString());
			}
		} else {
			logger.log("Request method type not specified", Severity.ERROR);
			return new ResponseEntity<>("Request method type not specified", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * This method creates a MediaType based on the mimetype that was provided
	 * 
	 * @param mimeType
	 * @return MediaType
	 */
	private MediaType createMediaType(String mimeType) {
		MediaType mediaType;
		String type, subtype;
		StringBuilder sb = new StringBuilder(mimeType);
		int index = sb.indexOf("/");
		// If a slash was found then there is a type and subtype
		if (index != -1) {
			type = sb.substring(0, index);

			subtype = sb.substring(index + 1, mimeType.length());
			mediaType = new MediaType(type, subtype);
		} else {
			// Assume there is just a type for the mime, no subtype
			mediaType = new MediaType(mimeType);
		}

		return mediaType;
	}

	/**
	 * create HttpEntity
	 */
	public HttpEntity<String> makeHttpEntity(HttpHeaders headers, String postString) {
		HttpEntity<String> requestEntity;

		if (postString.length() > 0)
			requestEntity = new HttpEntity<>(postString, headers);
		else
			requestEntity = new HttpEntity<>(headers);

		return requestEntity;

	}

	ObjectMapper makeObjectMapper() {
		return new ObjectMapper();
	}

	/**
	 * Processes the Result of the external Service execution. This will send the Ingest job through the message bus, and will
	 * return the Result of the data.
	 *
	 * @throws JsonProcessingException
	 */
	public DataResult processExecutionResult(Service service, String outputType, String status, ResponseEntity<String> handleResult,
			String dataId) throws IOException, InterruptedException {
		logger.log("Send Execute Status Message", Severity.DEBUG);
		// Initialize ingest job items
		DataResource data = new DataResource();
		PiazzaJobRequest jobRequest = new PiazzaJobRequest();
		IngestJob ingestJob = new IngestJob();
		ObjectMapper objectMapper = new ObjectMapper();

		if (handleResult != null) {
			logger.log("The result provided from service is " + handleResult.getBody(), Severity.DEBUG);

			// String serviceControlString = handleResult.getBody().get(0).toString();
			String serviceControlString = handleResult.getBody();

			logger.log("The service controller string is " + serviceControlString, Severity.DEBUG);

			try {
				// Now produce a new record
				jobRequest.createdBy = service.getResourceMetadata().getCreatedBy();
				data.dataId = dataId;
				logger.log("dataId is " + data.dataId, Severity.DEBUG);
				data = objectMapper.readValue(serviceControlString, DataResource.class);

				// Now check to see if the conversion is actually a proper DataResource
				// if it is not time to create a TextDataType and return
				if ((data == null) || (data.getDataType() == null)) {
					logger.log("The DataResource is not in a valid format, creating a new DataResource and TextDataType", Severity.DEBUG);

					data = new DataResource();
					data.dataId = dataId;
					TextDataType tr = new TextDataType();
					tr.content = serviceControlString;
					logger.log("The data being sent is " + tr.content, Severity.DEBUG);

					data.dataType = tr;
				} else if ((null != data) && (null != data.getDataType())) { // NOSONAR
					data.dataId = dataId;
				}
			} catch (Exception ex) {
				LOG.error("Exception occurred", ex);
				logger.log(ex.getMessage(), Severity.ERROR);

				if (data != null) {
					// Checking payload type and settings the correct type
					if (outputType.equals((new TextDataType()).getClass().getSimpleName())) { // NOSONAR
						TextDataType newDataType = new TextDataType();
						newDataType.content = serviceControlString;
						data.dataType = newDataType;
					} else if (outputType.equals((new GeoJsonDataType()).getClass().getSimpleName())) { // NOSONAR
						GeoJsonDataType newDataType = new GeoJsonDataType();
						newDataType.setGeoJsonContent(serviceControlString);
						data.dataType = newDataType;
					}
				}
			}

			if (Thread.interrupted()) {
				throw new InterruptedException();
			}

			if (data == null)
			{
				return null;
			}

			ingestJob.data = data;
			ingestJob.host = true;
			jobRequest.jobType = ingestJob;

			String jobId = uuidFactory.getUUID();
			jobRequest.jobId = jobId;
			rabbitTemplate.convertAndSend(JobMessageFactory.PIAZZA_EXCHANGE_NAME, requestJobQueue.getName(), objectMapper.writeValueAsString(jobRequest));

			logger.log(String.format("Sending Ingest Job Id %s for Data Id %s for Data of Type %s", jobId, dataId,
					data.getDataType().getClass().getSimpleName()), Severity.INFORMATIONAL);

			// Return the Result of the Data.
			return new DataResult(data.dataId);
		}

		return null;
	}
}