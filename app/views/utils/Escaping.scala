package views.utils

import play.api.libs.json.Json

object Escaping {

  /** Encodes a string as a JSON/JS string literal safe to embed inside an
   * inline <script> block: JSON-escapes quotes/backslashes/control chars,
   * then additionally escapes '<' and '>' as unicode escapes so the value
   * can never contain a literal tag delimiter, regardless of tag name,
   * case, or whitespace — defeating any </script>, <script>, <!-- etc.
   * breakout attempt at the HTML-parser level.
   */
  def jsStringLiteral(value: String): String = {
    Json.toJson(value).toString
      .replace("<", "\\u003C")
      .replace(">", "\\u003E")
  }
}