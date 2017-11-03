// common settings (apply to all projects)
organization in ThisBuild := "com.gu"
version in ThisBuild := "0.0.1"
scalaVersion in ThisBuild := "2.11.8"
scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-unchecked", "-target:jvm-1.8", "-Xfatal-warnings")

val awsSdkVersion = "1.11.185"
val playVersion = "2.5.16"

lazy val hq = (project in file("hq")).
  enablePlugins(PlayScala).
  settings(
    name := """security-hq""",
    libraryDependencies ++= Seq(
      ws,
      filters,
      "com.gu" %% "play-googleauth" % "0.6.0",
      "joda-time" % "joda-time" % "2.9.9",
      "org.typelevel" %% "cats" % "0.8.1",
      "com.github.tototoshi" %% "scala-csv" % "1.3.5",
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-support" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.6" % Test
    ),
    // exclude docs
    sources in (Compile,doc) := Seq.empty,
    packageName in Universal := "security-hq",
    // include beanstalk config files in the zip produced by `dist`
    mappings in Universal ++=
      (baseDirectory.value / "beanstalk" * "*" get)
        .map(f => f -> s"beanstalk/${f.getName}"),
    parallelExecution in Test := false,
    fork in Test := false
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
      "ch.qos.logback" %  "logback-classic" % "1.1.7"
    )
  )


lazy val root = (project in file(".")).
  aggregate(hq, lambdaCommon).
  settings(
    name := """security-hq"""
  )
