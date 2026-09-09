# 🌸 Flower

[English](README.md) | **한국어**

**하나의 실행 모델. 일반 Java 업무 흐름부터 AI 에이전트까지.**

**Flow → Step → StepResult**로 애플리케이션의 실행 흐름을 명시적으로 표현하세요.

Flower는 Java 애플리케이션 안에서 동작하는 작은 런타임입니다. 여러 단계로
진행되는 업무, 이벤트를 기다리는 작업, AI 애플리케이션의 흐름을 같은 실행
구조로 표현합니다. 기존 도메인 모델과 애플리케이션 프레임워크는 그대로 유지합니다.

같은 모델이 사람에게는 읽을 수 있는 흐름을, 코딩 에이전트에게는 생성 구조의
제약을 제공합니다. Flower Skill, `flower-check`, 결정적 테스트가 이를 뒷받침합니다.

[![CI](https://github.com/flowerjvm/flower/actions/workflows/ci.yml/badge.svg)](https://github.com/flowerjvm/flower/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.flowerjvm/flower-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.flowerjvm/flower-core/0.1.3)

[빠른 시작](#quick-start) · [실행 모델](#the-execution-contract) ·
[코딩 에이전트](#one-execution-model-for-humans-and-coding-agents) ·
[런타임 상세 문서](docs/runtime-reference.md) · [모듈](#modules)

```java
Flow flow = Flow.builder("order", orderId)
        .step("accept", new AcceptOrderStep(orderService))
        .step("payment", new WaitForPaymentStep())
        .step("fulfill", new FulfillOrderStep(warehouseService))
        .build();

worker.submit(flow);
```

이 Step 클래스들은 애플리케이션이 정의합니다. Flower는 실행 모델을 제공합니다.
필요한 애플리케이션 클래스까지 포함한 예제는 [빠른 시작](#quick-start)에 있습니다.

처음에는 `flower-core`만 사용하면 됩니다. **Java 8 이상, 하나의 JVM,
별도 Flower 서버 없이 시작할 수 있으며, 비영속 Flow에는 데이터베이스도
필요하지 않습니다.** Spring Boot 연동, 체크포인트, 관측 기능, 개발 도구는
선택 사항입니다. Spring Boot starter는 Java 17과 Spring Boot 3.x가 필요합니다.

예제 버전은 `0.1.3`이며, `main`은 `0.1.4-SNAPSHOT`을 개발 중입니다.

`flower-core`는 `Worker → Flow → Step → StepResult`라는 확립된 실행 계약을
중심으로 합니다. Core는 이 모델과 기존 애플리케이션 코드와의 호환성을 유지하는
방향으로 발전합니다. 주변의 선택 모듈과 생태계 프로젝트는 이 기반 위에서
발전하며, MVP 표시는 API를 다듬고 있는 개별 구성요소에 붙습니다.

<a id="the-flow-is-already-there"></a>

## 흐름은 이미 존재합니다

애플리케이션에는 이미 흐름이 있습니다. 아직 잘 보이지 않을 뿐입니다.

주문은 결제를 기다립니다. 장비는 응답을 기다립니다. 게임은 플레이어의 입력을
기다립니다. AI 애플리케이션은 모델, 도구 또는 사람의 승인을 기다립니다.

흐름은 존재하지만 실행을 제어하는 코드가 서비스 메서드, 스케줄 작업,
이벤트 리스너, 콜백, 상태 필드, 공유 플래그에 흩어져 있을 수 있습니다.
작업이 더 이상 진행되지 않으면 누군가는 이 조각들을 따라가며 전체 순서를
다시 알아내야 합니다.

> 지금 어떤 작업이 실행 중인가? 어느 단계인가? 무엇을 기다리는가?
> 어떤 조건에서 다음으로 넘어가는가?

Flower는 이 순서를 하나의 Flow, 작은 Step, 명시적인 전이 결과로 코드에
표현합니다. 도메인 규칙은 애플리케이션이 담당하고, Step은 언제 작업을
진행할 수 있는지 조율합니다.

<a id="different-workflows-one-model"></a>

## 다른 업무, 같은 실행 모델

| 애플리케이션 | Step으로 표현하는 단계 예시 |
| --- | --- |
| 일반 업무 서비스 | 주문 접수 → 결제 대기 → 주문 이행 |
| 물류·장비 작업 조율 | 작업 검증 → 요청 전송 → 완료 대기 → 마무리 |
| 게임 서버 | 턴 준비 → 플레이어 입력 대기 → 애니메이션 완료 대기 → 턴 종료 |
| AI 애플리케이션 | 컨텍스트 준비 → 모델·에이전트 실행 → 결과 검증 → 승인 요청 → 액션 실행 |

AI 애플리케이션에서도 builder 사용 방식은 달라지지 않습니다.

```java
Flow aiFlow = Flow.builder("assistant-task", taskId)
        .step("context", prepareContextStep)
        .step("model", waitForModelResultStep)
        .step("validate", validateOutputStep)
        .step("action", executeGovernedActionStep)
        .build();

worker.submit(aiFlow);
```

이 코드는 애플리케이션 구성 예시입니다. Step 변수들은 애플리케이션이 만든
인스턴스이며, Core에 내장된 AI 컴포넌트가 아닙니다.
각 흐름은 같은 세 개념을 사용합니다.

| 개념 | 책임 |
| --- | --- |
| `Flow` | 하나의 작업 인스턴스가 거치는 실행 단계를 선언하고 현재 위치를 유지합니다. |
| `Step` | 현재 단계를 조율하는 작고 상태를 가진 실행 단위입니다. |
| `StepResult` | 계속 대기할지, 다음으로 진행할지, 반복·이동·종료·실패할지를 명시합니다. |

런타임은 현재 Step을 관리하고 그 결과를 해석합니다. 애플리케이션은 각 단계에서
수행하거나 조율할 업무를 구현합니다. 모델 턴, 대화 이력, 도구, 검증,
액션 권한은 애플리케이션 서비스나 선택적인 상위 런타임이 담당합니다.

업무 내용이 달라져도 실행을 표현하는 방법까지 바꿀 필요는 없습니다.

<a id="one-execution-model-for-humans-and-coding-agents"></a>

## 사람과 코딩 에이전트가 같은 실행 모델을 사용합니다

Flower를 사용하는 코딩 에이전트는 빈 Java 프로젝트에서 실행 구조부터 임의로
정하는 대신, `Flow → Step → StepResult`라는 정해진 실행 모델 안에서 코드를
작성합니다. 단계를 선언하고, 각 Step을 작게 유지하고, 전이를 명시적인 결과로
반환합니다. 업무 규칙은 일반 Java 서비스와 도메인 객체에 그대로 둡니다.

사람에게는 읽을 수 있는 Flow를, 코딩 에이전트에게는 생성 구조의 제약을 제공합니다.
`flower-check`와 결정적 테스트는 생성된 코드를 검증하고 수정할 근거를 제공합니다.

```text
GUIDE       Flower Skill
              ↓
CONSTRAIN   Flow / Step / StepResult
              ↓
VERIFY      flower-check + 결정적 테스트
```

[Flower Skill](https://github.com/flowerjvm/flower-agent-skills/blob/main/agent-skills/flower-app-guide/SKILL.md)은
Flow 구성, Worker를 차단하지 않는 Step 작성, 대기 표현, 애플리케이션 서비스에
업무 책임을 두는 방법을 안내합니다. 실행 모델은 생성된 코드에 사람과 도구가
함께 살펴볼 수 있는 공통 구조를 부여합니다.

호스트 빌드에 연결한 [flower-check](flower-check/README.md)는 지원하는 사용 규칙을
검사하고, 설정한 심각도 이상의 위반이 있으면 빌드를 실패시킵니다.
결정적 테스트는 실행 순서와 시간을 제어해 예상한 전이·대기·실패·복구 동작을
검증합니다. 에이전트는 검사 결과를 바탕으로 코드를 수정하고,
검토자는 같은 실행 구조를 따라 결과를 확인합니다.

이 개발 방식은 일반 Java 애플리케이션과 AI 애플리케이션 모두에 적용할 수 있습니다.
애플리케이션 자체에 LLM 의존성을 추가할 필요는 없습니다.
[Skill과 빌드 검사 구성](#use-flower-with-chatgpt-and-codex)을 참고하세요.

<a id="before-and-after-make-the-sequence-visible"></a>

## 적용 전후: 실행 순서를 보이게 만들기

결제 흐름은 처음에 여러 곳에 나뉘어 구현될 수 있습니다.

```text
주기적 Poller    → 처리 중인 주문을 조회하고 status로 분기
이벤트 Listener  → 공유된 paid 플래그 설정
Deadline 필드    → 대기 시간 초과 판단
Service 메서드   → 주문 준비와 이행
```

각각은 익숙한 코드입니다. 다만 하나의 업무 흐름을 이해하려면 이 모든 곳을
따라가야 합니다. Flower에서는 앞서 본 builder로 실행 단계를 한곳에서 선언하고,
대기 Step에서 필요한 이벤트를 구독하고 완료 조건을 확인한 뒤 결과를 반환합니다.

```java
final class WaitForPaymentStep extends Step {
    @Override
    protected void onEnter(StepContext ctx) {
        ctx.startTimeout(30_000);
        ctx.subscribe(PaymentApproved.class, event -> {
            if (event.orderId().equals(ctx.flowId().flowKey())) {
                ctx.signal("paid");
            }
        });
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (ctx.hasSignal("paid")) {
            return StepResult.done();
        }
        if (ctx.timedOut()) {
            return StepResult.fail(
                    new IllegalStateException("payment timeout"));
        }
        return StepResult.stay();
    }
}
```

대기가 코드에 드러납니다. 다음 전이는 반환값으로 표현됩니다.
`StepContext`를 통해 만든 구독은 Step 이탈, 초기화 또는 Flow 종료 시
Flower가 정리합니다.

이 예제는 **메모리 안에서만 유지되는 비영속 대기**입니다. 구독 전에 발행된
이벤트는 이 Step을 위해 보관되지 않습니다. signal은 영속적인 업무 사실이 아니며,
`startTimeout(...)`도 재시작 후 복구되는 deadline이 아닙니다.
복구가 필요하면 복구 가능한 도메인 상태와 명시적인 체크포인트 전략을
함께 사용해야 합니다. [실행 범위와 보장 경계](#execution-boundaries)를 참고하세요.

<a id="more-than-splitting-a-method-into-smaller-methods"></a>

## 메서드를 작게 나누는 것에서 한 단계 더

작은 메서드는 코드를 정리합니다. Flower는 여기에 더해 시간이 지나며
진행되는 작업을 위한 공통 실행 계약을 제공합니다.

| 필요한 것 | Flower가 제공하는 것 |
| --- | --- |
| 실행 구조 파악 | 선언된 Flow 단계, 안정적인 Step ID, 확인 가능한 현재 Step. |
| 일관된 전이 표현 | 애플리케이션마다 다른 플래그·반환 코드 대신 `StepResult`. |
| 외부 작업 대기 | Step 소유의 구독, signal, timeout 도구. |
| 실제 스케줄러 없이 테스트 | `engine.attach()`, `worker.tickOnce()`, 제어 가능한 시계. |
| 실행 중인 애플리케이션 점검 | `Engine.dump()`, 생명주기 리스너, 선택적인 추적·콘솔 기능. |
| 선택한 작업의 재시작 복구 | Step별 복구 정책을 명시하는 선택적 checkpoint/resume. |

이런 기능을 요구사항마다 직접 추가하다 보면 애플리케이션 안에 런타임 자체를
만들게 될 수 있습니다. Flower는 그 공통 실행 기능을 제공합니다.
업무 규칙은 서비스와 도메인 객체에 두고, Step은 실행 조율에 집중시키세요.

<a id="quick-start"></a>

## 빠른 시작

### 1. Core 추가

일반 Java 애플리케이션에서는 `flower-core`만 추가합니다.

Maven:

```xml
<dependency>
    <groupId>io.github.flowerjvm</groupId>
    <artifactId>flower-core</artifactId>
    <version>0.1.3</version>
</dependency>
```

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.flowerjvm:flower-core:0.1.3")
}
```

별도 Maven 저장소나 `mavenLocal()` 설정은 필요하지 않습니다.

### 2. Flow를 선언하고, 이벤트를 기다리고, 다음 단계로 진행

Core를 의존성에 추가한 프로젝트에서 아래 `FlowerQuickStart` 클래스를 실행하세요.
사용하는 애플리케이션 클래스가 모두 들어 있습니다. Worker를 직접 tick하므로
Spring, 데이터베이스, 백그라운드 스케줄러, `Thread.sleep`이 필요하지 않습니다.

`PrintStep`은 예제를 위한 출력입니다. 이벤트는 대기 Step의 구독이 등록된
뒤에 의도적으로 발행합니다.

```java
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.SystemClock;
import io.github.flowerjvm.flower.core.worker.Worker;

public final class FlowerQuickStart {
    public static void main(String[] args) throws Exception {
        Worker worker = Worker.builder("orders").build();
        Engine engine = Engine.builder()
                .clock(SystemClock.INSTANCE)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();

        Flow flow = Flow.builder("order", "ORD-1")
                .step("accept", new PrintStep("accepted"))
                .step("payment", new WaitForPaymentStep())
                .step("fulfill", new PrintStep("fulfilled"))
                .build();

        engine.attach();
        try {
            worker.submit(flow);
            worker.tickOnce(); // Complete accept; payment is next.
            worker.tickOnce(); // Enter payment, subscribe, and stay.
            System.out.println("waiting at " + flow.currentStepId());

            engine.eventBus().publish(new PaymentApproved("ORD-1"));

            worker.tickOnce(); // Complete payment; fulfill is next.
            worker.tickOnce(); // Complete fulfill and finish the Flow.
            System.out.println("flow " + flow.state());
        } finally {
            engine.stop();
        }
    }

    static final class PrintStep extends Step {
        private final String message;

        PrintStep(String message) {
            this.message = message;
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            System.out.println(message + " " + ctx.flowId().flowKey());
            return StepResult.done();
        }
    }

    static final class WaitForPaymentStep extends Step {
        @Override
        protected void onEnter(StepContext ctx) {
            ctx.startTimeout(30_000);
            ctx.subscribe(PaymentApproved.class, event -> {
                if (event.orderId().equals(ctx.flowId().flowKey())) {
                    ctx.signal("paid");
                }
            });
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            if (ctx.hasSignal("paid")) {
                return StepResult.done();
            }
            if (ctx.timedOut()) {
                return StepResult.fail(
                        new IllegalStateException("payment timeout"));
            }
            return StepResult.stay();
        }
    }

    static final class PaymentApproved {
        private final String orderId;

        PaymentApproved(String orderId) {
            this.orderId = orderId;
        }

        String orderId() {
            return orderId;
        }
    }
}
```

실행 결과:

```text
accepted ORD-1
waiting at payment
fulfilled ORD-1
flow FINISHED
```

Worker는 한 번의 tick에서 각 활성 Flow의 `onTick`을 최대 한 번 호출합니다.
`accept`가 끝나면 현재 위치가 `payment`로 바뀌고, 다음 tick에서
`payment.onEnter`가 실행됩니다. 마지막 Step이 `done()`을 반환하면
Flow가 종료되고 Worker의 활성 목록에서 제거됩니다.

자동 실행하려면 `engine.start()`를 사용하고 애플리케이션 생명주기에 맞춰
Engine을 종료하세요. 이때는 스케줄러가 Worker를 실행하므로 `tickOnce()`를
호출하지 않습니다. Spring Boot에서는 starter가 이 생명주기를 관리할 수 있습니다.
경과 시간까지 제어하며 테스트하려면 `ManualClock`을 사용하세요.
[테스트](#testing)를 참고하세요.

<a id="the-execution-contract"></a>

## 실행 계약

애플리케이션 코드는 `Flow → Step → StepResult`로 구성하고,
런타임은 이를 다음 구조로 실행합니다.

```text
Engine
  └─ Worker
      └─ Flow
          └─ Step
              └─ StepResult
```

`Engine`은 clock, event bus, Worker, listener 등의 런타임 서비스를 소유합니다.
자동 실행 모드에서는 Core Worker마다 하나의 스케줄러 스레드가 활성 Flow를
tick합니다. `FlowId(flowType, flowKey)`는 Flow의 식별자이며,
같은 Engine 안에서는 서로 다른 Worker 사이에서도 중복될 수 없습니다.

### Step 생명주기

| 콜백 | 목적 |
| --- | --- |
| `onEnter(ctx)` | Step 진입 시 호출합니다. 작업을 시작하거나 완료 이벤트를 구독합니다. |
| `onTick(ctx)` | 짧고 반복 가능한 판단을 수행하고 `StepResult`를 반환합니다. |
| `onExit(ctx)` | 취소를 포함해 현재 Step에서 이탈할 때 자원을 정리합니다. |
| `onReset(ctx)` | `repeat()` 시 초기화하고, 이후 Step에 다시 진입합니다. |

Step에 재진입하면 진입 과정도 다시 실행됩니다. 영속 Flow의 복구는 Step에
선언한 복구 정책을 따릅니다.
[Checkpoint / Resume](docs/runtime-reference.md#checkpoint--resume)를 참고하세요.

### 명시적인 결과

| 결과 | 의미 |
| --- | --- |
| `stay()` | 현재 Step을 유지하고 이후 tick에서 다시 판단합니다. |
| `done()` | 다음에 선언된 Step으로 진행합니다. 마지막 Step이면 Flow를 종료합니다. |
| `repeat()` | 현재 Step을 초기화하고 처음부터 다시 실행합니다. |
| `goTo("stepId")` | 안정적인 ID로 지정한 Step으로 이동합니다. |
| `finish()` | 나머지 Step을 실행하지 않고 성공 종료합니다. |
| `fail(cause)` | Flow를 실패로 종료합니다. |

위 메서드는 `StepResult`의 정적 생성 메서드입니다. `repeat()`는 초기화와
재진입을 뜻하며, 그 자체로 업무적으로 안전한 재시도나 backoff 정책을
제공하지는 않습니다.

Step은 작고 비차단 방식으로 유지하세요. 외부 작업은 애플리케이션 서비스가
소유하고, 완료 여부는 signal, event, 도메인 상태로 확인하세요.
`onEnter`나 `onTick` 안에서 네트워크 응답을 기다리거나 sleep하면 같은 Worker의
다른 Flow도 지연됩니다. 느린 작업은 적절한 비동기 서비스나 실행기에 맡기세요.

Flow를 만들 때마다 새로운 Step 인스턴스를 만들고, 필요한 서비스는 생성자로
전달하세요. `stepNo`는 작은 내부 진행 번호로만 사용하고,
복구가 필요한 업무 상태는 도메인 저장소에 두세요.
[Step 설계](docs/runtime-reference.md#step-design-rules)와
[이벤트·대기](docs/runtime-reference.md#event-driven-steps)를 참고하세요.

<a id="fits-inside-your-application"></a>

## 기존 애플리케이션 안에 배치하기

Flower는 실행 계층입니다. 도메인 모델과 의존성 주입 컨테이너는
기존의 책임을 유지합니다.

```text
REST / Kafka 입력
        ↓
애플리케이션 workflow: Flow + Step 클래스
        ↓
도메인 서비스, repository, SDK adapter
```

Spring 멀티모듈 애플리케이션에서는 workflow 모듈이 Flower에 의존하고,
domain 모듈은 자체 도메인 객체·규칙·서비스를 유지할 수 있습니다.

Kafka를 사용하는 작업에서는 다음 구분을 유지하세요.

```text
Kafka event  = 어떤 일이 일어났다는 알림
Flower Step  = 실행이 다음으로 진행할 수 있는지 판단
Database     = 업무상 사실을 기억
```

도메인 사실을 저장하고, JVM 내부 알림을 발행하고, Step이 다음 진행 여부를
판단하게 합니다. 새로 저장한 상태에 의존하는 Flow는 트랜잭션 커밋 후 제출하세요.
이벤트 중복 처리, 필요한 inbox/outbox, 시작 시 처리 중인 업무의 복구는
호스트 애플리케이션이 담당합니다.

[Kafka 연동](docs/runtime-reference.md#typical-use-with-kafka),
[Flow 제출](docs/runtime-reference.md#flow-submission),
[Bloom 연동](docs/runtime-reference.md#event-bus-choices),
[실행 컨텍스트](docs/runtime-reference.md#execution-context)를 참고하세요.
`ExecutionContext`는 실행 신원을 전달합니다. 여기에 넣은 `tenantId`는
중복 검사에 쓰는 `FlowId`를 바꾸지 않습니다.

## Spring Boot

Java 17 / Spring Boot 3.x에서는 `flower-spring-boot-starter`를 사용합니다.

```kotlin
dependencies {
    implementation("io.github.flowerjvm:flower-spring-boot-starter:0.1.3")
}
```

```yaml
flower:
  enabled: true
  auto-start: true
  workers:
    - name: orders
      interval-ms: 100
```

starter는 기본 clock·event bus와 함께 Engine 및 생명주기를 구성합니다.
JDBC 체크포인트는 명시적으로 활성화해야 합니다. Java 8/11 애플리케이션은
호환되는 Core artifact를 사용할 수 있지만 이 starter를 로드할 수는 없습니다.

[Spring Boot 설정](docs/runtime-reference.md#spring-boot)을 참고하세요.

<a id="testing"></a>

## 테스트

Core는 `engine.attach()`와 `worker.tickOnce()`를 통한 수동 실행을 지원합니다.
`ManualClock`으로 시간을 제어할 수 있습니다. 선택적인 MVP `flower-testkit`은
반복되는 테스트 구성과 검증을 묶어 제공합니다.

testkit 의존성을 추가한 뒤, 앞의 `FlowerQuickStart` 내부 클래스를 재사용하면
다음처럼 전이를 검증할 수 있습니다.

```java
try (FlowTestHarness harness = FlowTestHarness.create()) {
    Flow flow = Flow.builder("order", "ORD-1")
            .step("accept", new FlowerQuickStart.PrintStep("accepted"))
            .step("payment", new FlowerQuickStart.WaitForPaymentStep())
            .build();

    harness.submit(flow)
            .tick() // Complete accept and select payment.
            .tick() // Enter payment and subscribe before publishing.
            .assertFlow("order", "ORD-1")
            .isRunning()
            .currentStepIs("payment");

    harness.publish(new FlowerQuickStart.PaymentApproved("ORD-1"))
            .tick()
            .assertFlow("order", "ORD-1")
            .isFinished();
}
```

`io.github.flowerjvm.flower.testkit.FlowTestHarness`와 `Flow`를 import하세요.
이 예제는 payment가 마지막인 `accept → payment` 두 Step Flow를 사용합니다.
빠른 시작의 세 Step Flow에는 이후 fulfill 단계가 더 있습니다.

[Testkit 구성과 복구 테스트](docs/runtime-reference.md#testing-with-flower-testkit)를 참고하세요.

<a id="inspect-the-same-model-at-runtime"></a>

## 실행 중에도 같은 모델로 확인

코드에 선언한 Flow는 애플리케이션을 실행하면서 확인하는 구조이기도 합니다.

`Engine.dump()`는 활성 Flow, 현재 Step, 선언된 Step 순서, 실행 컨텍스트를
제공합니다. 생명주기 리스너는 제출·진입·이탈·완료·취소·실패를 관찰합니다.
선택적인 trace listener와 sink를 사용하면 실행 이력과 연관된 run 정보를
추가할 수 있습니다.

Spring Boot starter는 기존 애플리케이션 웹서버에 선택적으로 내부 콘솔을
제공할 수 있습니다.

![Worker, 활성 Flow, 현재 Step, 실행 컨텍스트를 보여주는 Flower 콘솔](assets/flower-console-runtime.png)

[Flower Studio](https://github.com/flowerjvm/flower-studio)는 별도의 읽기 전용
로컬 실행 추적 도구입니다. 실행 경로, 대기, 복구, 평가 결과와 함께 선택적인
Agent·Harness·Action 정보를 살펴볼 수 있습니다.
운영 지표와 경보는 호스트의 관측 플랫폼이 담당합니다.

관리 endpoint는 기본적으로 꺼져 있습니다. 실행 식별자와 운영 상태가 노출될 수
있으므로 애플리케이션 인증과 적절한 네트워크 접근 통제로 보호하세요.

[관측·추적·콘솔 설정](docs/runtime-reference.md#observability)을 참고하세요.

<a id="ai-is-a-use-case-not-a-dependency"></a>

## AI는 사용 사례이지 필수 의존성이 아닙니다

사람과 코딩 에이전트가 사용하는 같은 실행 모델로 애플리케이션 내부의
AI 작업도 조율할 수 있습니다.

### AI 애플리케이션의 흐름 실행

애플리케이션의 실행 단계를 동일한 Core 모델로 표현합니다.

```text
컨텍스트 준비
  → 모델 또는 에이전트 실행
  → 결과 대기
  → 출력 검증
  → 필요한 경우 승인 요청
  → 통제된 액션 실행
  → 결과 상태 확인
```

단계는 여전히 Flow와 Step입니다. AI 고유의 책임은 Core 밖에 둡니다.

| 프로젝트 | 책임 |
| --- | --- |
| Flower Core | 애플리케이션 Flow 실행, 현재 Step, 명시적인 대기와 전이. |
| [Flower Agent](https://github.com/flowerjvm/flower-agent) | AgentRun, 모델 턴, 대화 이력, 도구 호출, 예산, 완료. |
| [Flower AI Harness](https://github.com/flowerjvm/flower-ai-harness) | 최종 구조화 출력 검증, 전체 작업 단위의 보완·재시도. |
| [Flower Action Runtime](https://github.com/flowerjvm/flower-action-runtime) | 정책·승인·멱등성·감사를 적용하는 변경 액션 실행. |

모델 호출은 애플리케이션 서비스나 adapter에 두고 Worker를 차단하지 않는
방식으로 요청하세요. "승인"을 Step으로 표현하는 것만으로 권한 검사가 구현되지는
않습니다. 실제 통제는 애플리케이션이나 Action Runtime이 수행해야 합니다.

[Flower Agent Samples](https://github.com/flowerjvm/flower-agent-samples)는
OpenAI 호환 클라우드·로컬 모델과 연동하는 실행 가능한 Spring Boot 예제를 제공합니다.

<a id="use-flower-with-chatgpt-and-codex"></a>

### 코딩 에이전트 지침과 빌드 검사 구성

[ChatGPT·Codex용 Flower plugin](https://chatgpt.com/plugins/plugins_6a6b70b4903081918ec3eb37651cf01f)은
Flower 스킬을 제공합니다. 지침은
[Flower Skills 저장소](https://github.com/flowerjvm/flower-agent-skills)에서도 확인할 수 있습니다.

[Maven](flower-check-maven-plugin/README.md) 또는
[Gradle](flower-check-gradle-plugin/README.md) 검사 도구를 호스트 빌드에 추가하고,
[결정적 테스트](#testing)로 애플리케이션 동작을 검증하세요.
`flower-check`는 Worker tick 차단처럼 지원하는 구조·사용 규칙 위반을 탐지하고,
테스트는 해당 애플리케이션에서 기대하는 결과를 확인합니다.

<a id="execution-boundaries"></a>

## 실행 범위와 보장 경계

Flower는 작게 유지되는 만큼 적용 범위를 명확하게 정합니다.

| 경계 | 제공 범위와 주의점 |
| --- | --- |
| 하나의 JVM | Core는 분산 스케줄러, 다중 노드 조정기, BPMN 엔진, durable saga 엔진이 아닙니다. |
| tick 기반 Core | 자동 실행 모드에서는 Worker 하나가 단일 스케줄러 스레드에서 Flow를 tick합니다. `stay()`를 반환한다고 이벤트가 올 때만 실행되는 런타임으로 바뀌지 않습니다. |
| 메모리 알림 | signal·구독은 영속 이벤트 로그가 아닙니다. 복구가 중요하면 저장된 업무 사실을 다시 확인하세요. |
| 선택적인 checkpoint/resume | 새로운 Flow를 구성하고 저장된 위치·실행 신원으로 재개합니다. Step 객체, signal, 임의의 업무 상태는 직렬화하지 않으며 실행 replay를 제공하지 않습니다. |
| 외부 부수효과 | 체크포인트가 DB 쓰기, 메시지, API 호출을 exactly-once로 만들지는 않습니다. 필요한 경우 애플리케이션 멱등성과 영속적인 작업 의도·outbox 기록을 사용하세요. |
| 복구 가능한 deadline | Core `startTimeout(...)`은 런타임 전용이므로 durable Flow에서 거부됩니다. 도메인 상태에 deadline을 저장하거나 별도 event-loop 런타임의 await deadline을 사용하세요. |
| 저장소와 소유권 | 체크포인트 쓰기는 동기식입니다. 여러 프로세스의 복구 조정에는 호스트의 lock·lease·leader election이 필요합니다. |
| 규모 | Core는 소규모·중간 규모의 프로세스 내부 작업을 대상으로 합니다. 대기 Flow가 매우 많으면 샤딩이나 다른 스케줄링 전략이 필요할 수 있습니다. |

MVP [flower-eventloop](flower-eventloop/README.md)는 별도의 실행 계열이며
자체 API와 복구 동작을 가집니다. 이 문서의 Core 예제는 tick 기반
Worker / Flow / Step 계약을 설명합니다.

복구 정책, event-loop의 장애 구간, 복구 소유권은
[Checkpoint / Resume](docs/runtime-reference.md#checkpoint--resume),
[Operational boundaries](docs/runtime-reference.md#operational-boundaries),
[Persistence](docs/persistence.md)를 참고하세요.

<a id="when-to-use-flower"></a>

## 언제 사용하면 좋은가

업무에 의미 있는 실행 단계가 있고, 특히 이벤트 대기·timeout·단계 재진입·
현재 실행 위치 확인이 필요할 때 Flower를 사용하세요.

짧은 `validate → save → return` 작업에는 일반 Java 메서드만으로 충분할 수 있습니다.
작은 상태 머신에는 enum과 switch만으로 충분할 수 있습니다.
실행을 조율하는 장치 자체가 별도의 관리 대상이 되기 시작할 때 Flower의 가치가 생깁니다.

도메인에 종속되지 않는다는 뜻이지 모든 Java 코드를 Flow로 다시 작성해야 한다는
뜻은 아닙니다.

<a id="modules"></a>

## 모듈

**Core부터 시작하고, 필요한 것만 추가하세요.**

| 모듈 | 추가하는 기능 | 상태 |
| --- | --- | --- |
| `flower-core` | Engine, Worker, Flow, Step, event bus, clock, listener API. | 확립된 실행 모델 |
| `flower-spring-boot-starter` | Spring Boot 구성과 생명주기 연동. | 선택 사항 |
| `flower-persistence-jdbc` | 명시적으로 스키마를 구성하는 JDBC 체크포인트. | 선택 사항 |
| `flower-observability` | 로깅·추적·지표 연동·trace sink. | 선택 사항 |
| `flower-testkit` | 실행 순서와 시간을 제어하는 테스트 도구. | MVP |
| [flower-check 및 빌드 plugin](flower-check/README.md) | 알려진 Flower 안티패턴의 빌드 시점 검사. | MVP |
| [flower-evaluation](flower-evaluation/README.md) | 오프라인 데이터셋·평가기·비교·피드백. | MVP |
| [flower-eventloop](flower-eventloop/README.md)와 `flower-eventloop-persistence-jdbc` | 별도의 이벤트 기반 실행과 체크포인트. | MVP |

MVP 표시는 위의 개별 선택 모듈에 대한 설명입니다.
이 모듈들은 Core의 확립된 실행 모델을 바탕으로 API를 다듬고 있습니다.

JDBC 저장소는 PostgreSQL, MySQL, Oracle, H2, SQLite용 스키마 SQL을 제공합니다.
호스트 애플리케이션이 드라이버를 추가하고 스키마를 명시적으로 적용합니다.

모든 모듈을 함께 설치할 필요는 없습니다. 별도 배포되는 Bloom adapter는 기존
Bloom event bus를 연결하며, Flower Core 자체에도 `InMemoryEventBus`가 포함되어 있습니다.

[모듈 상세와 성숙도](docs/runtime-reference.md#modules-and-maturity),
[Java 호환성](docs/runtime-reference.md#java-compatibility)을 참고하세요.

<a id="where-it-comes-from"></a>

## 어디에서 시작되었나

Flower의 `Worker → Flow → Step → StepResult` 실행 모델은 산업용 장비 제어
시스템과 업무 애플리케이션을 개발하며 쌓은 실무 경험을 바탕으로 형성되었습니다.

오랜 시간 여러 단계를 거쳐 진행되는 작업에서 반복적으로 관찰한 패턴을
Java 애플리케이션용 재사용 가능한 워크플로우 런타임으로 일반화했습니다.
명시적인 실행 단계, 결과에 따른 전이, 대기, timeout, 재시도, 사람의 개입,
확인 가능한 실행 이력이 그 패턴입니다.

바탕이 되는 원칙은 단순합니다. 현재 상태를 드러내고, 전이를 명시하고,
각 작업 단위를 작게 유지하고, 사람이 확인할 수 있는 실행 기록을 남깁니다.

건축사 사무소 SaaS 문서 흐름, TOS(Terminal Operating System) 실행 계층,
게임 서버 조율, 통제된 AI 자동화 프로젝트에 적용하며 설계를 다듬고 있습니다.
이는 설계를 검증하고 강화하는 적용처의 설명이며 폭넓은 외부 도입을
주장하는 내용은 아닙니다.

<a id="documentation-and-project-status"></a>

## 문서와 프로젝트 상태

[런타임 상세 문서](docs/runtime-reference.md)에는 API 사용 지침, Kafka·Bloom 연동,
Spring 설정, 체크포인트, 실행 컨텍스트, 관측 기능, 테스트, 모듈 성숙도를 담았습니다.
상세 문서는 현재 영문이며, 이 소개 문서는 영어와 한국어로 제공합니다.

더 자세한 내용은 [Persistence](docs/persistence.md),
[Tracing, Studio, and Evaluation](docs/tracing-studio-evaluation.md),
[Trace Storage and Security](docs/tracing-storage-security.md),
[Domain Observation Adapters](docs/domain-observation-adapters.md)를 참고하세요.

프로젝트 운영: [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) ·
[Roadmap](ROADMAP.md) · [Releasing](docs/RELEASING.md).

저장소 빌드:

```bash
mvn -B verify
```

전체 저장소 빌드에는 JDK 17이 필요합니다. 애플리케이션에서는 일반적으로
Maven Central의 릴리스 artifact를 사용합니다. 별도 빌드되는 Gradle 검사 도구를
개발하는 기여자는 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

<a id="license"></a>

## 라이선스

Flower는 [Apache License 2.0](LICENSE)으로 배포됩니다.

**도메인은 달라도, 실행 모델은 같습니다.**

흐름은 명시적으로. 도메인은 애플리케이션에 그대로.
