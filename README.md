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

This section will walk through places where instrumentation can be a little tricky.

We'll then create a `getCurrentTime` method in a service:

```scala
@Singleton
class MyService {

  def getCurrentTime: Long = {
    val span = Span.current()
    if (span.getSpanContext == SpanContext.getInvalid) {
      throw new IllegalStateException("getCurrentTime: no active span!")
    }

    // On shutdown, threads will still be active but will get non-writable spans :-(
    val result = System.currentTimeMillis()
    if (span.isRecording) {
      val attributes = Attributes.of[java.lang.Long](resultKey, result)
      span.addEvent("getCurrentTime", attributes)
    } else {
      logger.warn("getCurrentTime: span is read only! {}", span)
    }
    logger.debug("getCurrentTime: {}", "result" -> result)
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

## Creating a Request Scope with OpenTelemetry

One neat thing you can do is abuse the Baggage functionality to implement request scoped components in Guice.

To do this, you need to set a `RequestScopeFilter` to set up the baggage with the current request id:

```scala
class RequestScopeFilter @Inject()(tracer: Tracer, requestScope: RequestScope, injector: Injector) extends EssentialFilter with Logging {

  override def apply(next: EssentialAction): EssentialAction = { rh =>
    val requestId = requireNonNull(rh.id.toString)
    val baggage = Baggage.builder().put("request_id", requestId).build()
    val baggageScope = baggage.storeInContext(Context.current()).makeCurrent

    // Seed the scope with the current request
    requestScope.seed(Key.get(classOf[RequestHeader]), rh)

    implicit val ec: ExecutionContext = ExecutionContext.parasitic
    next(rh).map { result =>
      requestScope.cleanupRequest(requestId)
      baggageScope.close()
      result
    }.recover {
      case e: Exception =>
        requestScope.cleanupRequest(requestId)
        baggageScope.close()
        throw e
    }
  }
}
```

and then you can look up the request later as long as you're in the same baggage context:

```scala
class RequestLookup @Inject()(requestScope: RequestScope) {
  // Retrieve the current request from the RequestScope
  def request: RequestHeader = {
    requestScope.get(Key.get(classOf[RequestHeader]))
  }
}
```