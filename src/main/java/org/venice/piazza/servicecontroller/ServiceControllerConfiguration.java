package org.venice.piazza.servicecontroller;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import messaging.job.JobMessageFactory;

@Configuration
public class ServiceControllerConfiguration {
	@Value("${SPACE}")
	private String SPACE; //NOSONAR
	
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

	@Bean
	public LocalValidatorFactoryBean getLocalValidatorFactoryBean() {
		return new LocalValidatorFactoryBean();
	}
}
