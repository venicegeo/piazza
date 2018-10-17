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
package org.venice.piazza.servicecontroller.data.accessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.venice.piazza.common.hibernate.dao.AsyncServiceInstanceDao;
import org.venice.piazza.common.hibernate.dao.ServiceJobDao;
import org.venice.piazza.common.hibernate.dao.job.JobDao;
import org.venice.piazza.common.hibernate.dao.service.ServiceDao;
import org.venice.piazza.common.hibernate.entity.AsyncServiceInstanceEntity;
import org.venice.piazza.common.hibernate.entity.JobEntity;
import org.venice.piazza.common.hibernate.entity.ServiceEntity;
import org.venice.piazza.common.hibernate.entity.ServiceJobEntity;

import exception.InvalidInputException;
import model.job.Job;
import model.job.metadata.ResourceMetadata;
import model.logger.AuditElement;
import model.logger.Severity;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.response.ServiceListResponse;
import model.service.async.AsyncServiceInstance;
import model.service.metadata.Service;
import model.service.taskmanaged.ServiceJob;
import util.PiazzaLogger;

/**
 * Class to store Service information in the Database.
 * 
 * @author mlynum
 * 
 */
@Component
public class DatabaseAccessor {
	@Value("${async.stale.instance.threshold.seconds}")
	private int STALE_INSTANCE_THRESHOLD_SECONDS; //NOSONAR
	@Autowired
	private PiazzaLogger logger;

	@Autowired
	private ServiceDao serviceDao;
	@Autowired
	private ServiceJobDao serviceJobDao;
	@Autowired
	private AsyncServiceInstanceDao asyncServiceInstanceDao;
	@Autowired
	private JobDao jobDao;

	private static final Logger LOG = LoggerFactory.getLogger(DatabaseAccessor.class);
	private static final String SERVICE_CTR = "serviceController";

	/**
	 * Deletes existing registered service from the Database
	 * 
	 * @param serviceId
	 *            Service id to be deleted
	 * @param softDelete
	 *            If softDelete is true, updates status to OFFLINE instead of deleting.
	 *
	 * @return Message string containing result
	 */
	public String delete(String serviceId, boolean softDelete) {
		String result = null;
		if (softDelete) {
			ServiceEntity serviceEntity = serviceDao.getServiceById(serviceId);
			if (serviceEntity != null) {
				if (serviceEntity.getService().getResourceMetadata() == null) {
					serviceEntity.getService().setResourceMetadata(new ResourceMetadata());
				}
				serviceEntity.getService().getResourceMetadata().setAvailability(ResourceMetadata.STATUS_TYPE.OFFLINE.toString());
				serviceDao.save(serviceEntity);
				result = " service " + serviceId + " was disabled ";
			}
		} else {
			logger.log(String.format("Deleting resource from DB %s", serviceId), Severity.INFORMATIONAL,
					new AuditElement(SERVICE_CTR, "Deleted Service", serviceId));
			ServiceEntity entity = serviceDao.getServiceById(serviceId);
			if (entity != null) {
				serviceDao.delete(entity);
			}
			// If any Service Queue exists, also delete that here.
			deleteServiceQueue(serviceId);
			result = " service " + serviceId + " was deleted ";
		}
		return result;
	}

	/**
	 * Store the new service information
	 */
	public String save(Service service) {
		logger.log(String.format("Saving resource in DB %s", service.getServiceId()), Severity.INFORMATIONAL,
				new AuditElement(SERVICE_CTR, "Created Service ", service.getServiceId()));
		serviceDao.save(new ServiceEntity(service));
		return service.getServiceId();
	}

	/**
	 * Updates an existing service record in the database
	 * 
	 * @param service
	 *            The service to update. Must exist.
	 */
	public String updateService(Service service) {
		ServiceEntity entity = serviceDao.getServiceById(service.getServiceId());
		if (entity != null) {
			entity.setService(service);
			serviceDao.save(entity);
		}
		return service.getServiceId();
	}

	/**
	 * List all Services. Could be large. Use the paginated version instead.
	 */
	public List<Service> list() {
		Iterable<ServiceEntity> results = serviceDao.getAllAvailableServices();
		List<Service> services = new ArrayList<>();
		for (ServiceEntity serviceEntity : results) {
			services.add(serviceEntity.getService());
		}
		return services;
	}

	/**
	 * Get a list of services with pagination
	 */
	public PiazzaResponse getServices(Integer page, Integer perPage, String order, String sortBy, String keyword, String userName) {
		// Execute the appropriate query based on the nullable, optional parameters
		Pagination pagination = new Pagination(null, page, perPage, sortBy, order);
		Page<ServiceEntity> results = null;
		if (StringUtils.isNotEmpty(userName) && StringUtils.isNotEmpty(keyword)) {
			// Both parameters specified
			results = serviceDao.getServiceListForUserAndKeyword(keyword, userName, pagination);
		} else if (StringUtils.isNotEmpty(userName)) {
			// Query by User
			results = serviceDao.getServiceListByUser(userName, pagination);
		} else if (StringUtils.isNotEmpty(keyword)) {
			// Query by Keyword
			results = serviceDao.getServiceListByKeyword(keyword, pagination);
		} else {
			// Query all Jobs
			results = serviceDao.getServiceList(pagination);
		}

		// Collect the Jobs
		List<Service> services = new ArrayList<>();
		for (ServiceEntity serviceEntity : results) {
			services.add(serviceEntity.getService());
		}
		// Set Pagination count
		pagination.setCount(results.getTotalElements());

		// Return the complete List
		return new ServiceListResponse(services, pagination);
	}

