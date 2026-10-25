package com.commure.backend.demo.batch;

import java.util.List;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("batch")
public class DemoBatchConfig {

	public static final String JOB_NAME = "demoBatchJob";

	@Bean
	Job demoBatchJob(JobRepository jobRepository, Step demoBatchStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.start(demoBatchStep)
				.build();
	}

	@Bean
	Step demoBatchStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			DemoBatchItemProcessor processor, DemoBatchItemWriter writer) {
		return new StepBuilder("demoBatchStep", jobRepository)
				.<String, String>chunk(2, transactionManager)
				.reader(new ListItemReader<>(List.of("alpha", "beta", "gamma")))
				.processor(processor)
				.writer(writer)
				.build();
	}
}
