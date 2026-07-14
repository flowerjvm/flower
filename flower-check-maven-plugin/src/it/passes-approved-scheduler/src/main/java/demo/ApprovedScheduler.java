package demo;

import io.github.flowerjvm.flower.check.annotation.FlowerSchedulerApproved;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ApprovedScheduler {

    @FlowerSchedulerApproved(reason = "User approved this compatibility poller while the partner callback is unavailable")
    public void start(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
            }
        }, 0L, 1L, TimeUnit.SECONDS);
    }
}
