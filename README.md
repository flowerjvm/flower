# Flower Design Brief

이 폴더는 기존 `flower_*.md`, `AGENTS.md`, `CLAUDE.md`, 그리고 `old/tsb.li.common.eventflow` 코드를 읽고 다시 정리한 Flower 초기 설계 문서입니다.

Claude 또는 다른 설계 에이전트가 다음 단계 구현 설계를 시작할 때는 이 폴더의 문서를 우선 기준으로 삼습니다.

## 읽는 순서

1. [00-review-summary.md](00-review-summary.md)
2. [01-scope-and-principles.md](01-scope-and-principles.md)
3. [02-core-architecture.md](02-core-architecture.md)
4. [03-step-stepno-and-flow-control.md](03-step-stepno-and-flow-control.md)
5. [04-events-bloom-and-threading.md](04-events-bloom-and-threading.md)
6. [05-initial-implementation-plan.md](05-initial-implementation-plan.md)
7. [06-user-programming-model.md](06-user-programming-model.md)

## 최종 용어

기존 Claude 문서의 `Job` 용어는 Flower에서 사용하지 않습니다.

Flower의 계층은 아래로 고정합니다.

```text
Engine
  -> Worker
      -> Flow
          -> Step
              -> StepNo / StepId
```

`Bloom`은 이벤트 시스템입니다. `Flower`는 오케스트레이션 프레임워크입니다. Flower core는 Bloom에 직접 의존하지 않고, Bloom 연동은 adapter로 둡니다.

## 후속 구현 순서

`flower-core` 이후에는 아래 순서로 선택 모듈을 구현합니다.

```text
1. flower-bloom-adapter
2. flower-spring-boot-starter
3. flower-observability
```

`flower-bloom-adapter`는 core의 `EventBus` SPI를 Bloom으로 연결합니다. `flower-spring-boot-starter`는 Spring Boot 환경에서 Engine/Worker lifecycle과 설정을 자연스럽게 붙이는 모듈입니다. `flower-observability`는 core의 dump/listener 위에 Micrometer/OpenTelemetry 같은 metrics/tracing 연동을 얹는 선택 모듈입니다.

## 가장 중요한 결정

- Flower는 BPMN, Temporal, Camunda, YAML workflow, Human Task, Saga 엔진이 아닙니다.
- Flower는 한 프로세스 안에서 여러 도메인 흐름을 tick + event 기반으로 안전하게 진행시키는 경량 오케스트레이션 toolkit입니다.
- Java 8+를 우선합니다. 따라서 Java 17 `sealed interface` 기반 설계는 v1 기준에서 제외하고, `enum + final class` 형태의 Java 8 호환 `StepResult`로 설계합니다.
- `StepNo` 또는 내부 `StepId`는 지원해야 합니다. old eventFlow의 `SeqBase.seqNo` 패턴은 Flower의 중요한 사용성 자산입니다.
- 기존 eventFlow의 state machine 코드는 참고 대상이지만 Flower의 본질은 FSM 라이브러리가 아니라 Worker-Flow-Step 오케스트레이션입니다.
- v1 공식 사용 방식은 사용자가 `Step`을 상속하고, `FlowFactory`에서 Step 의존성을 명시적으로 조립하는 방식입니다.
- 의존성이 많은 Step은 `Deps` 객체와 domain service 위임으로 해결합니다. Flower core는 DI container를 제공하지 않습니다.
- adapter는 core를 순수하게 유지하기 위한 선택 모듈입니다. Bloom/Spring Boot/observability 연동은 core 밖에서 연결합니다.
