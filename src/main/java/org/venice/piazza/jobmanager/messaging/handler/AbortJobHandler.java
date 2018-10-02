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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import exception.PiazzaJobException;
import org.venice.piazza.jobmanager.database.DatabaseAccessor;
import model.job.Job;
import model.job.type.AbortJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import model.status.StatusUpdate;
import util.PiazzaLogger;

/**
 * Handles the request for Aborting a Job by updating the Job table with the new Status.
 * 
 * @author Patrick.Doody
 * 
 */
@Component
public class AbortJobHandler {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private DatabaseAccessor accessor;

	private static final Logger LOG = LoggerFactory.getLogger(AbortJobHandler.class);

	/**
	 * Processes a Job request to cancel a Piazza job.
	 * 
	 * @param request
	 *            Job request.
	 */
	public void process(PiazzaJobRequest request) throws PiazzaJobException {
		final AbortJob abortJob = (AbortJob) request.jobType;
		Job jobToCancel = null;
		
		try {
			jobToCancel = accessor.getJobById(abortJob.getJobId());
		} catch (ResourceAccessException e) {
			String error = String.format("Could not retrieve Abort Job by ID: %s", abortJob.getJobId());
			LOG.info(error, e);
			logger.log(error, Severity.INFORMATIONAL);
		}
		
		if (jobToCancel == null) {
			throw new PiazzaJobException(String.format("No job could be founding matching Id %s", abortJob.getJobId()));
		}
		
		// Ensure the user has permission to cancel the Job.
		if ((jobToCancel.getCreatedBy() == null) || (!jobToCancel.getCreatedBy().equals(request.createdBy))) {
			throw new PiazzaJobException(
					String.format("Could not Abort Job %s because it was not requested by the originating user.", abortJob.getJobId()));
		}
		String currentStatus = jobToCancel.getStatus();
		if ((currentStatus.equals(StatusUpdate.STATUS_RUNNING)) || (currentStatus.equals(StatusUpdate.STATUS_PENDING))
				|| (currentStatus.equals(StatusUpdate.STATUS_SUBMITTED))) {
			logger.log("Requesting Job Cancellation", Severity.INFORMATIONAL, new AuditElement(request.createdBy, "cancellingJob", jobToCancel.getJobId()));
			accessor.updateJobStatus(abortJob.getJobId(), StatusUpdate.STATUS_CANCELLING);
		} else {
			String error = String.format("Could not Abort Job %s because it is no longer running.", abortJob.getJobId());
			logger.log(error, Severity.INFORMATIONAL);
		}
	}
}