	/**
	 * Returns a ResourceMetadata object that matches the specified Id.
	 * 
	 * @param jobId
	 *            Job Id
	 * @return The Job with the specified Id
     * @throws ResourceAccessException
	 */
	public Service getServiceById(String serviceId) {
		ServiceEntity serviceEntity = serviceDao.getServiceById(serviceId);
		if (serviceEntity == null) {
			throw new ResourceAccessException(String.format("Service not found : %s", serviceId));
		} else {
			return serviceEntity.getService();
		}
	}

	/**
	 * Adds an Asynchronous Service Instance to the Database.
	 * 
	 * @param instance
	 *            The instance
	 */
	public void addAsyncServiceInstance(AsyncServiceInstance instance) {
		asyncServiceInstanceDao.save(new AsyncServiceInstanceEntity(instance));
	}

	/**
	 * Gets the Async Service Instance for the Piazza Job ID
	 * 
	 * @param jobId
	 *            The piazza Job ID
	 * @return The async service instance
	 */
	public AsyncServiceInstance getInstanceByJobId(String jobId) {
		AsyncServiceInstanceEntity entity = asyncServiceInstanceDao.getInstanceByJobId(jobId);
		if (entity == null) {
			return null;
		} else {
			return entity.getAsyncServiceInstance();
		}
	}

	/**
	 * Updates an Async Service instance. Uses the Job ID as the key to find the Instance.
	 */
	public void updateAsyncServiceInstance(AsyncServiceInstance instance) {
		AsyncServiceInstanceEntity entity = asyncServiceInstanceDao.getInstanceByJobId(instance.getJobId());
		if (entity != null) {
			entity.setAsyncServiceInstance(instance);
			asyncServiceInstanceDao.save(entity);
		}
	}

	/**
	 * Deletes an Async Service Instance by Job ID. This is done when the Service has been processed to completion and
	 * the instance is no longer needed.
	 * 
	 * @param id
	 *            The Job ID of the Async Service Instance.
	 */
	public void deleteAsyncServiceInstance(String jobId) {
		AsyncServiceInstanceEntity entity = asyncServiceInstanceDao.getInstanceByJobId(jobId);
		if (entity != null) {
			asyncServiceInstanceDao.delete(entity);
		}
	}

	/**
	 * @return Gets all Instances that require a status check.
	 */
	public List<AsyncServiceInstance> getStaleServiceInstances() {
		long thresholdEpoch = new DateTime().minusSeconds(STALE_INSTANCE_THRESHOLD_SECONDS).getMillis();
		Iterable<AsyncServiceInstanceEntity> results = asyncServiceInstanceDao.getStaleServiceInstances(thresholdEpoch);
		List<AsyncServiceInstance> instances = new ArrayList<>();
		for (AsyncServiceInstanceEntity result : results) {
			instances.add(result.getAsyncServiceInstance());
		}
		return instances;
	}

	/**
	 * Gets a list of all Piazza Services that are registered as a Task-Managed Service.
	 * 
	 * @return List of Task-Managed Services
	 */
	public List<Service> getTaskManagedServices() {
		Iterable<ServiceEntity> results = serviceDao.getAllTaskManagedServices();
		List<Service> services = new ArrayList<>();
		for (ServiceEntity result : results) {
			services.add(result.getService());
		}
		return services;
	}

	/**
	 * Gets the next Job in the queue for a particular service.
	 * <p>
	 * This method is synchronized because it is incredibly important that we never return the same job twice, avoiding
	 * potential race conditions.
	 * </p>
	 * 
	 * @param serviceId
	 *            The ID of the Service to fetch work for.
	 * @return The next ServiceJob in the Service Queue, if one exists; null if the Queue has no Jobs ready to be
	 *         processed.
	 */
	public synchronized ServiceJob getNextJobInServiceQueue(String serviceId) {
		ServiceJobEntity serviceJobEntity = serviceJobDao.getNextJobInServiceQueue(serviceId);
		if (serviceJobEntity == null) {
			// No jobs to be processed
			return null;
		} else {
			// A job is to be processed. Set the start time.
			serviceJobEntity.getServiceJob().setStartedOn(new DateTime());
			serviceJobDao.save(serviceJobEntity);
			return serviceJobEntity.getServiceJob();
		}
	}

