package io.github.parkkevinsb.flower.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks recurring scheduler usage as explicitly reviewed and approved.
 *
 * <p>Use this only on framework-owned or user-approved periodic work. The
 * required value records why the scheduler is allowed to exist.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FlowerSchedulerApproved {

    String value();
}
