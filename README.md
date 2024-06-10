# OpenTelemetry With Scala Futures

This is an example project in Play that shows how to manage OpenTelemetry together with Scala Futures, and some of the problems involved.

## Running with Agent

The project comes with `opentelemetry-javaagent` enabled, which auto-instruments Play and Akka/Pekko.  This agent does most of the heavy lifting for you, but you do have to run the agent in a distinct JVM, which means that using `sbt run` will not work for you and tests run from IntelliJ IDEA will not work.

## Running

To start the application, you can start up a server:

```scala
sbt runProd
```

And then go to http://localhost:9000.

## Test

To run through the tests:

```scala
sbt test
```

The tests should all be running successfully.

## Description

The OpenTelemetry Java agent does [much of the work](https://opentelemetry.io/docs/zero-code/java/agent/) in instrumenting the main points of the underlying frameworks and libraries by using ByteBuddy advice to propagate `Span` between Threads.

However, if you are using Scala, you will have to add some manual tracing in order to close some gaps.

### Spans vs Scopes

There are two complimentary handles involved in tracing: managing lifecycle of a `Span`, and managing a  lifecycle of a `Scope`.

#### Spans

A `Span` can be passed around between threads, and the lifecycle of a Span can technically end at any point.

It's generally safest if you end a span that you created.  For synchronous code, this is easy:

```scala
val span = tracer.spanBuilder("operationInvolvingSpan").startSpan()
try {
  operationInvolvingSpan(span)
} finally {
  span.end()
}
```

For async code, you ideally want an `onComplete` that will handle both success and failure cases:

```scala
implicit val span: Span = tracer.spanBuilder(spanName).startSpan()
val f: Future[Foo] = operationInvolvingFuture(span)
f.onComplete {
    case Success(_) =>
      span.end()
    case Failure(e) =>
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
      span.end()
}(ExecutionContext.global)
f
```

#### Scopes

If you explicitly pass in spans everywhere you go, you might think you would be okay, but that is not the case.  If you are calling code that the OpenTelemetry Java Agent has instrumented, then it is expecting a thread local `Span` to be set on the current thread, accessible via `Span.current`, and so you should always set that thread local to the incoming `Span` as soon as you have it.

This is done using `span.makeCurrent`, which returns a `Scope`.

```scala
val scope: Scope = span.makeCurrent
```

The `Scope` is a handle that manages the lifecycle of the thread-local, essentially "unsetting" it when `scope.close` is called:

```scala
val scope = span.makeCurrent
try {
  // activeSpan should equal span!
  val activeSpan = Span.current
  operationInvolvingSpan(span)
} finally {
  scope.close
  // after this point Span.current does not equal span 
}
```

Notably, if you create manual spans, `Span.current` is called under the hood to register itself as a parent to the new span:

```scala
val scope = parentSpan.makeCurrent
try {
  // parentSpan is parent of childSpan because it is accessible via thread-local
  val childSpan: Span = tracer.spanBuilder("getCurrentTime").startSpan()
  try {
    operationInvolvingSpan(childSpan)
  } finally {
    childSpan.end
  }
} finally {
  scope.close
}
```

It is safest to limit access to `Scope` and not pass it around between methods, always closing the scope in `finally` blocks.  If a scope is opened and not closed, then resource leaks can occur.

There is a safety feature in OpenTelemetry that you can enable, which enables strict context checking.  This should not be enabled in production, but can be useful in tests.

To enable strict context checking in the Java Agent:

```scala
-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=true
```

Note that this is distinct from the `io.opentelemetry.context.enableStrictContext` system property used to enable strict context checking in the SDK.

## Design

This section will walk through places where the instrumentation does not cover asynchronous code, and show some workarounds.

### Assertions

When writing your code, you will want to ensure that you have as little work as possible to find out when stuff has gone wrong.  The fastest way to do this is to ensure that you can assert a `Span` exists.

```scala
  private def assertSpan()(implicit enclosing: Enclosing, line: sourcecode.Line): Span = {
    assertSpan(Span.current)
  }

  private def assertSpan(span: Span)(implicit enclosing: Enclosing, line: sourcecode.Line): Span = {
    if (!span.isRecording) {
      throw new IllegalStateException(s"We don't have a current span from ${enclosing.value} line ${line.value}!")
    }
    span
  }
```

We'll then create a `getCurrentTime` method in a service

```scala
@Singleton
class MyService {
  def getCurrentTime(implicit enc: sourcecode.Enclosing, line: sourcecode.Line): Long = {
    assertSpan()

    val result = System.currentTimeMillis()
    logger.debug(s"Rendered: $result")
    result
  }
}
```

And we can test this out to verify that we need an active span:

```scala
class MyServiceSpec extends PlaySpec with Matchers with GuiceOneServerPerSuite with Injecting with ScalaFutures with TryValues {
  private val tracer = GlobalOpenTelemetry.getTracer("application")

  "MyService" must {
    "fail with getCurrentTime without an active span" in {
      val myService = inject[MyService]
      Try(myService.getCurrentTime).failure.exception mustBe an[IllegalStateException]
    }

    "work with getCurrentTime with an active span" in {
      val myService = inject[MyService]
      val span = tracer.spanBuilder("getCurrentTime").startSpan()
      val scope = span.makeCurrent()
      try {
        myService.getCurrentTime
      } finally {
        span.end()
        scope.close()
      }
    }
  }
}
```

This all works as expected.

### Futures

Now let's do the same thing but use a `Future`:

```scala
@Singleton
class MyService @Inject()(implicit ec: ExecutionContext) {
  // ...
  
  def futureCurrentTime: Future[Long] = {
    Future {
      getCurrentTime
    }(ec)
  }
}
```

And we'll add tests here just to prove that it works:

```scala
class MyServiceSpec {
  // ...
  "fail with futureCurrentTime if a span is not active" in {
    val myService = inject[MyService]
    myService.futureCurrentTime.failed.futureValue mustBe an[IllegalStateException]
  }

  "work with futureCurrentTime with an active span" in {
    val myService = inject[MyService]
    val span = tracer.spanBuilder("futureCurrentTime").startSpan()
    val scope = span.makeCurrent()
    whenReady(myService.futureCurrentTime) {
      scope.close()
      span.end()
      _ > 0 mustBe true
    }
  }
}
```

### Delayed Futures

So far, so good.  But what if we change the code to delay the future slightly?  Using the `play.api.libs.concurrent.Futures` class, we'll delay the future by 10 milliseconds:

```scala
class MyService @Inject()(futures: Futures)(implicit ec: ExecutionContext) {
  // ...
  
  def brokenDelayedCurrentTime: Future[Long] = {
    val span = tracer.spanBuilder("brokenDelayedCurrentTime").startSpan()
    val scope = span.makeCurrent()
    try {
      val delayed = futures.delayed(10.millis) {
        futureCurrentTime
      }
      delayed.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }
      delayed
    } finally {
      scope.close()
    }
  }
}
```

This will fail.  The scope does not propagate properly between the `delayed` call to the future.

```scala
class MyServiceSpec {
  // ...
  
  "fail with brokenDelayedCurrentTime because it activates outside the future" in {
    val myService = inject[MyService]
    myService.brokenDelayedCurrentTime.failed.futureValue mustBe an[IllegalStateException]
  }
}
```

It's possible to fix this by activating the scope inside the delayed block:

```scala
futures.delayed(10.millis) {
  val scope = span.makeCurrent()
  try {
    futureCurrentTime
  } finally {
    scope.close()
  }
}
```

But this doesn't really address the underlying problem -- why does this happen?

The reason why is that the `Futures` class uses the `after` pattern internally:

```scala
class DefaultFutures @Inject() (actorSystem: ActorSystem) extends Futures {
  // ...
  override def delayed[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    implicit val ec = actorSystem.dispatcher
    org.apache.pekko.pattern.after(duration, actorSystem.scheduler)(f)
  }
}
```

This takes an implicit `ExecutionContext` which is passed through to the after pattern.  To fix this, we must tell `after` to use an execution context that is aware of the current `Context` and will call `context.wrap`:

```scala
class MyFutures @Inject()(actorSystem: ActorSystem) {

  def delayed[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    val context = Context.current()
    implicit val ec: ExecutionContextExecutor = new TracingExecutionContext(actorSystem.dispatcher, context)
    org.apache.pekko.pattern.after(duration, actorSystem.scheduler)(f)
  }

  class TracingExecutionContext(executor: ExecutionContextExecutor, context: Context) extends ExecutionContextExecutor {
    override def reportFailure(cause: Throwable): Unit = executor.reportFailure(cause)
    override def execute(command: Runnable): Unit = executor.execute(context.wrap(command))
  }
}
```

After doing this, we can see that the code works correctly:

```scala
class MyService @Inject()(contextAwareFutures: MyFutures)(implicit ec: ExecutionContext) {
  
  def contextAwareDelayedCurrentTime: Future[Long] = {
    val span = tracer.spanBuilder("contextAwareDelayedCurrentTime").startSpan()
    val scope = span.makeCurrent()
    try {
      // contextAwareFutures knows about the activated span and will propagate it
      // correctly between threads
      val delayed = contextAwareFutures.delayed(10.millis) {
        futureCurrentTime
      }
      delayed.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }
      delayed
    } finally {
      scope.close()
    }
  }
}
```

And the given test:

```scala
class MyServiceSpec {
  // ...
  "work with a Futures that is context aware" in {
    val myService = inject[MyService]
    whenReady(myService.contextAwareDelayedCurrentTime) {
      _ > 0 mustBe true
    }
  }
}
```