	/**
	 * Gets the complete list of all Jobs in the Service's Service Queue that have exceeded the length of time for
	 * processing, and are thus considered timed out.
	 * 
	 * @param serviceId
	 *            The ID of the Service whose Queue to check
	 * @return The list of timed out Jobs
	 */
	public List<ServiceJob> getTimedOutServiceJobs(String serviceId) {
		Service service = getServiceById(serviceId);
		Long timeout = service.getTimeout();
		if (timeout == null) {
			// If no timeout is specified for the Service, then we can't check for timeouts.
			return new ArrayList<>();
		}
		// The timeout is in seconds. Get the current time and subtract the number of seconds to find the timestamp of a
		// timed out service.
		long timeoutEpoch = new DateTime().minusSeconds(timeout.intValue()).getMillis();
		Iterable<ServiceJobEntity> results = serviceJobDao.getTimedOutServiceJobs(serviceId, timeoutEpoch);
		List<ServiceJob> serviceJobs = new ArrayList<>();
		for (ServiceJobEntity entity : results) {
			serviceJobs.add(entity.getServiceJob());
		}
		return serviceJobs;
	}

	/**
	 * Gets the Service Job for the specified service with the specified Job ID
	 * 
	 * @param serviceId
	 *            The ID of the Service
	 * @param jobId
	 *            The ID of the Job
	 * @return
	 */
	public ServiceJob getServiceJob(String serviceId, String jobId) {
		ServiceJobEntity entity = serviceJobDao.getServiceJobByServiceAndJobId(serviceId, jobId);
		if (entity != null) {
			return entity.getServiceJob();
		} else {
			return null;
		}
	}

	/**
	 * Increments the timeout count for the Job ID
	 * 
	 * @param serviceId
	 *            The Service ID containing the Job
	 * @param serviceJob
	 *            The ServiceJob that has timed out
	 */
	public synchronized void incrementServiceJobTimeout(String serviceId, ServiceJob serviceJob) {
		ServiceJobEntity entity = serviceJobDao.getServiceJobByServiceAndJobId(serviceId, serviceJob.getJobId());
		if (entity != null) {
			// Increment the failure count
			entity.getServiceJob().setTimeouts(entity.getServiceJob().getTimeouts() + 1);
			// Delete the previous Started On date, so that it can be picked up again.
			entity.getServiceJob().setStartedOn(null);
			// Save
			serviceJobDao.save(entity);
		}
	}

	/**
	 * Adds a new Service Job reference to the specified Service's Job Queue.
	 * 
	 * @param serviceId
	 *            The ID of the Service
	 * @param serviceJob
	 *            The ServiceJob, describing the ID of the Job
	 */
	public void addJobToServiceQueue(String serviceId, ServiceJob serviceJob) {
		serviceJobDao.save(new ServiceJobEntity(serviceId, serviceJob));
	}

	/**
	 * Removes the specified Service Job (by ID) from the Service Queue for the specified Service
	 * 
	 * @param serviceId
	 *            The ID of the service whose Job to remove
	 * @param jobId
	 *            The ID of the Job to remove from the queue
	 */
	public void removeJobFromServiceQueue(String serviceId, String jobId) {
		ServiceJobEntity entity = serviceJobDao.getServiceJobByServiceAndJobId(serviceId, jobId);
		if (entity != null) {
			serviceJobDao.delete(entity);
		}
	}

	/**
	 * Returns a Job that matches the specified Id.
	 * 
	 * @param jobId
	 *            Job Id
	 * @return The Job with the specified Id
	 * @throws InterruptedException
     * @throws ResourceAccessException
	 */
	public Job getJobById(String jobId) {
		JobEntity entity = jobDao.getJobByJobId(jobId);
		if (entity != null) {
			return entity.getJob();
		} else {
			return null;
		}
	}

	/**
	 * Completely deletes a Service Queue for a registered service.
	 * 
	 * @param serviceId
	 *            The ID of the Service whose queue to drop.
	 */
	public void deleteServiceQueue(String serviceId) {
		serviceJobDao.deleteAllJobsByServiceId(serviceId);
	}

	/**
	 * Determines if the User is able to access the Service Queue for the specified service or not.
	 * 
	 * @param serviceId
	 *            The ID of the Service
	 * @param username
	 *            The user name
	 * @return True if able to access, false if not.
	 */
	public boolean canUserAccessServiceQueue(String serviceId, String username) throws InvalidInputException {
		try {
			Service service = getServiceById(serviceId);
			if (service.getTaskAdministrators() != null) {
				return service.getTaskAdministrators().contains(username);
			} else {
				return false;
			}
		} catch (ResourceAccessException exception) {
			LOG.info(String.format("User %s attempted to check Service Queue for non-existent service with ID %s", username, serviceId),
					exception);
			throw new InvalidInputException(String.format("Service not found : %s", serviceId));
		}
	}

	/**
	 * Gets Metadata on the specified Service Queue
	 * 
	 * @param serviceId
	 *            The ID of the service
	 * @return Map containing metadata information
	 */
	public Map<String, Object> getServiceQueueCollectionMetadata(String serviceId) {
		Map<String, Object> map = new HashMap<>();
		// Get the Length
		map.put("totalJobCount", serviceJobDao.getServiceJobCountForService(serviceId));
		return map;
	}
}