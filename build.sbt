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
val awsSdkVersion = "2.55.4"

/*
 * To test whether any of these entries are redundant:
 * 1. Comment it out
 * 2. Run `sbt dependencyList`
 * 3. If no earlier version appears in the dependency list, the entry can be removed.
 */
val safeTransitiveDependencies = {
  val jacksonV2Version = "2.22.3"
  val jacksonV3Version = "3.2.3"
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
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
      "joda-time" % "joda-time" % "2.14.4",
      "com.gu" %% "anghammarad-client" % "9.0.0",
      "com.gu" %% "janus-config-tools" % "14.0.1",
      "software.amazon.awssdk" % "iam" % awsSdkVersion,
      "software.amazon.awssdk" % "cloudwatch" % awsSdkVersion,
      "software.amazon.awssdk" % "dynamodb" % awsSdkVersion,
      "software.amazon.awssdk" % "s3" % awsSdkVersion,
      "software.amazon.awssdk" % "sns" % awsSdkVersion,
      "software.amazon.awssdk" % "sts" % awsSdkVersion,
      "ch.qos.logback" % "logback-classic" % "1.6.4",
      "net.logstash.logback" % "logstash-logback-encoder" % "9.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
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
    // exclude docs
    Compile / doc / sources := Seq.empty,
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
      "com.amazonaws" % "aws-lambda-java-core" % awsLambdaVersion,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    assembly / mainClass := Some("unrecognised.Main"),
    mergeStrategySettings
  )

lazy val guardianDuty = (project in file("guardian-duty"))
  .dependsOn(core)
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "guardian-duty",
    scalacOptions += "--deprecation",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % awsLambdaVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "3.16.1",
      "software.amazon.awssdk" % "aws-crt-client" % awsSdkVersion,
      "org.scalameta" %% "munit" % "1.3.6" % Test
    ),
    assembly / mainClass := Some("com.gu.guardianduty.Lambda"),
    mergeStrategySettings
  )

lazy val root = (project in file("."))
  .aggregate(core, iamUnrecognisedUsers, iamOutdatedCredentials, guardianDuty)
  .settings(
    name := """security-hq"""
  )

addCommandAlias("dependency-tree", "dependencyTree")
