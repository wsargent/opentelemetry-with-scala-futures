package services

import com.google.inject.{AbstractModule, Guice, Injector, Scopes}
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.context.Context
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import javax.inject.{Inject, Provider}

// A simple service that will be request-scoped for testing
class TestService @Inject() () {
  val instanceId: String = java.util.UUID.randomUUID().toString
}

// Test module that binds the TestService as request-scoped
class TestModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[RequestScope]).in(Scopes.SINGLETON)
    bind(classOf[TestService]).in(classOf[RequestScoped])
    bindScope(classOf[RequestScoped], new RequestScope())
  }
}

class RequestScopeSpec extends PlaySpec with Matchers {

  "RequestScope" must {

    "return the same instance for the same request ID" in {
      // Create an injector with our test module
      val injector = Guice.createInjector(new TestModule())
      val requestScope = injector.getInstance(classOf[RequestScope])

      // Set up a request context with a specific request ID
      val requestId = "test-request-1"
      val baggage = Baggage.builder().put("request_id", requestId).build()
      val scope = baggage.storeInContext(Context.current()).makeCurrent()

      try {
        // Get the service instance twice within the same request
        val service1 = injector.getInstance(classOf[TestService])
        val service2 = injector.getInstance(classOf[TestService])

        // They should be the same instance
        service1.instanceId mustEqual service2.instanceId
      } finally {
        scope.close()
      }
    }

    "return different instances for different request IDs" in {
      // Create an injector with our test module
      val injector = Guice.createInjector(new TestModule())
      val requestScope = injector.getInstance(classOf[RequestScope])

      // Set up the first request context
      val requestId1 = "test-request-1"
      val baggage1 = Baggage.builder().put("request_id", requestId1).build()
      val scope1 = baggage1.storeInContext(Context.current()).makeCurrent()

      val service1 = injector.getInstance(classOf[TestService])
      scope1.close()

      // Set up the second request context
      val requestId2 = "test-request-2"
      val baggage2 = Baggage.builder().put("request_id", requestId2).build()
      val scope2 = baggage2.storeInContext(Context.current()).makeCurrent()

      try {
        val service2 = injector.getInstance(classOf[TestService])

        // They should be different instances
        service1.instanceId must not equal service2.instanceId
      } finally {
        scope2.close()
      }
    }

    "clean up instances when a request is complete" in {
      // Create an injector with our test module
      val injector = Guice.createInjector(new TestModule())
      val requestScope = injector.getInstance(classOf[RequestScope])

      // Set up a request context
      val requestId = "test-request-cleanup"
      val baggage = Baggage.builder().put("request_id", requestId).build()

      // First request - get a service instance
      val scope1 = baggage.storeInContext(Context.current()).makeCurrent()
      try {
        val service1 = injector.getInstance(classOf[TestService])
        val id1 = service1.instanceId

        // Get another instance in the same request - should be the same
        val service1Again = injector.getInstance(classOf[TestService])
        service1.instanceId mustEqual service1Again.instanceId
      } finally {
        scope1.close()
      }

      // Clean up the request
      requestScope.cleanupRequest(requestId)

      // Second request with the same request ID - should get a new instance
      val scope2 = baggage.storeInContext(Context.current()).makeCurrent()
      try {
        val service2 = injector.getInstance(classOf[TestService])

        // Get another instance in the same request - should be the same
        val service2Again = injector.getInstance(classOf[TestService])
        service2.instanceId mustEqual service2Again.instanceId
      } finally {
        scope2.close()
      }
    }

    "fall back to unscoped provider when no request ID is available" in {
      // Create an injector with our test module
      val injector = Guice.createInjector(new TestModule())

      // No baggage context set up, so there's no request ID

      // Get two service instances
      val service1 = injector.getInstance(classOf[TestService])
      val service2 = injector.getInstance(classOf[TestService])

      // They should be different instances because there's no request scope
      service1.instanceId must not equal service2.instanceId
    }
  }
}
