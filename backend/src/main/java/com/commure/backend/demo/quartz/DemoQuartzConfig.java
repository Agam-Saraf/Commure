package com.commure.backend.demo.quartz;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("quartz")
public class DemoQuartzConfig {

	@Bean
	JobDetail demoQuartzJobDetail() {
		return JobBuilder.newJob(DemoQuartzJob.class)
				.withIdentity("demoQuartzJob")
				.storeDurably()
				.build();
	}

	@Bean
	Trigger demoQuartzTrigger(JobDetail demoQuartzJobDetail) {
		return TriggerBuilder.newTrigger()
				.forJob(demoQuartzJobDetail)
				.withIdentity("demoQuartzTrigger")
				.withSchedule(SimpleScheduleBuilder.simpleSchedule()
						.withIntervalInSeconds(1)
						.repeatForever())
				.startNow()
				.build();
	}
}
