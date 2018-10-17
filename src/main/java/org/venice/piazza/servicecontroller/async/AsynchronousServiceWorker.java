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

import java.io.IOException;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.messaging.handlers.ExecuteServiceHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import exception.DataInspectException;
import messaging.job.JobMessageFactory;
import model.job.result.type.DataResult;
import model.job.result.type.ErrorResult;
import model.job.type.ExecuteServiceJob;
import model.logger.Severity;
import model.response.JobResponse;
import model.service.async.AsyncServiceInstance;
import model.service.metadata.Service;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * This Worker will make the direct REST requests to that User Service, for execution, cancelling, and updating status.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class AsynchronousServiceWorker {
	@Value("${async.status.endpoint}")
	private String STATUS_ENDPOINT; //NOSONAR
	@Value("${async.results.endpoint}")
	private String RESULTS_ENDPOINT; //NOSONAR
	@Value("${async.delete.endpoint}")
	private String DELETE_ENDPOINT; //NOSONAR
	@Value("${SPACE}")
	private String SPACE; //NOSONAR
	@Value("${async.status.error.limit}")
	private int STATUS_ERROR_LIMIT; //NOSONAR

	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private ExecuteServiceHandler executeServiceHandler;
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private RabbitTemplate rabbitTemplate;
	@Autowired
	@Qualifier("UpdateJobsQueue")
	private Queue updateJobsQueue;

	private ObjectMapper objectMapper = new ObjectMapper();
	private static final String URL_FORMAT = "%s/%s/%s";
	private static final Logger LOG = LoggerFactory.getLogger(AsynchronousServiceWorker.class);

	/**
	 * Executes the Piazza Job Type
	 * 
	 * @param jobType
	 *            The Piazza Job Type, describing everything about the Service execution.
	 * @throws InterruptedException
	 */
	@Async
	public void executeService(ExecuteServiceJob job) throws InterruptedException {
		// Log the Request
		logger.log(String.format("Processing Asynchronous User Service with Job ID %s", job.getJobId()), Severity.INFORMATIONAL);
		// Handle the external HTTP execution to the Service
		ResponseEntity<String> response = executeServiceHandler.handle(job);
		if (!response.getStatusCode().is2xxSuccessful()) {
			// Execution has failed. Log this as a failure, and send an error status.
			String errorMessage = String.format(
					"Asynchronous Service Failed to Execute for Job ID %s to Service ID %s. Status Code %s was returned with Message %s",
					job.getJobId(), job.data.getServiceId(), response.getStatusCode(), response.getBody());
			logger.log(errorMessage, Severity.ERROR);
			processErrorStatus(job.getJobId(), StatusUpdate.STATUS_ERROR, errorMessage);
		} else {
			try {
				// Convert the response entity into a JobResponse object in order to get the Instance ID
				JobResponse jobResponse = objectMapper.readValue(response.getBody(), JobResponse.class);
				// Create an persist the Async Service Instance Object for this Instance
				AsyncServiceInstance instance = new AsyncServiceInstance(job.getJobId(), job.data.getServiceId(),
						jobResponse.data.getJobId(), null, job.data.getDataOutput().get(0).getClass().getSimpleName());
				accessor.addAsyncServiceInstance(instance);
				// Log the successful start of asynchronous service execution
				logger.log(String.format("Successful start of Asynchronous Execution for Job ID %S with Service ID %s and Instance ID %s",
						instance.getJobId(), instance.getServiceId(), instance.getInstanceId()), Severity.INFORMATIONAL);
			} catch (IOException exception) {
				// The response from the User Service did not conform to the proper model. Log this and flag as a
				// failure.
				String errorMessage = String.format(
						"Could not parse the 2xx HTTP Status response from User Service Execution for Job ID %s. It did not conform to the typical Response format. Details: %s",
						job.getJobId(), exception.getMessage());
				LOG.error(errorMessage, exception);
				logger.log(errorMessage, Severity.ERROR);
				processErrorStatus(job.getJobId(), StatusUpdate.STATUS_ERROR, errorMessage);
			}
		}
	}

	/**
	 * Polls for the Status of the Asynchronous Service Instance. This will update any status information in the Status
	 * table, and will also check if the Status is in a completed State. If a completed state is detected (success or
	 * fail) then it will initialize the logic to handle the result or error.
	 * 
	 * @param instance
	 */
	@Async
	public void pollStatus(AsyncServiceInstance instance) {
		try {
			// Get the Service, so we can fetch the URL
			Service service = accessor.getServiceById(instance.getServiceId());
			// Build the GET URL
			String url = String.format(URL_FORMAT, service.getUrl(), STATUS_ENDPOINT, instance.getInstanceId());

			// Get the Status of the job.
			StatusUpdate status = restTemplate.getForObject(url, StatusUpdate.class);

			if (status == null) {
				throw new DataInspectException("Null Status received from Service.");
			}

			// Act appropriately based on the status received
			if ((status.getStatus().equals(StatusUpdate.STATUS_PENDING)) || (status.getStatus().equals(StatusUpdate.STATUS_RUNNING))
					|| (status.getStatus().equals(StatusUpdate.STATUS_SUBMITTED))) {
				// If this service is not done, then mark the status and we'll poll again later.
				instance.setStatus(status);
				instance.setLastCheckedOn(new DateTime());
				accessor.updateAsyncServiceInstance(instance);
				// Route the current Job Status through Message Bus.
				try {
					status.setJobId(instance.getJobId());
					rabbitTemplate.convertAndSend(JobMessageFactory.PIAZZA_EXCHANGE_NAME, updateJobsQueue.getName(), objectMapper.writeValueAsString(status));
				} catch (JsonProcessingException exception) {
					// The message could not be serialized. Record this.
					LOG.error("Json processing error occured", exception);
					logger.log("Could not send Running Status Message to Job Manager. Error serializing Status: " + exception.getMessage(),
							Severity.ERROR);
				}
			} else if (status.getStatus().equals(StatusUpdate.STATUS_SUCCESS)) {
				// Queue up a subsequent request to get the Result of the Instance
				processSuccessStatus(service, instance);
			} else if ((status.getStatus().equals(StatusUpdate.STATUS_ERROR)) || (status.getStatus().equals(StatusUpdate.STATUS_FAIL))
					|| (status.getStatus().equals(StatusUpdate.STATUS_CANCELLED))) {
				// Errors encountered. Report this and bubble it back up through the Job ID.
				String errorMessage = String.format("Instance %s reported back Status %s. ", instance.getInstanceId(), status.getStatus());
				if (status.getResult() instanceof ErrorResult) {
					// If we can parse any further details on the error, then do so here.
					ErrorResult errorResult = (ErrorResult) status.getResult();
					errorMessage = String.format("%s Details: %s, %s", errorMessage, errorResult.getMessage(), errorResult.getDetails());
				}
				logger.log(errorMessage, Severity.ERROR);
				processErrorStatus(instance.getJobId(), status.getStatus(), errorMessage);
			} else {
				// If it's an unknown status, then we can't process it.
				updateFailureCount(instance);
				logger.log(String.format(
						"Unknown Status %s encountered for Service ID %s Instance %s under Job ID %s. The number of Errors has been incremented (%s)",
						status.getStatus(), instance.getServiceId(), instance.getInstanceId(), instance.getJobId(),
						instance.getNumberErrorResponses()), Severity.WARNING);
			}
		} catch (HttpClientErrorException | HttpServerErrorException exception) {
			updateFailureCount(instance);
			String error = String.format(
					"HTTP Error Status %s encountered for Service ID %s Instance %s under Job ID %s. The number of Errors has been incremented (%s)",
					exception.getStatusCode().toString(), instance.getServiceId(), instance.getInstanceId(), instance.getJobId(),
					instance.getNumberErrorResponses());
			LOG.error(error, exception);
			logger.log(error, Severity.WARNING);
		} catch (Exception exception) {
			updateFailureCount(instance);
			String error = String.format(
					"Unexpected Error %s encountered for Service ID %s Instance %s under Job ID %s. The number of Errors has been incremented (%s)",
					exception.getMessage(), instance.getServiceId(), instance.getInstanceId(), instance.getJobId(),
					instance.getNumberErrorResponses());
			LOG.error(error, exception);
			logger.log(error, Severity.WARNING);
		}
	}

	/**
	 * Updates the failure count for the Instance.
	 * 
	 * @param instance
	 *            The Instance.
	 */
	public void updateFailureCount(AsyncServiceInstance instance) {
		// Increment the failure count
		instance.setNumberErrorResponses(instance.getNumberErrorResponses() + 1);
		// Check if the Failure count is above the threshold. If so, then fail the job.
		if (instance.getNumberErrorResponses() > STATUS_ERROR_LIMIT) {
			// Failure threshold has been reached. Fail the job.
			String errorMessage = String.format(
					"Job ID %s for Service ID %s Instance ID %s has failed too many times during periodic Status Checks. This Job is being marked as a failure.",
					instance.getJobId(), instance.getServiceId(), instance.getInstanceId());
			logger.log(errorMessage, Severity.ERROR);
			// Remove this from the Collection of tracked instance Jobs.
			accessor.deleteAsyncServiceInstance(instance.getJobId());
			// Send a Failure message back to the Job Manager via Message Bus.
			processErrorStatus(instance.getJobId(), StatusUpdate.STATUS_ERROR, errorMessage);
		} else {
			// Update the Database that this instance has failed.
			accessor.updateAsyncServiceInstance(instance);
		}
	}

	/**
	 * Handles a successful Instance. This will make a call to the results endpoint to grab the results of the service.
	 * 
	 * @param service
	 *            The Service metadata information (used to grab URL, etc)
	 * @param instance
	 *            The User Service execution instance
	 */
	private void processSuccessStatus(Service service, AsyncServiceInstance instance) {
		// Log
		logger.log(String.format("Handling Successful status of Instance %s for Service %s under Job ID %s", instance.getInstanceId(),
				instance.getServiceId(), instance.getJobId()), Severity.INFORMATIONAL);
		// Make a request to the results endpoint to get the results of the Service
		String url = String.format(URL_FORMAT, service.getUrl(), RESULTS_ENDPOINT, instance.getInstanceId());
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			String dataId = uuidFactory.getUUID();
			// Get the Result of the Service
			DataResult result = executeServiceHandler.processExecutionResult(service, instance.getOutputType(), StatusUpdate.STATUS_SUCCESS,
					response, dataId);
			// Send the Completed Status to the Job Manager, including the Result
			StatusUpdate statusUpdate = new StatusUpdate(StatusUpdate.STATUS_SUCCESS);
			statusUpdate.setResult(result);
			statusUpdate.setJobId(instance.getJobId());
			rabbitTemplate.convertAndSend(JobMessageFactory.PIAZZA_EXCHANGE_NAME, updateJobsQueue.getName(), objectMapper.writeValueAsString(statusUpdate));
			// Remove this Instance from the Instance table
			accessor.deleteAsyncServiceInstance(instance.getJobId());
		} catch (HttpClientErrorException | HttpServerErrorException exception) {
			updateFailureCount(instance);

			String error = String.format(
					"Error fetching Service results: HTTP Error Status %s encountered for Service ID %s Instance %s under Job ID %s. The number of Errors has been incremented (%s)",
					exception.getStatusCode().toString(), instance.getServiceId(), instance.getInstanceId(), instance.getJobId(),
					instance.getNumberErrorResponses());
			LOG.error(error, exception);
			logger.log(error, Severity.WARNING);
		} catch (Exception exception) {
			updateFailureCount(instance);
			String error = String.format(
					"Unexpected Error fetching Service results: %s encountered for Service ID %s Instance %s under Job ID %s. The number of Errors has been incremented (%s)",
					exception.getMessage(), instance.getServiceId(), instance.getInstanceId(), instance.getJobId(),
					instance.getNumberErrorResponses());
			LOG.error(error, exception);
			logger.log(error, Severity.WARNING);
		}
	}

	/**
	 * Handles a non-success completed Status message.
	 * 
	 * @param instance
	 *            The service instance.
	 * @param serviceStatus
	 *            The StatusUpdate received from the external User Service
	 */
	private void processErrorStatus(String jobId, String status, String message) {
		// Remove the Instance from the Instance Table
		accessor.deleteAsyncServiceInstance(jobId);

		// Create a new Status Update to send to the Job Manager.
		StatusUpdate statusUpdate = new StatusUpdate();
		statusUpdate.setStatus(status);
		// Create the Message for the Error Result of the Status
		statusUpdate.setResult(new ErrorResult(message, null));

		// Send the Job Status through the Message Bus.
		try {
			rabbitTemplate.convertAndSend(JobMessageFactory.PIAZZA_EXCHANGE_NAME, updateJobsQueue.getName(), objectMapper.writeValueAsString(status));
		} catch (JsonProcessingException exception) {
			// The message could not be serialized. Record this.
			LOG.error("Could not send Error Status to Job Manager. Error serializing Status", exception);
			logger.log("Could not send Error Status to Job Manager. Error serializing Status: " + exception.getMessage(), Severity.ERROR);
		}
	}

	/**
	 * Sends the cancellation status to the external User Service for the specified instance.
	 * 
	 * @param instance
	 *            The instance to be cancelled
	 */
	@Async
	public void sendCancellationStatus(AsyncServiceInstance instance) {
		// Remove this from the Instance Table
		accessor.deleteAsyncServiceInstance(instance.getJobId());

		// Send the DELETE request to the external User Service
		Service service = accessor.getServiceById(instance.getServiceId());
		if (service != null) {
			String url = String.format(URL_FORMAT, service.getUrl(), DELETE_ENDPOINT, instance.getInstanceId());
			try {
				restTemplate.delete(url);
			} catch (HttpClientErrorException | HttpServerErrorException exception) {
				String error = String.format(
						"Error Cancelling Service Instance on external User Service: HTTP Error Status %s encountered for Service ID %s Instance %s under Job ID %s. No subsequent calls will be made.",
						exception.getStatusCode().toString(), instance.getServiceId(), instance.getInstanceId(), instance.getJobId());
				LOG.error(error, exception);
				logger.log(error, Severity.WARNING);
			}
		}

		// Send the Message for successful Cancellation status
		try {
			StatusUpdate statusUpdate = new StatusUpdate(StatusUpdate.STATUS_CANCELLED);
			statusUpdate.setJobId(instance.getJobId());
			rabbitTemplate.convertAndSend(JobMessageFactory.PIAZZA_EXCHANGE_NAME, updateJobsQueue.getName(), objectMapper.writeValueAsString(statusUpdate));
		} catch (JsonProcessingException jsonException) {
			String error = String.format(
					"Error sending Cancelled Status from Job %s: %s. The Job was cancelled, but its status will not be updated in the Job Manager.",
					instance.getJobId(), jsonException.getMessage());
			LOG.error(error, jsonException);
			logger.log(error, Severity.ERROR);
		}
	}
}
