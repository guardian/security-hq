import com.gu.riffraff.artifact.RiffRaffArtifact
import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import com.typesafe.sbt.packager.archetypes.systemloader.ServerLoader.Systemd
import play.sbt.PlayImport.PlayKeys._
import sbt.Keys.libraryDependencies

import scala.concurrent.duration.DurationInt

// common settings (apply to all projects)
ThisBuild / organization := "com.gu"
ThisBuild / version := "0.2.0"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xfatal-warnings")

resolvers += DefaultMavenRepository

val awsSdkVersion = "1.12.368"
val playJsonVersion = "2.9.3"
val jacksonVersion = "2.13.4"

// Until all dependencies are on scala-java8-compat v1.x, this avoids unnecessary fatal eviction errors
// See https://github.com/akka/akka/pull/30375
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always

lazy val hq = (project in file("hq"))
  .enablePlugins(PlayScala, RiffRaffArtifact, SbtWeb, JDebPackaging, SystemdPlugin)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := """security-hq""",
    playDefaultPort := 9090,
    fileDescriptorLimit := Some("16384"), // This increases the number of open files allowed when running in AWS
    libraryDependencies ++= Seq(
      ws,
      filters,
      "com.gu.play-googleauth" %% "play-v28" % "2.2.6",
      "com.gu.play-secret-rotation" %% "play-v28" % "0.36",
      "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v1" % "0.36",
      "joda-time" % "joda-time" % "2.11.2",
      "org.typelevel" %% "cats-core" % "2.8.0",
      "com.github.tototoshi" %% "scala-csv" % "1.3.10",
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-support" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudformation" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-efs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "com.vladsch.flexmark" % "flexmark" % "0.64.0",
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion,
      "io.reactivex" %% "rxscala" % "0.27.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
      "com.google.cloud" % "google-cloud-securitycenter" % "2.10.0",
      "org.scalatest" %% "scalatest" % "3.2.14" % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % Test,
      "org.scalacheck" %% "scalacheck" % "1.17.0" % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.15" % "1.3.0" % Test,
      "com.gu" %% "anghammarad-client" % "1.2.0",

      // logstash-logback-encoder brings in version 2.11.0
      // exclude transitive dependency to avoid a runtime exception:
      // `com.fasterxml.jackson.databind.JsonMappingException: Scala module 2.10.2 requires Jackson Databind version >= 2.10.0 and < 2.11.0`
      "net.logstash.logback" % "logstash-logback-encoder" % "7.2" exclude("com.fasterxml.jackson.core", "jackson-databind"),
      "com.gu" % "kinesis-logback-appender" % "2.1.0",
      "com.gu" %% "janus-config-tools" % "0.0.5"
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
    debianPackageDependencies := Seq("java-11-amazon-corretto-jdk:arm64"),
    maintainer := "Security Team <devx.sec.ops@guardian.co.uk>",
    packageSummary := "Security HQ app.",
    packageDescription := """Deb for Security HQ - the Guardian's service to centralise security information for our AWS accounts.""",
    riffRaffPackageType := (Debian / packageBin).value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),

    riffRaffAddManifestDir := Option("hq/public"),
    riffRaffArtifactResources  := Seq(
      riffRaffPackageType.value -> s"${name.value}/${name.value}.deb",
      baseDirectory.value / "conf" / "riff-raff.yaml" -> "riff-raff.yaml",
      file("cdk/cdk.out/security-hq.template.json") -> s"${name.value}-cfn/cfn.json"
    ),

    Universal / javaOptions ++= Seq(
      "-Dpidfile.path=/dev/null",
      "-Dconfig.file=/etc/gu/security-hq.conf",
      "-J-XX:+UseCompressedOops",
      "-J-XX:+UseConcMarkSweepGC",
      "-J-XX:NativeMemoryTracking=detail",
      "-J-XX:MaxRAMPercentage=50",
      "-J-XX:InitialRAMPercentage=50",
      "-XX:NewRatio=3",
      "-J-XX:MaxMetaspaceSize=300m",
      "-J-Xlog:gc*",
      s"-J-Xlog:gc:/var/log/${packageName.value}/gc.log"
    )

  )

// More will go here!

lazy val commonLambdaSettings = Seq(
  Universal / topLevelDirectory := None
)
// exclude this key from the linting (unused keys) as it is incorrectly flagged
Global / excludeLintKeys += Universal / topLevelDirectory

lazy val lambdaCommon = (project in file("lambda/common")).
  settings(commonLambdaSettings: _*).
  settings(
    name := """lambda-common""",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-config" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-config" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "org.scalatest" %% "scalatest" % "3.2.14" % Test,
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
    )
  )

lazy val lambdaSecurityGroups = (project in file("lambda/security-groups")).
  settings(commonLambdaSettings: _*).
  dependsOn(lambdaCommon % "compile->compile;test->test").
  settings(
    name := """securitygroups-lambda""",
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",
    libraryDependencies ++= Seq(
      "com.gu" %% "anghammarad-client" % "1.2.0"
    )
)

lazy val root = (project in file(".")).
  aggregate(hq, lambdaCommon, lambdaSecurityGroups).
  settings(
    name := """security-hq"""
  )

addCommandAlias("dependency-tree", "dependencyTree")
