package models.journeyCache

import org.scalatest.matchers.should.Matchers.*
import testUtils.BaseSpec

import scala.compiletime.testing.{Error, typeCheckErrors}

class UserAnswersTypeSafetySpec extends BaseSpec {

  "upsert rejects wrong type using scala.compileTime" in {
    val errors: List[Error] = typeCheckErrors("""
        import models.forms.{PostcodeForm, RadiusForm}
        import models.journeyCache.UserAnswersKey.ChoosePostcodeQuestion
        import repositories.MariadbJourneyCacheRepository
        import models.journeyCache.{UserAnswersItem, UserAnswersKey}

        val repo: MariadbJourneyCacheRepository = ???
        repo.upsert(ChoosePostcodeQuestion, RadiusForm(0.0))(???, ???)
      """)

    withClue(errors.mkString("\n")) {
      assert(errors.exists(_.message.contains("Found:    models.forms.RadiusForm")))
      assert(errors.exists(_.message.contains("Required: models.forms.PostcodeForm")))
    }
  }

  "upsert rejects wrong type using scalatest" in {
    """
        import models.forms.{PostcodeForm, RadiusForm}
        import models.journeyCache.UserAnswersKey.ChoosePostcodeQuestion
        import repositories.MariadbJourneyCacheRepository
        import models.journeyCache.{UserAnswersItem, UserAnswersKey}

        val repo: MariadbJourneyCacheRepository = ???
        repo.upsert(ChoosePostcodeQuestion, RadiusForm(0.0))(???, ???)
      """ shouldNot typeCheck
  }
}
