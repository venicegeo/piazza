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


import java.util.ArrayList;

import model.job.PiazzaJobType;
import model.job.type.DeleteServiceJob;
import model.logger.AuditElement;
import model.logger.Severity;
import util.PiazzaLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

/**
 * Handler for handling registerService requests.  This handler is used 
 * when register-service messages are received or when clients utilize the 
 * ServiceController registerService web service.
 * @author mlynum & Sonny.Saniev
 * @version 1.0
 *
 */

@Component
public class DeleteServiceHandler implements PiazzaJobHandler {

	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private PiazzaLogger coreLogger;

	private static final Logger LOG = LoggerFactory.getLogger(DeleteServiceHandler.class);
	
	/**
	 * Handler for the DeleteServiceJob that was submitted. Stores the metadata
	 * in the DB (non-Javadoc)
	 * 
	 * @see org.venice.piazza.servicecontroller.messaging.handlers.Handler#handle(
	 *      model.job.PiazzaJobType)
	 */
	@Override
	public ResponseEntity<String> handle(PiazzaJobType jobRequest) {
		ResponseEntity<String> responseEntity;

		if (jobRequest != null) {
			coreLogger.log("Deleting a service", Severity.DEBUG);
			DeleteServiceJob job = (DeleteServiceJob) jobRequest;

			// Get the ResourceMetadata
			String resourceId = job.getServiceID();
			coreLogger.log("deleteService serviceId=" + resourceId, Severity.INFORMATIONAL);

			String result = handle(resourceId, false);
			if ((result != null) && (result.length() > 0)) {
				String jobId = job.getJobId();
				ArrayList<String> resultList = new ArrayList<>();
				resultList.add(jobId);
				resultList.add(resourceId);
				responseEntity = new ResponseEntity<>(resultList.toString(), HttpStatus.OK);
			} else {
				coreLogger.log("No result response from the handler, something went wrong", Severity.ERROR);
				responseEntity = new ResponseEntity<>("DeleteServiceHandler handle didn't work", HttpStatus.NOT_FOUND);
			}
		} else {
			coreLogger.log("A null PiazzaJobRequest was passed in. Returning null", Severity.ERROR);
			responseEntity = new ResponseEntity<>("A Null PiazzaJobRequest was received", HttpStatus.BAD_REQUEST);
		}

		return responseEntity;
	}

	/**
	 * Deletes resource by removing from the database
	 * 
	 * @param rMetadata
	 * @return resourceId of the registered service
	 */
	public String handle(String resourceId, boolean softDelete) {
		coreLogger.log(String.format("Deleting Registered Service: %s with softDelete %s", resourceId, softDelete), Severity.INFORMATIONAL);
		String result = "";
		try {
			result = accessor.delete(resourceId, softDelete);
		} catch (Exception e) {
			LOG.error("Unable to delete from the database", e);
			coreLogger.log(e.toString(), Severity.ERROR);
		}

		if ((result != null) && (result.length() > 0)) {
			coreLogger.log("The service with id " + resourceId + " was deleted " + result, Severity.INFORMATIONAL);
			coreLogger.log(String.format("The service was deleted id %s", resourceId), Severity.INFORMATIONAL,
					new AuditElement("serviceController", "deletedRegisteredService", resourceId));
		} else {
			coreLogger.log("The service with id " + resourceId + " was NOT deleted", Severity.INFORMATIONAL);
			coreLogger.log(String.format("The service was NOT deleted id %s", resourceId), Severity.ERROR, new AuditElement("serviceController", "failedToDelete", resourceId));
		}

		return result;
	}
}
