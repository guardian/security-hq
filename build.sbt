// common settings (apply to all projects)
organization in ThisBuild := "com.gu"
version in ThisBuild := "0.0.1"
scalaVersion in ThisBuild := "2.11.8"
scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-unchecked", "-target:jvm-1.8", "-Xfatal-warnings")

// common dependencies
libraryDependencies in ThisBuild ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

val awsSdkVersion = "1.11.77"
val playVersion = "2.5.12"

lazy val hq = (project in file("hq")).
  enablePlugins(PlayScala).
  settings(
    name := """security-hq""",
    libraryDependencies ++= Seq(
      ws,
      filters,
      "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
    ),
    // exclude docs
    sources in (Compile,doc) := Seq.empty,
    packageName in Universal := "security-hq",
    // include beanstalk config files in the zip produced by `dist`
    mappings in Universal ++=
      (baseDirectory.value / "beanstalk" * "*" get)
        .map(f => f -> s"beanstalk/${f.getName}")
  )

// More will go here!

lazy val root = (project in file(".")).
  aggregate(hq).
  settings(
    name := """security-hq"""
  )
