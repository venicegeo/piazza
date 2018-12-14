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
package org.venice.piazza.jobmanager.database;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.venice.piazza.common.hibernate.dao.job.JobDao;
import org.venice.piazza.common.hibernate.entity.JobEntity;

import model.job.Job;
import model.job.JobProgress;
import model.response.JobListResponse;
import model.response.Pagination;
import model.status.StatusUpdate;

/**
 * Helper class to interact with and access the Database
 * 
 * @author Patrick.Doody
 * 
 */
@Component
public class JobManagerDatabaseAccessor {
	@Autowired
	private JobDao jobDao;

	public JobManagerDatabaseAccessor() {
		// Expected for Component instantiation
	}

	/**
	 * Gets the total number of Jobs in the database
	 * 
	 * @return Number of jobs in the DB
	 */
	public long getJobsCount() {
		return jobDao.count();
	}

	/**
	 * Gets the number of Jobs for the specific status
	 * 
	 * @param status
	 *            The Status
	 * @return The number of Jobs for that status
	 */
	public Long getJobStatusCount(String status) {
		return jobDao.countJobByStatus(status);
	}

	/**
	 * Returns a Job that matches the specified Id.
	 * 
	 * @param jobId
	 *            Job Id
	 * @return The Job with the specified Id
	 * @throws org.springframework.web.client.ResourceAccessException
	 */
	public Job getJobById(String jobId) {
		JobEntity jobEntity = jobDao.getJobByJobId(jobId);
		return jobEntity != null ? jobEntity.getJob() : null;
	}

	/**
	 * Gets a list of Jobs from the database.
	 * 
	 * @param page
	 *            the page number
	 * @param perPage
	 *            the number of results per page
	 * @param order
	 *            "ascending" or "descending"
	 * @param status
	 *            The status of the Job
	 * @param userName
	 *            The username who submitted the Job
	 * @return The list of jobs
	 */
	public JobListResponse getJobs(int page, int perPage, String order, String sortBy, String status, String userName) {
		// Execute the appropriate query based on the nullable, optional parameters
		Pagination pagination = new Pagination(null, page, perPage, sortBy, order);
		Page<JobEntity> results;

		if (StringUtils.isNotEmpty(userName) && StringUtils.isNotEmpty(status)) {
			// Both parameters specified
			results = jobDao.getJobListForUserAndStatus(status, userName, pagination);
		} else if (StringUtils.isNotEmpty(userName)) {
			// Query by User
			results = jobDao.getJobListByUser(userName, pagination);
		} else if (StringUtils.isNotEmpty(status)) {
			// Query by Status
			results = jobDao.getJobListByStatus(status, pagination);
		} else {
			// Query all Jobs
			results = jobDao.getJobList(pagination);
		}

		// Collect the Jobs
		List<Job> jobs = new ArrayList<>();
		for (JobEntity jobEntity : results) {
			jobs.add(jobEntity.getJob());
		}
		// Set Pagination count
		pagination.setCount(results.getTotalElements());

		// Return the complete List
		return new JobListResponse(jobs, pagination);
	}

	/**
	 * Updates the status of a Job.
	 * 
	 * @param jobId
	 *            The Job Id
	 * @param status
	 *            The Status String of the Job
	 */
	public void updateJobStatus(String jobId, String status) {
		JobEntity jobEntity = jobDao.getJobByJobId(jobId);
		if (jobEntity != null) {
			Job job = jobEntity.getJob();
			job.setStatus(status);
			jobDao.save(jobEntity);
		}
	}

	/**
	 * Updates the Progress of a Job
	 * 
	 * @param jobId
	 *            The Job Id to update
	 * @param progress
	 *            The progres to set
	 */
	public void updateJobProgress(String jobId, JobProgress progress) {
		JobEntity jobEntity = jobDao.getJobByJobId(jobId);
		if (jobEntity != null) {
			Job job = jobEntity.getJob();
			job.setProgress(progress);
			jobDao.save(jobEntity);
		}
	}

	/**
	 * Updates the Status of a Job. This will update the result, progress, and status of the Job. This method will
	 * update in a single write to the database.
	 * 
	 * @param jobId
	 *            The Id of the Job whose status to update
	 * @param statusUpdate
	 *            The Status Update information
	 */
	public void updateJobStatus(String jobId, StatusUpdate statusUpdate) {
		JobEntity jobEntity = jobDao.getJobByJobId(jobId);
		if (jobEntity != null) {
			Job job = jobEntity.getJob();

			// Only update the information that is not null
			if (!statusUpdate.getStatus().isEmpty()) {
				// Only update the status if the status of that job is in a non-finished state. If it is in a finished
				// state, then this is potentially an out-of-order message and should be disregarded.
				if (job.getStatus() == null) {
					job.setStatus(statusUpdate.getStatus());
				} else {
					switch (job.getStatus()) {
					case StatusUpdate.STATUS_CANCELLED:
					case StatusUpdate.STATUS_ERROR:
					case StatusUpdate.STATUS_FAIL:
					case StatusUpdate.STATUS_SUCCESS:
						// Do not update the status if it is in a finalized state.
						break;
					default:
						job.setStatus(statusUpdate.getStatus());
						break;
					}
				}
			}
			if (statusUpdate.getProgress() != null) {
				job.setProgress(statusUpdate.getProgress());
			}
			if (statusUpdate.getResult() != null) {
				job.setResult(statusUpdate.getResult());
			}

			// Commit
			jobDao.save(jobEntity);
		}
	}

	/**
	 * Deletes a Job entry.
	 * 
	 * @param jobId
	 *            The Id of the job to delete
	 */
	public void removeJob(String jobId) {
		JobEntity entity = jobDao.getJobByJobId(jobId);
		if (entity != null) {
			jobDao.delete(entity);
		}
	}

	/**
	 * Adds a Job
	 * 
	 * @param job
	 *            The Job
	 */
	public void addJob(Job job) {
		JobEntity jobEntity = new JobEntity(job);
		jobDao.save(jobEntity);
	}

}