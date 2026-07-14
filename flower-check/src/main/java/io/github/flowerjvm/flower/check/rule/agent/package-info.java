/**
 * Tier 2 rules — Agent runtime (FLOWER-CHECK-006..008).
 *
 * <p>These target the agent/harness layer (the future {@code flower-agent-runtime}
 * and the patterns proven in archdox). They are <strong>opt-in</strong>
 * ({@code FlowerCheckConfig.agentRulesEnabled}) because they need
 * project-specific type/annotation names for the action registry, policy gate,
 * audit emitter, and approval marker. See {@code docs/02-rule-catalog.md} Tier 2.
 *
 * <p>No rules are implemented here yet; this package marks where they go.
 */
package io.github.flowerjvm.flower.check.rule.agent;
