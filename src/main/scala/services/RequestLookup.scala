package services

import com.google.inject.Key
import play.api.mvc.RequestHeader

import javax.inject.Inject

class RequestLookup @Inject()(requestScope: RequestScope) {

  // Retrieve the current request from the RequestScope
  def request: RequestHeader = {
    requestScope.get(Key.get(classOf[RequestHeader]))
  }
}