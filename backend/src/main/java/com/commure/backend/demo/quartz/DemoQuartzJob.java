package com.commure.backend.demo.quartz;

import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Instantiated by Quartz; dependencies are injected by Spring's JobFactory.
 */
public class DemoQuartzJob extends QuartzJobBean {

	private DemoQuartzCounter counter;

	@Autowired
	public void setCounter(DemoQuartzCounter counter) {
		this.counter = counter;
	}

	@Override
	protected void executeInternal(JobExecutionContext context) {
		counter.increment();
	}
}
