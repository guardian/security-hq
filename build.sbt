import com.gu.riffraff.artifact.RiffRaffArtifact
import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import play.sbt.PlayImport.PlayKeys._

// common settings (apply to all projects)
organization in ThisBuild := "com.gu"
version in ThisBuild := "0.0.1"
scalaVersion in ThisBuild := "2.12.3"
scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-unchecked", "-target:jvm-1.8", "-Xfatal-warnings")

// resolvers += "guardian-bintray" at "https://dl.bintray.com/guardian/sbt-plugins/"
resolvers += DefaultMavenRepository

val awsSdkVersion = "1.11.296"
val playVersion = "2.6.7"

lazy val hq = (project in file("hq"))
  .enablePlugins(PlayScala, RiffRaffArtifact, UniversalPlugin, SbtWeb)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    name := """security-hq""",
    playDefaultPort := 9090,
    libraryDependencies ++= Seq(
      ws,
      filters,
      "com.gu" %% "play-googleauth" % "0.7.2",
      "joda-time" % "joda-time" % "2.9.9",
      "org.typelevel" %% "cats-core" % "1.0.1",
      "com.github.tototoshi" %% "scala-csv" % "1.3.5",
      "com.gu" %% "configraun" % "0.3",
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-support" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudformation" % awsSdkVersion,
      "com.vladsch.flexmark" % "flexmark-all" % "0.28.20",
      "io.reactivex" %% "rxscala" % "0.26.5",
      "com.gu" %% "box" % "0.1.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.11",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.6" % Test
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
    riffRaffPackageType := (packageZipTarball in Universal).value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffAddManifestDir := Option("hq/public"),
    riffRaffArtifactResources += (file("cloudformation/security-hq.template.yaml"), s"${name.value}-cfn/cfn.yaml")
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
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % awsSdkVersion,
      "com.typesafe.play" %% "play-json" % playVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "ch.qos.logback" %  "logback-classic" % "1.2.3",
      "com.amazonaws" % "aws-java-sdk-config" % "1.11.246",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.11"
    )
  )


lazy val root = (project in file(".")).
  aggregate(hq, lambdaCommon).
  settings(
    name := """security-hq"""
  )

addCommandAlias("dependency-tree", "dependencyTree")
