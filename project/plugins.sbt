addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0")

libraryDependencies += "org.vafer" % "jdeb" % "1.11" artifacts Artifact("jdeb", "jar", "jar")

// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")

// web plugins

addSbtPlugin("com.github.sbt" % "sbt-coffeescript" % "1.11.0")

addSbtPlugin("com.github.sbt" % "sbt-less" % "1.5.0")

addSbtPlugin("com.github.sbt" % "sbt-jshint" % "2.0.1")

addSbtPlugin("com.github.sbt" % "sbt-rjs" % "2.0.0")

addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")

addSbtPlugin("com.github.sbt" % "sbt-mocha" % "2.1.0")

addDependencyTreePlugin