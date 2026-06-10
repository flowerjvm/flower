# flower-check-annotations

Source-retained annotations used by `flower-check`.

This module is intentionally separate from `flower-core`. It contains no runtime
Flower behavior and should be used by host applications only when they want an
official marker for development policy checks.

```xml
<dependency>
    <groupId>io.github.parkkevinsb.flower</groupId>
    <artifactId>flower-check-annotations</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

Example:

```java
import io.github.parkkevinsb.flower.check.annotation.FlowerSchedulerApproved;
import org.springframework.scheduling.annotation.Scheduled;

class ReconciliationJob {

    @FlowerSchedulerApproved(
        reason = "User approved periodic reconciliation outside a Flower flow",
        approvedBy = "ops-owner",
        approvedAt = "2026-06-11",
        reference = "OPS-1234"
    )
    @Scheduled(fixedDelay = 60000)
    void reconcile() {
    }
}
```

The annotation has `RetentionPolicy.SOURCE`, so it is for source checks only and
does not affect runtime behavior.
