package services

import com.google.inject.{Key, Provider, Scope}
import io.opentelemetry.api.baggage.Baggage

import scala.collection.concurrent.TrieMap

/**
 * This is a custom request scope that uses Opentelemetry Baggage as an underlying mechanism.
 */
class RequestScope extends Scope with Logging {
  opaque type BaggageKey = String
  opaque type KeyMap = TrieMap[Key[?], AnyRef]

  // Use a concurrent map to store scoped objects per request ID
  private val scopedObjects = new TrieMap[BaggageKey, KeyMap]()

  def seed[T](key: Key[T], value: T): Unit = {
    // Get the current baggage context
    val baggage = Baggage.current()

    // Get the request ID from the baggage
    val requestId = baggage.getEntryValue("request_id")

    if (requestId == null || requestId.isEmpty) {
      logger.warn("seed: No request_id found in baggage context, cannot seed {}", key)
      return
    }

    logger.debug("seed: storing {} for request_id {}", key, requestId)

    // Get or create the map for this request
    val requestScopedObjects = scopedObjects.getOrElseUpdate(requestId, TrieMap.empty[Key[?], AnyRef])

    // Store the value for this key
    requestScopedObjects.put(key, value.asInstanceOf[AnyRef])
  }

  def get[T](key: Key[T]): T = {
    // Get the current baggage context
    val baggage = Baggage.current()

    // Get the request ID from the baggage
    val requestId = baggage.getEntryValue("request_id")

    if (requestId == null || requestId.isEmpty) {
      throw new IllegalStateException(s"get: No request_id found in baggage context, cannot get $key")
    }

    logger.debug("get: retrieving {} for request_id {}", key, requestId)

    // Get the map for this request
    val requestScopedObjects = scopedObjects.getOrElse(requestId,
      throw new IllegalStateException(s"No scoped objects found for request_id $requestId"))

    // Get the value for this key
    requestScopedObjects.getOrElse(key,
      throw new IllegalStateException(s"No value found for key $key")).asInstanceOf[T]
  }

  override def scope[T](key: Key[T], unscoped: Provider[T]): Provider[T] = {
    new Provider[T] {
      override def get(): T = {
        // Get the current baggage context
        val baggage = Baggage.current()

        // Get the request ID from the baggage
        val requestId = baggage.getEntryValue("request_id")

        if (requestId == null || requestId.isEmpty) {
          logger.warn("scope: No request_id found in baggage context, falling back to unscoped provider for {}", key)
          return unscoped.get()
        }

        logger.debug("scope: getting {} for request_id {}", key, requestId)

        // Get or create the map for this request
        val requestScopedObjects = scopedObjects.getOrElseUpdate(requestId, TrieMap.empty[Key[?], AnyRef])

        // Get or create the object for this key
        requestScopedObjects.getOrElseUpdate(key, unscoped.get().asInstanceOf[AnyRef]).asInstanceOf[T]
      }
    }
  }

  // Method to clean up resources when a request is complete
  def cleanupRequest(requestId: String): Unit = {
    logger.debug("Cleaning up scoped objects for request_id {}", requestId)
    scopedObjects.remove(requestId)
  }
}
