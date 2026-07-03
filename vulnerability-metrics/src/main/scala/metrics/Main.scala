package metrics

/** Local entrypoint for testing metrics collection outside lambda. */
@main def runMetricsCollector(): Unit = MetricsCollector.run()
