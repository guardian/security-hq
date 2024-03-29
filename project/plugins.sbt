addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")

libraryDependencies += "org.vafer" % "jdeb" % "1.10" artifacts Artifact("jdeb", "jar", "jar")

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.1")

// web plugins

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.10")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-mocha" % "1.1.2")

addDependencyTreePlugin