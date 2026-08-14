import sbt.Keys.libraryDependencies

import scala.concurrent.duration.DurationInt

// common settings (apply to all projects)
ThisBuild / organization := "com.gu"
ThisBuild / version := "0.5.0"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-no-indent", // don't support significant indentation
  "-Wunused:all", // fail the build on unused imports, vals, params, and private members
  "-Xfatal-warnings"
)

resolvers += DefaultMavenRepository

val awsLambdaVersion = "1.4.0"
val awsSdkVersion = "2.52.0"

/*
 * To test whether any of these entries are redundant:
 * 1. Comment it out
 * 2. Run `sbt dependencyList`
 * 3. If no earlier version appears in the dependency list, the entry can be removed.
 */
val safeTransitiveDependencies = {
  val jacksonV2Version = "2.22.1"
  val jacksonV3Version = "3.2.1"
  Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonV2Version,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonV2Version,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonV2Version,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonV2Version,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV2Version,
    "com.fasterxml.jackson.module" % "jackson-module-scala_3" % jacksonV2Version,
    "tools.jackson.core" % "jackson-core" % jacksonV3Version,
    "tools.jackson.core" % "jackson-databind" % jacksonV3Version
  )
}

val mergeStrategySettings = assemblyMergeStrategy := {
  case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
  case _                                                   => MergeStrategy.first
}

lazy val core = (project in file("core"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := "security-hq-core",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "3.13.0",
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
      "joda-time" % "joda-time" % "2.14.3",
      "com.gu" %% "anghammarad-client" % "7.0.0",
      "com.gu" %% "janus-config-tools" % "14.0.0",
      "software.amazon.awssdk" % "iam" % awsSdkVersion,
      "software.amazon.awssdk" % "cloudwatch" % awsSdkVersion,
      "software.amazon.awssdk" % "dynamodb" % awsSdkVersion,
      "software.amazon.awssdk" % "ec2" % awsSdkVersion,
      "software.amazon.awssdk" % "s3" % awsSdkVersion,
      "software.amazon.awssdk" % "sns" % awsSdkVersion,
      "software.amazon.awssdk" % "sts" % awsSdkVersion,
      "software.amazon.awssdk" % "support" % awsSdkVersion,
      "ch.qos.logback" % "logback-classic" % "1.6.1",
      "net.logstash.logback" % "logstash-logback-encoder" % "9.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test,
      "org.scalacheck" %% "scalacheck" % "1.19.0" % Test
    ) ++ safeTransitiveDependencies,
    Test / parallelExecution := false,
    Test / fork := false
  )

lazy val iamOutdatedCredentials = (project in file("iam-outdated-credentials"))
  .enablePlugins(AssemblyPlugin)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := """iam-outdated-credentials""",
    scalacOptions += "--deprecation",
    Assets / pipelineStages := Seq(digest),
    // exclude docs
    Compile / doc / sources := Seq.empty,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "markdown",
    Test / unmanagedSourceDirectories += baseDirectory.value / "test" / "jars",
    Test / parallelExecution := false,
    Test / fork := false,

    assembly / mainClass := Some("logic.IamOutdatedCredentialsMain"),

    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % awsLambdaVersion,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    mergeStrategySettings
  )

lazy val iamUnrecognisedUsers = (project in file("iam-unrecognised-users"))
  .dependsOn(core % "compile->compile;test->test")
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "iam-unrecognised-users",
    scalacOptions += "--deprecation",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.4.0",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    assembly / mainClass := Some("unrecognised.Main"),
    mergeStrategySettings
  )

lazy val root = (project in file("."))
  .aggregate(core, iamUnrecognisedUsers, iamOutdatedCredentials)
  .settings(
    name := """security-hq"""
  )

addCommandAlias("dependency-tree", "dependencyTree")
