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
package org.venice.piazza.servicecontroller.messaging;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.async.AsyncServiceInstanceScheduler;
import org.venice.piazza.servicecontroller.taskmanaged.ServiceTaskManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import messaging.job.JobMessageFactory;
import messaging.job.WorkerCallback;
import model.job.Job;
import model.job.type.AbortJob;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import util.PiazzaLogger;

/**
 * Listener class that processes incoming Messages on the Queue
 * 
 * @author Patrick.Doody, Marge.Lynum
 *
 */
@Component
public class ServiceMessageThreadManager {
	@Autowired
	private PiazzaLogger coreLogger;
	@Autowired
	private ServiceMessageWorker serviceMessageWorker;
	@Autowired
	private ServiceTaskManager serviceTaskManager;
	@Autowired
	private AsyncServiceInstanceScheduler asyncServiceInstanceManager;
	@Autowired
	private ObjectMapper mapper;

	@Value("${SPACE}")
	private String SPACE; //NOSONAR

	private static final Logger LOG = LoggerFactory.getLogger(ServiceMessageThreadManager.class);
	private Map<String, Future<?>> runningServiceRequests = new HashMap<>();

	/**
	 * Processes a message for a request to execute a service job
	 * 
	 * @param serviceJobRequest
	 *            The ExecuteServiceJob Request with the Execution information
	 */
	@RabbitListener(bindings = @QueueBinding(key = "ExecuteServiceJob-${SPACE}", value = @Queue(value = "ServiceControllerJob-${SPACE}", autoDelete = "false", durable = "true"), exchange = @Exchange(value = JobMessageFactory.PIAZZA_EXCHANGE_NAME, autoDelete = "false", durable = "true")))
	public void processServiceExecutionJob(String serviceJobRequest) {
		try {
			// Callback that will be invoked when a Worker completes. This will
			// remove the Job Id from the running Jobs list.
			WorkerCallback callback = (String jobId) -> runningServiceRequests.remove(jobId);
			// Get the Job Model
			Job job = mapper.readValue(serviceJobRequest, Job.class);
			// Process the work
			Future<?> workerFuture = serviceMessageWorker.run(job, callback);
			// Keep track of this running Job's ID
			runningServiceRequests.put(job.getJobId(), workerFuture);
		} catch (IOException exception) {
			String error = String.format("Error Reading Execution Job Message from Queue %s", exception.getMessage());
			LOG.error(error, exception);
			coreLogger.log(error, Severity.ERROR);
		}
	}

	/**
	 * Process a message for cancelling a Job. If this instance of the Service Controller contains this job, it will be
	 * terminated.
	 * 
	 * @param abortJobRequest
	 *            The information regarding the job to abort
	 */
	@RabbitListener(bindings = @QueueBinding(key = "AbortJob-${SPACE}", value = @Queue(autoDelete = "true", durable = "true", exclusive = "true"), exchange = @Exchange(value = JobMessageFactory.PIAZZA_EXCHANGE_NAME, autoDelete = "false", durable = "true")))
	public void processAbortJob(String abortJobRequest) {
		// Get the Job ID
		String jobId = null;
		try {
			PiazzaJobRequest request = mapper.readValue(abortJobRequest, PiazzaJobRequest.class);
			jobId = ((AbortJob) request.jobType).getJobId();
		} catch (Exception exception) {
			String error = String.format("Error Aborting Job. Could not get the Job ID from the Message with error:  %s",
					exception.getMessage());
			LOG.error(error, exception);
			coreLogger.log(error, Severity.ERROR);
		}

		// Determine if this a Sync or Async job
		if (runningServiceRequests.containsKey(jobId)) {
			// Cancel the Running Synchronous Job by terminating its thread
			boolean cancelled = runningServiceRequests.get(jobId).cancel(true);
			if (cancelled) {
				// Log the cancellation has occurred
				coreLogger.log(String.format("Successfully requested termination of Job thread for Job ID %s", jobId),
						Severity.INFORMATIONAL);
			} else {
				coreLogger.log(
						String.format("Attempted to Cancel running job thread for ID %s, but the thread could not be forcefully cancelled.",
								jobId),
						Severity.ERROR);
			}
			// Remove it from the list of Running Jobs
			runningServiceRequests.remove(jobId);
		} else {
			// Is this a Task Managed Job? Remove it from the Jobs queue if it is pending.
			serviceTaskManager.cancelJob(jobId);
			// Is this an Async Job? Send a cancellation to the running service.
			asyncServiceInstanceManager.cancelInstance(jobId);
		}
	}

}
