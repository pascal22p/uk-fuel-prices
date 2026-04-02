package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject() (configuration: Configuration) {
  val tokenUrl: String = configuration.get[String]("fuel.oauth.token-url")
  val clientId: String     = configuration.get[String]("fuel.oauth.client-id")
  val clientSecret: String = configuration.get[String]("fuel.oauth.client-secret")
  
}
