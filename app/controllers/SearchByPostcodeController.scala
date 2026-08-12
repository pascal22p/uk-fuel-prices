package controllers

import actions.AuthAction
import cats.implicits.*
import models.forms.{FuelTypeForm, PostcodeForm, RadiusForm}
import models.journeyCache.UserAnswersKey.{ChooseFuelTypeQuestion, ChoosePostcodeQuestion, ChooseRadiusQuestion}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Result}
import repositories.MariadbJourneyCacheRepository
import views.html.search.{CheckYourAnswersView, InputFuelTypeView, InputPostcodeView, InputRadiusView, SearchStationsView}
import models.forms.extensions.FillFormExtension.filledWith
import models.journeyCache.JourneyId.SearByPostcode
import models.{FuelType, LoggingWithRequest}
import play.api.data.Form
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import services.SearchByPostcodeService

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByPostcodeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                authAction: AuthAction,
                                searchByPostcodeService: SearchByPostcodeService,
                                journeyCacheRepository: MariadbJourneyCacheRepository,
                                inputPostcodeView: InputPostcodeView,
                                inputFuelTypeView: InputFuelTypeView,
                                inputRadiusView: InputRadiusView,
                                checkYourAnswersView: CheckYourAnswersView,
                                searchStationsView: SearchStationsView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport with LoggingWithRequest {

  def showPostcodeForm: Action[AnyContent] = Action.async { implicit authenticatedRequest =>
    journeyCacheRepository.get(ChoosePostcodeQuestion).map { defaults =>
      val form = PostcodeForm.postcodeForm.filledWith(defaults)
      Ok(inputPostcodeView(form))
    }
  }

  def showPostcodeOnSubmit: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val errorFunction: Form[PostcodeForm] => Future[Result] = {
      (formWithErrors: Form[PostcodeForm]) =>
        Future.successful(BadRequest(inputPostcodeView(formWithErrors)))
    }

    val successFunction: PostcodeForm => Future[Result] = { (dataForm: PostcodeForm) =>
      journeyCacheRepository.upsert(ChoosePostcodeQuestion, dataForm).map { _ =>
        Redirect(controllers.routes.SearchByPostcodeController.showFuelTypeForm())
      }
    }

    val formValidationResult = PostcodeForm.postcodeForm.bindFromRequest()
    formValidationResult.fold(errorFunction, successFunction)
  }

  def showFuelTypeForm: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    journeyCacheRepository.get(ChooseFuelTypeQuestion).map { defaults =>
      val form = FuelTypeForm.fuelTypeForm.filledWith(defaults)
      Ok(inputFuelTypeView(form))
    }
  }

  def showFuelTypeOnSubmit: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val errorFunction: Form[FuelTypeForm] => Future[Result] = {
      (formWithErrors: Form[FuelTypeForm]) =>
        Future.successful(BadRequest(inputFuelTypeView(formWithErrors)))
    }

    val successFunction: FuelTypeForm => Future[Result] = { (dataForm: FuelTypeForm) =>
      journeyCacheRepository.upsert(ChooseFuelTypeQuestion, dataForm).map { _ =>
        Redirect(controllers.routes.SearchByPostcodeController.showRadiusForm())
      }
    }

    val formValidationResult = FuelTypeForm.fuelTypeForm.bindFromRequest()
    formValidationResult.fold(errorFunction, successFunction)
  }

  def showRadiusForm: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    journeyCacheRepository.get(ChooseRadiusQuestion).map { defaults =>
      val form = RadiusForm.radiusForm.filledWith(defaults)
      Ok(inputRadiusView(form))
    }
  }

  def showRadiusOnSubmit: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val errorFunction: Form[RadiusForm] => Future[Result] = {
      (formWithErrors: Form[RadiusForm]) =>
        Future.successful(BadRequest(inputRadiusView(formWithErrors)))
    }

    val successFunction: RadiusForm => Future[Result] = { (dataForm: RadiusForm) =>
      journeyCacheRepository.upsert(ChooseRadiusQuestion, dataForm).map { _ =>
        Redirect(controllers.routes.SearchByPostcodeController.checkYourAnswers())
      }
    }

    val formValidationResult = RadiusForm.radiusForm.bindFromRequest()
    formValidationResult.fold(errorFunction, successFunction)
  }

  def checkYourAnswers: Action[AnyContent] = authAction.async { implicit request =>
    journeyCacheRepository.get.map {
      case None => Redirect(controllers.routes.SearchByPostcodeController.showPostcodeForm())
      case Some(cache) =>
        cache
          .validated(SearByPostcode)
          .fold(
            call => Redirect(call),
            userAnswers =>
              Ok(
                checkYourAnswersView(
                  userAnswers.flattenByKey(SearByPostcode),
                  SearByPostcode,
                  controllers.routes.SearchByPostcodeController.submitCheckYourAnswers()
                )
              )
          )
    }
  }

  def submitCheckYourAnswers: Action[AnyContent] = authAction.async { implicit request =>
    journeyCacheRepository.get.map {
      case None => Redirect(controllers.routes.SearchByPostcodeController.showPostcodeForm())
      case Some(cache) =>
        val result = for {
          postcode <- cache.getOptionalItem(ChoosePostcodeQuestion)
          fuelType <- cache.getOptionalItem(ChooseFuelTypeQuestion)
          radius <- cache.getOptionalItem(ChooseRadiusQuestion)
        } yield {
          Redirect(controllers.routes.SearchByPostcodeController.showNearbyFuelStations(
            postcode.postcode,
            fuelType.fuelType,
            radius.radiusInMiles
          ))
        }
        result.getOrElse(InternalServerError("A cache entry is missing"))
    }
  }

  def showNearbyFuelStations(postcode: String, fuelType: FuelType, radius: Double): Action[AnyContent] = authAction.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    logger.debug(s"traceparent= ${request.headers.get("traceparent")}")
    
    searchByPostcodeService.getViewModel(postcode, fuelType, radius.min(100.0)).fold(
      error => error match {
        case error: UpstreamErrorResponse if error.statusCode == NOT_FOUND => NotFound("Postcode cannot be found")
        case error => InternalServerError(s"Something gone wrong. $error")
      },
      viewModel => Ok(searchStationsView(viewModel))
    )
  }


}

