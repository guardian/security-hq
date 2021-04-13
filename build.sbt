import com.gu.riffraff.artifact.RiffRaffArtifact
import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import play.sbt.PlayImport.PlayKeys._
import sbt.Keys.libraryDependencies

// common settings (apply to all projects)
organization in ThisBuild := "com.gu"
version in ThisBuild := "0.2.0"
scalaVersion in ThisBuild := "2.12.10"
scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-unchecked", "-target:jvm-1.8", "-Xfatal-warnings")

// resolvers += "guardian-bintray" at "https://dl.bintray.com/guardian/sbt-plugins/"
resolvers += DefaultMavenRepository

val awsSdkVersion = "1.11.596"
val playVersion = "2.8.1"
val jacksonVersion = "2.10.1"

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
      "com.gu" %% "play-googleauth" % "0.7.6",
      "joda-time" % "joda-time" % "2.10.5",
      "org.typelevel" %% "cats-core" % "2.0.0",
      "com.github.tototoshi" %% "scala-csv" % "1.3.5",
      "com.gu" %% "configraun" % "0.3",
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-support" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudformation" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-efs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
      "com.vladsch.flexmark" % "flexmark" % "0.62.2",
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "io.reactivex" %% "rxscala" % "0.26.5",
      "com.gu" %% "box" % "0.1.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.google.cloud" % "google-cloud-securitycenter" % "1.3.6",
      "org.quartz-scheduler" % "quartz" % "2.3.2",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.6" % Test,
      "com.gu" % "anghammarad-client_2.12" % "1.1.2",

      // logstash-logback-encoder brings in version 2.11.0
      // exclude transitive dependency to avoid a runtime exception:
      // `com.fasterxml.jackson.databind.JsonMappingException: Scala module 2.10.2 requires Jackson Databind version >= 2.10.0 and < 2.11.0`
      "net.logstash.logback" % "logstash-logback-encoder" % "6.4" exclude("com.fasterxml.jackson.core", "jackson-databind"),
      "com.gu" % "kinesis-logback-appender" % "1.4.4",
    ),
    pipelineStages in Assets := Seq(digest),
    // exclude docs
    sources in (Compile,doc) := Seq.empty,
    packageName in Universal := "security-hq",
    // include beanstalk config files in the zip produced by `dist`
    mappings in Universal ++=
      (baseDirectory.value / "beanstalk" * "*" get)
        .map(f => f -> s"beanstalk/${f.getName}"),
    // include upstart config files in the zip produced by `dist`
    mappings in Universal ++=
      (baseDirectory.value / "upstart" * "*" get)
        .map(f => f -> s"upstart/${f.getName}"),
    // include systemd config files in the zip produced by `dist`
    mappings in Universal ++=
      (baseDirectory.value / "systemd" * "*" get)
        .map(f => f -> s"systemd/${f.getName}"),
    unmanagedResourceDirectories in Compile += baseDirectory.value / "markdown",
    parallelExecution in Test := false,
    fork in Test := false,
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffAddManifestDir := Option("hq/public"),
    riffRaffArtifactResources  := Seq(
      riffRaffPackageType.value -> s"${name.value}/${name.value}.deb",
      baseDirectory.value / "conf" / "riff-raff.yaml" -> "riff-raff.yaml",
      file("cloudformation/security-hq.template.yaml") -> s"${name.value}-cfn/cfn.yaml"
    ),
    javaOptions in Universal ++= Seq(
      "-Dpidfile.path=/dev/null",
      "-Dconfig.file=/etc/gu/security-hq.conf",
      "-J-XX:+UseCompressedOops",
      "-J-XX:+UseConcMarkSweepGC",
      "-J-XX:NativeMemoryTracking=detail",
      "-J-XX:MaxRAMFraction=2",
      "-J-XX:InitialRAMFraction=2",
      "-XX:NewRatio=3",
      "-J-XX:MaxMetaspaceSize=300m",
      "-J-XX:+PrintGCDetails",
      "-J-XX:+PrintGCDateStamps",
      s"-J-Xloggc:/var/log/${packageName.value}/gc.log"
    )

  )

// More will go here!

lazy val commonLambdaSettings = Seq(
  topLevelDirectory in Universal := None
)

lazy val lambdaCommon = (project in file("lambda/common")).
  settings(commonLambdaSettings: _*).
  settings(
    name := """lambda-common""",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-events" % "1.3.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-config" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-config" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      "com.typesafe.play" %% "play-json" % playVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "ch.qos.logback" %  "logback-classic" % "1.2.3",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
    )
  )

lazy val lambdaSecurityGroups = (project in file("lambda/security-groups")).
  settings(commonLambdaSettings: _*).
  dependsOn(lambdaCommon % "compile->compile;test->test").
  settings(
    name := """securitygroups-lambda""",
    assemblyJarName in assembly := s"${name.value}-${version.value}.jar",
    libraryDependencies ++= Seq(
      "com.gu" % "anghammarad-client_2.12" % "1.1.0"
    )
)

lazy val root = (project in file(".")).
  aggregate(hq, lambdaCommon, lambdaSecurityGroups).
  settings(
    name := """security-hq"""
  )

addCommandAlias("dependency-tree", "dependencyTree")


