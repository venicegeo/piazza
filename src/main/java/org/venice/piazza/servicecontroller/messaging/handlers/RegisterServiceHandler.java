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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.taskmanaged.ServiceTaskManager;

import model.job.PiazzaJobType;
import model.job.type.RegisterServiceJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.service.metadata.Service;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Handler for handling registerService requests. This handler is used when register-service topics are received
 * or when clients utilize the ServiceController registerService web service.
 * 
 * @author mlynum
 * @version 1.0
 *
 */
@Component
public class RegisterServiceHandler implements PiazzaJobHandler {
	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private PiazzaLogger coreLogger;
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	private ServiceTaskManager serviceTaskManager;

	private static final long HTTP_REQUEST_TIMEOUT = 600;

	/**
	 * Handler for the RegisterServiceJob that was submitted. Stores the metadata in DB
	 * 
	 * @see org.venice.piazza.servicecontroller.messaging.handlers.Handler#handle(model.job.PiazzaJobType)
	 */
	@Override
	public ResponseEntity<String> handle(PiazzaJobType jobRequest) {
		coreLogger.log("Registering a Service", Severity.INFORMATIONAL);
		RegisterServiceJob job = (RegisterServiceJob) jobRequest;

		if (job != null) {
			// Get the Service metadata
			Service serviceMetadata = job.getData();
			coreLogger.log("serviceMetadata received is " + serviceMetadata, Severity.INFORMATIONAL);

			String result = handle(serviceMetadata);
			if (result.length() > 0) {
				String responseString = "{\"resourceId\":" + "\"" + result + "\"}";

				coreLogger.log(String.format("Service registered %s", serviceMetadata.getServiceId()), Severity.INFORMATIONAL,
						new AuditElement("serviceController", "registeredExternalService", serviceMetadata.getServiceId()));

				return new ResponseEntity<>(responseString, HttpStatus.OK);
			} else {
				coreLogger.log("No result response from the handler, something went wrong", Severity.ERROR);
				coreLogger.log(String.format("The service was NOT registered id %s", serviceMetadata.getServiceId()), Severity.ERROR,
						new AuditElement("serviceController", "registerServiceError", serviceMetadata.getServiceId()));
				return new ResponseEntity<>("RegisterServiceHandler handle didn't work", HttpStatus.UNPROCESSABLE_ENTITY);
			}
		} else {
			coreLogger.log("No RegisterServiceJob", Severity.ERROR);
			return new ResponseEntity<>("No RegisterServiceJob", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * Handler for registering the new service with the database.
	 * 
	 * @param service
	 * @return resourceId of the registered service
	 */
	public String handle(Service service) {
		
		if( service == null ) {
			return "";
		}
		
		String resultServiceId = uuidFactory.getUUID();
		service.setServiceId(resultServiceId);

		// Set default request timeout
		if (null == service.getTimeout()) {
			service.setTimeout(HTTP_REQUEST_TIMEOUT);
		}

		// Set the Administrators of the service, if none have been specified.
		if ((service.getIsTaskManaged() != null) && (service.getIsTaskManaged().booleanValue())) {
			
			String createdBy = service.getResourceMetadata().getCreatedBy();
			
			if (service.getTaskAdministrators() == null) {
				// If no administration list has been specified, then create one by default.
				service.setTaskAdministrators(new ArrayList<>());
				service.getTaskAdministrators().add(createdBy);
			} 
			else if (!service.getTaskAdministrators().contains(createdBy)) {
				service.getTaskAdministrators().add(createdBy);
			}

			// Create the Task Management Service Queue for this Service
			serviceTaskManager.createServiceQueue(resultServiceId);
		}

		// Commit
		resultServiceId = accessor.save(service);
		coreLogger.log("Registering a Service with ID " + resultServiceId, Severity.DEBUG);
		coreLogger.log("Successfully Registered service " + service.getServiceId(), Severity.DEBUG);

		return resultServiceId;
	}
}
