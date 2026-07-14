/**
 * Tier 1 rules — Step / Flow usage (FLOWER-CHECK-001..005, 009..015).
 *
 * <p>These enforce the core Flower execution model and are enabled by default.
 * Each rule is specified in {@code docs/02-rule-catalog.md}; implement them by
 * extending {@link io.github.flowerjvm.flower.check.rule.AbstractRule} and
 * registering in the {@code META-INF/services} file.
 *
 * <p>{@link io.github.flowerjvm.flower.check.rule.core.BlockingCallRule} is the
 * reference implementation to copy.
 */
package io.github.flowerjvm.flower.check.rule.core;
