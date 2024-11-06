import Dependency._

version := "1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"

//main
libraryDependencies ++= pekko.pekkoTypedDeps
libraryDependencies ++= loggingDeps
libraryDependencies ++= pureConfigDeps
//test
libraryDependencies ++= mockServerDeps
libraryDependencies ++= scalaTestDeps
libraryDependencies ++= pekko.pekkoTestKitDeps

scalacOptions ++= Seq(
  "-encoding",
  "utf8",
  "-Wunused"
)
