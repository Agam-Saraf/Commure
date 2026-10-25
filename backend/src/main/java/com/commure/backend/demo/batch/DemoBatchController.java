package com.commure.backend.demo.batch;

import java.util.Map;
import java.util.UUID;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/batch")
@Profile("batch")
public class DemoBatchController {

	private final JobLauncher jobLauncher;
	private final Job demoBatchJob;
	private final DemoBatchItemWriter writer;

	public DemoBatchController(JobLauncher jobLauncher, Job demoBatchJob, DemoBatchItemWriter writer) {
		this.jobLauncher = jobLauncher;
		this.demoBatchJob = demoBatchJob;
		this.writer = writer;
	}

	@PostMapping("/run")
	public Map<String, Object> run() throws Exception {
		writer.clear();
		var execution = jobLauncher.run(demoBatchJob, new JobParametersBuilder()
				.addString("runId", UUID.randomUUID().toString())
				.toJobParameters());
		return Map.of(
				"status", execution.getStatus().toString(),
				"written", writer.written());
	}

	@GetMapping
	public Map<String, Object> status() {
		return Map.of("job", DemoBatchConfig.JOB_NAME, "lastWritten", writer.written());
	}
}
