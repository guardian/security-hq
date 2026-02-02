import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
import play.sbt.PlayImport.PlayKeys._
import sbt.Keys.libraryDependencies

import scala.concurrent.duration.DurationInt

// common settings (apply to all projects)
ThisBuild / organization := "com.gu"
ThisBuild / version := "0.5.0"
ThisBuild / scalaVersion := "3.3.7"
// Omitting scalacOptions 'deprecation' and 'feature' here because they are included by the Play plugin
ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-no-indent", // don't support significant indentation
  "-Xfatal-warnings"
)

resolvers += DefaultMavenRepository

val awsSdkVersion = "2.40.17"
val playJsonVersion = "3.0.4"
val jacksonVersion = "2.21.0"

val mergeStrategySettings= assemblyMergeStrategy := {
  case PathList(ps@_*) if ps.last == "module-info.class" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

lazy val hq = (project in file("hq"))
  .enablePlugins(PlayScala, SbtWeb, JDebPackaging, SystemdPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := """security-hq""",
    playDefaultPort := 9090,
    fileDescriptorLimit := Some("16384"), // This increases the number of open files allowed when running in AWS
    libraryDependencies ++= Seq(
      ws,
      filters,
      "com.gu.play-googleauth" %%  "play-v30" % "32.0.2",
      "com.gu.play-secret-rotation" %% "play-v30" % "16.1.0",
       "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v2" % "16.1.0",

      "joda-time" % "joda-time" % "2.14.0",
      "co.fs2" %% "fs2-core" % "3.12.2",
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
      "software.amazon.awssdk" % "iam" % awsSdkVersion,
      "software.amazon.awssdk" % "cloudformation" % awsSdkVersion,
      "software.amazon.awssdk" % "cloudwatch" % awsSdkVersion,
      "software.amazon.awssdk" % "dynamodb" % awsSdkVersion,
      "software.amazon.awssdk" % "ec2" % awsSdkVersion,
      "software.amazon.awssdk" % "efs" % awsSdkVersion,
      "software.amazon.awssdk" % "s3" % awsSdkVersion,
      "software.amazon.awssdk" % "sns" % awsSdkVersion,
      "software.amazon.awssdk" % "ssm" % awsSdkVersion,
      "software.amazon.awssdk" % "sts" % awsSdkVersion,
      "software.amazon.awssdk" % "support" % awsSdkVersion,
      "com.vladsch.flexmark" % "flexmark" % "0.64.8",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test,
      "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
      "com.gu" %% "anghammarad-client" % "6.0.0",
      "ch.qos.logback" % "logback-classic" % "1.5.26",


      // logstash-logback-encoder brings in version 2.11.0
      // exclude transitive dependency to avoid a runtime exception:
      // `com.fasterxml.jackson.databind.JsonMappingException: Scala module 2.10.2 requires Jackson Databind version >= 2.10.0 and < 2.11.0`
      "net.logstash.logback" % "logstash-logback-encoder" % "9.0" exclude("com.fasterxml.jackson.core", "jackson-databind"),
      "com.gu" %% "janus-config-tools" % "7.0.0"
    ),


    Assets / pipelineStages := Seq(digest),
    // exclude docs
    Compile / doc / sources := Seq.empty,
    Universal / packageName := "security-hq",
    // include beanstalk config files in the zip produced by `dist`
    Universal / mappings ++=
      (baseDirectory.value / "beanstalk" * "*" get)
        .map(f => f -> s"beanstalk/${f.getName}"),
    // include upstart config files in the zip produced by `dist`
    Universal / mappings ++=
      (baseDirectory.value / "upstart" * "*" get)
        .map(f => f -> s"upstart/${f.getName}"),
    // include systemd config files in the zip produced by `dist`
    Universal / mappings ++=
      (baseDirectory.value / "systemd" * "*" get)
        .map(f => f -> s"systemd/${f.getName}"),
    Compile / unmanagedResourceDirectories += baseDirectory.value / "markdown",
    Test / unmanagedSourceDirectories += baseDirectory.value / "test" / "jars",
    Test / parallelExecution := false,
    Test / fork := false,

    Debian / serverLoading := Some(Systemd),

    maintainer := "Security Team <devx.sec.ops@guardian.co.uk>",
    packageSummary := "Security HQ app.",
    packageDescription := """Deb for Security HQ - the Guardian's service to centralise security information for our AWS accounts.""",
    Universal / javaOptions ++= Seq(
      "-Dpidfile.path=/dev/null",
      "-Dconfig.file=/etc/gu/security-hq.conf",
      "-J-XX:+UseCompressedOops",
      "-J-XX:NativeMemoryTracking=detail",
      "-J-XX:MaxRAMPercentage=50",
      "-J-XX:InitialRAMPercentage=50",
      "-J-XX:MaxMetaspaceSize=300m",
      "-J-Xlog:gc*",
      s"-J-Xlog:gc:/var/log/${packageName.value}/gc.log"
    ),
    mergeStrategySettings

  )


// exclude this key from the linting (unused keys) as it is incorrectly flagged
Global / excludeLintKeys += Universal / topLevelDirectory

lazy val root = (project in file(".")).
  aggregate(hq).
  settings(
    name := """security-hq"""
  )

addCommandAlias("dependency-tree", "dependencyTree")
