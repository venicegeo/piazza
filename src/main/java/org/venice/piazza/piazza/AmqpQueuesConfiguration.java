package org.venice.piazza.piazza;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import messaging.job.JobMessageFactory;

@Configuration
public class AmqpQueuesConfiguration {
	@Value("${SPACE}")
	private String SPACE;
	
	@Bean(name = "UpdateJobsQueue")
	public Queue updateJobsQueue() {
		return new Queue(String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.UPDATE_JOB_TOPIC_NAME, SPACE), true, false,
				false);
	}

	@Bean(name = "RequestJobQueue")
	public Queue requestJobQueue() {
		return new Queue(String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.REQUEST_JOB_TOPIC_NAME, SPACE), true, false,
				false);
	}

	@Bean(name = "AbortJobsQueue")
	public Queue abortJobsQueue() {
		return new Queue(String.format(JobMessageFactory.TOPIC_TEMPLATE, JobMessageFactory.ABORT_JOB_TOPIC_NAME, SPACE), true, false,
				false);
	}

}
