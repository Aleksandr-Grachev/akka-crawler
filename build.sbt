version := "1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.15"


lazy val pekkoDeps = {
  val V = "1.1.0"
  Seq(
    "org.apache.pekko" %% "pekko-stream"          % V,
    "org.apache.pekko" %% "pekko-stream-typed"    % V,
    "org.apache.pekko" %% "pekko-http"            % V,
    "org.apache.pekko" %% "pekko-http-spray-json" % V,
    "org.apache.pekko" %% "pekko-actor-typed"     % V
  )
}

libraryDependencies ++= pekkoDeps

scalacOptions ++= Seq( // use ++= to add to existing options
  "-encoding",
  "utf8", // if an option takes an arg, supply it on the same line
  "-Wunused"
)
