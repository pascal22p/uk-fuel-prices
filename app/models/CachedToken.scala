package models

final case class CachedToken(
                        accessToken: String,
                        expiresAt: Long // epoch millis
                      )