package app.model

package object config {
  import scala.concurrent.duration.FiniteDuration

  final case class AppConf(
      httpPort:             Int,
      stashSize:            Int,
      workerPoolSize:       Int,
      threadPoolSize:       Option[Int],
      askTimeout:           FiniteDuration,
      followLocation:       Boolean,
      followLocationMaxHop: Int
  )

}
