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
package org.venice.piazza.jobmanager.messaging.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.venice.piazza.jobmanager.database.JobManagerDatabaseAccessor;
import messaging.job.JobMessageFactory;
import model.job.Job;
import model.logger.AuditElement;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Handles the Message Queue for the requesting of a Job on the "Request-Job" topic. This will relay the "Create-Job"
 * job topic to the appropriate worker components, while additionally adding in the Job metadata to the Jobs table.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class RequestJobHandler {
	@Autowired
	private RabbitTemplate rabbitTemplate;
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	private JobManagerDatabaseAccessor accessor;
	@Value("${SPACE}")
	private String space;
	@Value("${logger.console.job.payloads:false}")
	private Boolean logJobPayloadsToConsole;

	private static final Logger LOG = LoggerFactory.getLogger(RequestJobHandler.class);
	ObjectMapper mapper = new ObjectMapper();

	/**
	 * Processes a new Piazza Job Request. This will add the Job metadata into the Jobs table, and then fire the Message
	 * event to the worker components to process the Job.
	 * 
	 * @param jobRequest
	 *            The Job Request
	 * @param jobId
	 *            the Job Id
	 */
	public void process(PiazzaJobRequest jobRequest, String jobId) {
		try {
			Job job = new Job(jobRequest, jobId);
			// If the job was submitted internally, the submitter
			// wouldn't give it an Id. Assign a random Id here. (If
			// submitted via the Gateway, the Gateway will assign
			// the Id)
			if (job.getJobId().isEmpty()) {
				job.setJobId(uuidFactory.getUUID());
			}
			// Commit the Job metadata to the Jobs table
			accessor.addJob(job);

			// Send the content of the actual Job under the
			// topic name of the Job type for all workers to
			// listen to.
			String queueName = String.format(JobMessageFactory.TOPIC_TEMPLATE, job.getJobType().getClass().getSimpleName(), space);
			rabbitTemplate.convertAndSend(JobMessageFactory.PIAZZA_EXCHANGE_NAME, queueName, mapper.writeValueAsString(job));

			// Log default to Piazza Logger
			logger.log(
					String.format("Relayed Job Id %s for Type %s on Message Queue %s", job.getJobId(),
							job.getJobType().getClass().getSimpleName(), queueName),
					Severity.INFORMATIONAL, new AuditElement(jobRequest.createdBy, "relayedJobCreation", jobId));
			// If extended logging is enabled, then log the payload of the job.
			if (logJobPayloadsToConsole.booleanValue() && LOG.isInfoEnabled()) {
				LOG.info(String.format("Job Id %s payload was: %s", job.getJobId(), mapper.writeValueAsString(job)));
			}
		} catch (Exception exception) {
			String error = String.format("Error Processing Request-Job with error %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
		}
	}
}
