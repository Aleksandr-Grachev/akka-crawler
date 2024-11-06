import sbt._
import sbt.librarymanagement.DependencyBuilders

object Dependency {

  object pekko {
    val V = "1.1.0"

    val pekkoTypedDeps =
      Seq(
        "org.apache.pekko" %% "pekko-stream"          % V,
        "org.apache.pekko" %% "pekko-stream-typed"    % V,
        "org.apache.pekko" %% "pekko-http"            % V,
        "org.apache.pekko" %% "pekko-http-spray-json" % V,
        "org.apache.pekko" %% "pekko-actor-typed"     % V
      )

    val pekkoTestKitDeps =
      Seq(
        // "org.apache.pekko" %% "pekko-stream-testkit" % V % Test,
        // "org.apache.pekko" %% "pekko-http-testkit"   % V % Test
        "org.apache.pekko" %% "pekko-actor-testkit-typed" % V % Test
      )
  }

  lazy val loggingDeps = {
    val V = "1.5.12"
    Seq(
      "ch.qos.logback" % "logback-classic" % V
    )
  }

  lazy val pureConfigDeps = {
    val V = "0.17.7"
    Seq(
      "com.github.pureconfig" %% "pureconfig" % V
    )
  }

  lazy val mockServerDeps = {
    val V = "5.15.0"
    Seq(
      "org.mock-server" % "mockserver-netty" % V % Test
    )
  }

  lazy val scalaTestDeps = {
    val V = "3.2.19"
    Seq(
      "org.scalactic" %% "scalactic" % V % Test,
      "org.scalatest" %% "scalatest" % V % Test
    )

  }

}
