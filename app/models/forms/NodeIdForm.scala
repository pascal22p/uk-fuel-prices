package models.forms

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}

import java.util.Locale

final case class NodeIdForm(postcode: String)

object NodeIdForm {
  def unapply(u: NodeIdForm): Option[(String)] = Some((u.postcode))

  private val nodeIdRegex = """^[A-Fa-f0-9]{64}$""".r

  val nodeIdForm: Form[NodeIdForm] = Form(
    mapping(
      "nodeId"  -> nonEmptyText
        .transform[String](_.toUpperCase(Locale.ENGLISH).trim, identity)
        .verifying("error.invalid.nodeId", p => nodeIdRegex.matches(p))
    )(NodeIdForm.apply)(NodeIdForm.unapply)
  )
}
