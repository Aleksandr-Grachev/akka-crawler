import Dependency._

version := "2.2-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"

//main
libraryDependencies ++= pekko.pekkoTypedDeps
libraryDependencies ++= loggingDeps
libraryDependencies ++= pureConfigDeps
libraryDependencies ++= scoptDeps
//test
libraryDependencies ++= mockServerDeps
libraryDependencies ++= scalaTestDeps
libraryDependencies ++= pekko.pekkoTestKitDeps

scalacOptions ++= Seq(
  "-encoding",
  "utf8",
  "-Wunused"
)

// Assembly options for publish and publishLocal sbt tasks
enablePlugins(AssemblyPlugin)

assembly / assemblyMergeStrategy := {
  case PathList(ps @ _*) if ps.last endsWith "module-info.class" =>
    MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".conf" => MergeStrategy.concat
  case PathList(ps @ _*) if ps.last endsWith ".properties" =>
    MergeStrategy.concat
  case x => MergeStrategy.defaultMergeStrategy(x)
}

assembly / mainClass := Some("app.Main")

assembly / assemblyJarName := s"crawler-app-${version.value.takeWhile(_ != '-')}.jar"
