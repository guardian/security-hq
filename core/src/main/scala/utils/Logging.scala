package utils

import org.slf4j.{Logger, LoggerFactory}

/** A lightweight logging trait backed by SLF4J so that `core` doesn't depend on Play. */
trait Logging {
  protected val logger: Logger = LoggerFactory.getLogger(getClass)
}
