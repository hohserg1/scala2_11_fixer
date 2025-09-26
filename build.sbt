ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "scala2_11_fixer"
  )

libraryDependencies ++= Seq(
  "org.ow2.asm" % "asm" % "9.8",
  "org.ow2.asm" % "asm-commons" % "9.8"
  )