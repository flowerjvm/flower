package io.github.flowerjvm.flower.check.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a recurring scheduler as explicitly approved by the user or project owner.
 *
 * <p>This is a source-retained development-policy marker for {@code flower-check}.
 * It does not affect Flower runtime behavior and is not part of {@code flower-core}.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FlowerSchedulerApproved {

    /**
     * Why this recurring scheduler is intentionally used instead of a Flower wait,
     * signal, event, or timeout pattern.
     */
    String reason();

    /**
     * Person, role, or process that approved this scheduler.
     */
    String approvedBy() default "";

    /**
     * Approval date or change/reference id, using the host project's convention.
     */
    String approvedAt() default "";

    /**
     * Optional issue, ticket, pull request, or change-management reference.
     */
    String reference() default "";
}
