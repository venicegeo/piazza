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
package org.venice.piazza.jobmanager.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import exception.InvalidInputException;
import org.venice.piazza.jobmanager.database.JobManagerDatabaseAccessor;
import org.venice.piazza.jobmanager.messaging.handler.AbortJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RepeatJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RequestJobHandler;
import model.job.Job;
import model.job.type.AbortJob;
import model.job.type.RepeatJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.JobListResponse;
import model.response.JobResponse;
import model.response.JobStatusResponse;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

@RestController
public class JobManagerController {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	private JobManagerDatabaseAccessor accessor;
	@Autowired
	private AbortJobHandler abortJobHandler;
	@Autowired
	private RepeatJobHandler repeatJobHandler;
	@Autowired
	private RequestJobHandler requestJobHandler;
	@Autowired
	private ThreadPoolTaskExecutor threadPoolTaskExecutor;
	@Value("${SPACE}")
	private String space;

	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";

	private static final Logger LOG = LoggerFactory.getLogger(JobManagerController.class);
	private static final String ERROR_MSG = "Job not found: %s";
	private static final String JOB_MGR_UPPER = "Job Manager";
	private static final String JOB_MGR_LOWER = "jobmanager";

	/**
	 * Healthcheck required for all Piazza Core Services
	 * 
	 * @return String
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getHealthCheck() {
		return "Hello, Health Check here for pz-jobmanager.";
	}

	/**
	 * Returns the Job Status and potential Results of the specified Job Id. This is used when the Gateway needs a
	 * synchronous response for the specific status of a Job.
	 * 
	 * @param jobId
	 *            The Job Id.
	 * @return The StatusResponse object of the Job. This will, at the very least, involved the readiness of the Job. If
	 *         not ready, the available Status and Progress of the Job will be included in this response object. If the
	 *         job is ready, then this Response will contain an Object reference to the output produced by the Job.
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PiazzaResponse> getJobStatus(@PathVariable(value = "jobId") String jobId) {
		try {
			if (jobId.isEmpty()) {
				throw new InvalidInputException("No Job Id specified.");
			}
			// Query for the Job Id
			Job job = accessor.getJobById(jobId);
			// If no Job was found.
			if (job == null) {
				logger.log(String.format("Job not found for requested Id %s", jobId), Severity.WARNING);
				return new ResponseEntity<>(new ErrorResponse(String.format(ERROR_MSG, jobId), JOB_MGR_UPPER),
						HttpStatus.NOT_FOUND);
			}
			// Return Job Status
			logger.log(String.format("Returning Job Status for %s", jobId), Severity.INFORMATIONAL,
					new AuditElement(JOB_MGR_LOWER, "readJob", jobId));
			return new ResponseEntity<>(new JobStatusResponse(job), HttpStatus.OK);
		} catch (Exception exception) {
			String error = String.format("Error fetching a Job %s: %s", jobId, exception.getMessage());
			logger.log(error, Severity.ERROR, new AuditElement(JOB_MGR_LOWER, "failedToAccessJob", jobId));
			LOG.error(error, exception);
			return new ResponseEntity<>(new ErrorResponse("Error fetching Job: " + exception.getMessage(), JOB_MGR_UPPER),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Sends a new Piazza Job Request to the Job Manager. This will add a new entry in the Jobs table for the request,
	 * and will also proxy off the message to the worker components.
	 * 
	 * @param request
	 *            The job request
	 * @param jobId
	 *            The Id of the job to create. Optional. If specified, this will be used for the Job Id. If not
	 *            specified, then one will be randomly generated.
	 * @return The Response, containing the Job Id, or an Error
	 */
	@RequestMapping(value = "/requestJob", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PiazzaResponse> requestJob(@RequestBody PiazzaJobRequest request,
			@RequestParam(value = "jobId", required = false) String jobId) {

		// Generate a Job Id if needed
		final String finalJobId = jobId.isEmpty() ? uuidFactory.getUUID() : jobId;

		try {

			// Create the Job and send off the Job message
			requestJobHandler.process(request, finalJobId);
			// Return to the user the Job Id.
			return new ResponseEntity<>(new JobResponse(finalJobId), HttpStatus.OK);
		} catch (Exception exception) {
			String error = String.format("Error Requesting Job: %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR, new AuditElement(request.createdBy, "errorRequestingJob", finalJobId));
			return new ResponseEntity<>(new ErrorResponse(error, JOB_MGR_UPPER), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Aborts a currently running Piazza Job.
	 * 
	 * @param request
	 *            The request, detailing the AbortJob type and the user who has requested this action.
	 * @return null response if successful. ErrorResponse containing appropriate error details on exception.
	 */
	@RequestMapping(value = "/abort", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PiazzaResponse> abortJob(@RequestBody PiazzaJobRequest request) {
		try {
			// Verify the Job exists
			String jobId = ((AbortJob) request.jobType).getJobId();
			Job jobToCancel = accessor.getJobById(jobId);
			if (jobToCancel == null) {
				return new ResponseEntity<>(new ErrorResponse(String.format(ERROR_MSG, jobId), JOB_MGR_UPPER),
						HttpStatus.NOT_FOUND);
			}
			String currentStatus = jobToCancel.getStatus();
			if ((currentStatus.equals(StatusUpdate.STATUS_RUNNING)) || (currentStatus.equals(StatusUpdate.STATUS_PENDING))
					|| (currentStatus.equals(StatusUpdate.STATUS_SUBMITTED))) {
				// Abort the Job in the Jobs table.
				abortJobHandler.process(request);
				// Log the successful Cancellation
				logger.log(String.format("Successfully indexed Cancelled Job %s by User %s", ((AbortJob) request.jobType).getJobId(),
						request.createdBy), Severity.INFORMATIONAL, new AuditElement(JOB_MGR_LOWER, "abortJob", jobId));
				return new ResponseEntity<>(
						new SuccessResponse("Job " + jobId + " was requested to be cancelled.", JOB_MGR_UPPER), HttpStatus.OK);
			} else {
				return new ResponseEntity<>(new ErrorResponse(String
						.format("Could not Abort Job because it is no longer running. The Job reported a status of %s", currentStatus),
						JOB_MGR_UPPER), HttpStatus.BAD_REQUEST);
			}
		} catch (Exception exception) {
			String error = String.format("Error Cancelling Job: %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR,
					new AuditElement(request.createdBy, "errorCancellingJob", ((AbortJob) request.jobType).getJobId()));
			return new ResponseEntity<>(new ErrorResponse("Error Cancelling Job: " + exception.getMessage(), JOB_MGR_UPPER),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Repeats an existing Job within Piazza.
	 * 
	 * @param request
	 *            The request, detailing the RepeatJob type and the user who has submitted this request.
	 * @return The response containing the Job Id if successful. Appropriate error details returned on exception.
	 * 
	 */
	@RequestMapping(value = "/repeat", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<PiazzaResponse> repeatJob(@RequestBody PiazzaJobRequest request) {
		try {
			// Verify the Job exists
			String jobId = ((RepeatJob) request.jobType).jobId;
			Job jobToRepeat = accessor.getJobById(jobId);

			if (jobToRepeat == null) {
				return new ResponseEntity<>(new ErrorResponse(String.format(ERROR_MSG, jobId), JOB_MGR_UPPER),
						HttpStatus.NOT_FOUND);
			}

			// Repeat the Job; ASYNC process
			final String newJobId = uuidFactory.getUUID();
			repeatJobHandler.process(jobToRepeat, newJobId);

			// Log the successful Repetition of the Job
			logger.log(
					String.format("Successfully created a Repeat Job under Id %s for original Job Id %s by user %s", newJobId,
							((RepeatJob) request.jobType).getJobId(), request.createdBy),
					Severity.INFORMATIONAL, new AuditElement(request.createdBy, "repeatedJob", ((RepeatJob) request.jobType).getJobId()));

			// Return the Job Id
			return new ResponseEntity<>(new JobResponse(newJobId), HttpStatus.CREATED);
		} catch (Exception exception) {
			String error = String.format("Error Repeating Job: %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR, new AuditElement(request.createdBy, "errorProcessingRepeatJob", ""));
			return new ResponseEntity<>(new ErrorResponse("Error Repeating Job: %s" + exception.getMessage(), JOB_MGR_UPPER),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns Jobs currently held by the Piazza Job table.
	 * 
	 * This is intended to be used by the Swiss-Army-Knife (SAK) administration application for reporting the status of
	 * this Job Manager component. It is not used in normal function of the Job Manager.
	 * 
	 * @param page
	 *            The start page
	 * @param pageSize
	 *            The number of results per page
	 * @return The List of all Jobs in the system.
	 */
	@RequestMapping(value = "/job", method = RequestMethod.GET)
	public JobListResponse getJobs(@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) String page,
			@RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) String pageSize,
			@RequestParam(value = "order", required = false, defaultValue = "asc") String order,
			@RequestParam(value = "sortBy", required = false, defaultValue = "createdOn") String sortBy,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "userName", required = false) String userName) {

		final String finalOrder;

		// Don't allow for invalid orders
		if (!("asc".equalsIgnoreCase(order)) && !("desc".equalsIgnoreCase(order))) {
			finalOrder = "asc";
		} else {
			finalOrder = order;
		}

		// Get and return
		logger.log("Looking up Job Query", Severity.INFORMATIONAL, new AuditElement(JOB_MGR_LOWER, "queryingJobs", ""));
		return accessor.getJobs(Integer.parseInt(page), Integer.parseInt(pageSize), finalOrder, sortBy, status, userName);
	}

	/**
	 * Returns the Number of Jobs in the piazza system.
	 * 
	 * This is intended to be used by the Swiss-Army-Knife (SAK) administration application for reporting the status of
	 * this Job Manager component. It is not used in normal function of the Job Manager.
	 * 
	 * @return Number of Jobs in the system.
	 */
	@RequestMapping(value = "/job/count", method = RequestMethod.GET)
	public long getJobCount() {
		return accessor.getJobsCount();
	}

	/**
	 * Gets all Job Status types that can be queried for by the system.
	 * 
	 * This is intended to be used by the Swiss-Army-Knife (SAK) administration application for reporting the status of
	 * this Job Manager component. It is not used in normal function of the Job Manager.
	 * 
	 * @return List of Job Status types that can be queried for in the system.
	 */
	@RequestMapping(value = "/job/status", method = RequestMethod.GET)
	public List<String> getStatuses() {
		List<String> statuses = new ArrayList<>();
		statuses.add(StatusUpdate.STATUS_CANCELLED);
		statuses.add(StatusUpdate.STATUS_CANCELLING);
		statuses.add(StatusUpdate.STATUS_ERROR);
		statuses.add(StatusUpdate.STATUS_FAIL);
		statuses.add(StatusUpdate.STATUS_PENDING);
		statuses.add(StatusUpdate.STATUS_RUNNING);
		statuses.add(StatusUpdate.STATUS_SUBMITTED);
		statuses.add(StatusUpdate.STATUS_SUCCESS);
		return statuses;
	}

	/**
	 * Gets the count for all Jobs of the specified status.
	 * 
	 * This is intended to be used by the Swiss-Army-Knife (SAK) administration application for reporting the status of
	 * this Job Manager component. It is not used in normal function of the Job Manager.
	 * 
	 * @return List of Jobs that match the specified status.
	 */
	@RequestMapping(value = "/job/status/{status}/count", method = RequestMethod.GET)
	public Long getStatusCount(@PathVariable(value = "status") String status) {
		return accessor.getJobStatusCount(status);
	}

	/**
	 * Returns administrative statistics for this component.
	 * 
	 * @return Component information
	 */
	@RequestMapping(value = "/admin/stats", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getAdminStats() {
		Map<String, Object> stats = new HashMap<>();
		// Add information related to the Jobs in the system
		stats.put("total", accessor.getJobsCount());
		stats.put("success", getStatusCount(StatusUpdate.STATUS_SUCCESS));
		stats.put("error", getStatusCount(StatusUpdate.STATUS_ERROR));
		stats.put("fail", getStatusCount(StatusUpdate.STATUS_FAIL));
		stats.put("running", getStatusCount(StatusUpdate.STATUS_RUNNING));
		stats.put("pending", getStatusCount(StatusUpdate.STATUS_PENDING));
		stats.put("submitted", getStatusCount(StatusUpdate.STATUS_SUBMITTED));
		stats.put("cancelled", getStatusCount(StatusUpdate.STATUS_CANCELLED));
		stats.put("cancelling", getStatusCount(StatusUpdate.STATUS_CANCELLING));
		stats.put("activeThreads", threadPoolTaskExecutor.getActiveCount());
		if (threadPoolTaskExecutor.getThreadPoolExecutor() != null) {
			stats.put("threadQueue", threadPoolTaskExecutor.getThreadPoolExecutor().getQueue().size());
		}
		return new ResponseEntity<>(stats, HttpStatus.OK);
	}
}