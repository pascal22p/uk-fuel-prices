package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject() (configuration: Configuration) {
  private def baseUrl(serviceName: String): String = {
    val servicesRoot = "microservice.services"
    val protocol = configuration.getOptional[String](s"$servicesRoot.$serviceName.protocol").getOrElse("http")
    val host = configuration.get[String](s"$servicesRoot.$serviceName.host")
    val port = configuration.get[Int](s"$servicesRoot.$serviceName.port")
    s"$protocol://$host:$port"
  }

  val clientId: String     = configuration.get[String]("microservice.services.fuel-finder.client-id")
  val clientSecret: String = configuration.get[String]("microservice.services.fuel-finder.client-secret")

  val fuelApiHost: String = baseUrl("fuel-finder")
}
