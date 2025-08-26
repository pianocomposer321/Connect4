val scala3Version = "3.7.2"

reStart / mainClass := Some("server.Server")

lazy val root = project
  .in(file("."))
  .settings(
    name := "connect4-server",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "com.lihaoyi" %% "cask" % "0.10.2",
    libraryDependencies += "com.lihaoyi" %% "upickle" % "4.1.0"
  )
