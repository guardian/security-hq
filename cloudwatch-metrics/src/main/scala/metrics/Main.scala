package metrics

/** Local entrypoint for running the metrics collection outside of Lambda. */
object Main {
  def main(args: Array[String]): Unit = MetricsCollector.run()
}
