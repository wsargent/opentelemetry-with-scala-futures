package services

import org.slf4j.Logger

trait Logging {
  protected lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)
}
