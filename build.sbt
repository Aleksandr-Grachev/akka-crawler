version := "1.0-SNAPSHOT"

lazy val akkaDeps = {
  val V = "1.1.0"
  Seq(
    "org.apache.pekko" %% "pekko-stream"      % V,
    "org.apache.pekko" %% "pekko-http"        % V,
    "org.apache.pekko" %% "pekko-actor-typed" % V
  )
}

libraryDependencies ++= akkaDeps
