package app

import scopt.OParser

package object cli {
  import app.model.config._

  val builder = OParser.builder[AppConf]

  val commandLineParser: OParser[Unit, AppConf] = {
    import builder._
    OParser.sequence(
      programName("crawler-app"),
      head("crawler-app", "2.2"),
      opt[Int]("port")
        .action((port, appConfig) => appConfig.copy(httpPort = port))
        .optional()
        .text(
          s"The http port for running app"
        ),
      opt[Unit]("follow-location")
        .action((_, appConfig) => appConfig.copy(followLocation = true))
        .optional()
        .text(
          s"The crawler will attempt to follow the Location header in redirect responses"
        ),
      opt[Int]("follow-location-max-hop")
        .action((maxHop, appConfig) =>
          appConfig.copy(followLocationMaxHop = maxHop)
        )
        .optional()
        .text(
          s"Maximum number of hops for redirect responses"
        )
    )
  }

  def loadAppConfig(
      args: Array[String],
      init: AppConf
  ): Either[Exception, AppConf] =
    OParser.parse(commandLineParser, args, init).toRight {
      new Exception("An error occured while parsing command line")
    }

}
