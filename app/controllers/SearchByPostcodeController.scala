package controllers

import actions.AuthAction
import cats.data.OptionT
import cats.implicits.*
import connectors.PostcodesIOConnector
import models.forms.{FuelTypeForm, PostcodeForm, RadiusForm}
import models.journeyCache.UserAnswersKey.{ChooseFuelTypeQuestion, ChoosePostcodeQuestion, ChooseRadiusQuestion}
import play.api.i18n.I18nSupport
import play.api.mvc.*
import repositories.MariadbJourneyCacheRepository
import views.html.search.{CheckYourAnswersView, InputFuelTypeView, InputPostcodeView, InputRadiusView}
import models.forms.extensions.FillFormExtension.filledWith
import models.journeyCache.JourneyId.SearByPostcode
import models.FuelPriceForStation
import play.api.data.Form
import queries.GetSqlQueries
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import utils.GeoBoundingBox
import net.sf.geographiclib.Geodesic
import views.html.TableView

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByPostcodeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                authAction: AuthAction,
                                journeyCacheRepository: MariadbJourneyCacheRepository,
                                postcodesIOConnector: PostcodesIOConnector,
                                getSqlQueries: GetSqlQueries,
                                inputPostcodeView: InputPostcodeView,
                                inputFuelTypeView: InputFuelTypeView,
                                inputRadiusView: InputRadiusView,
                                checkYourAnswersView: CheckYourAnswersView,
                                tableView: TableView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport {

  def showPostcodeForm: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
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
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    journeyCacheRepository.get.flatMap {
      case None => Future.successful(Redirect(controllers.routes.SearchByPostcodeController.showPostcodeForm()))
      case Some(cache) =>
        (for {
          postcode <- OptionT.fromOption[Future](cache.getOptionalItem(ChoosePostcodeQuestion))
          fuelType <- OptionT.fromOption[Future](cache.getOptionalItem(ChooseFuelTypeQuestion))
          radius <- OptionT.fromOption[Future](cache.getOptionalItem(ChooseRadiusQuestion))
          coordinates <- postcodesIOConnector.getCoordinates(postcode.postcode).toOption
          boundingBox <- OptionT.some[Future](GeoBoundingBox.fromRadius(coordinates._1, coordinates._2, radius.radiusInMiles * 1.60934))
          fuelStationsCandidates <- OptionT.liftF(getSqlQueries.getFuelStations(boundingBox))
          fuelStations = fuelStationsCandidates.filter { station =>
            Geodesic.WGS84.Inverse(coordinates._1, coordinates._2, station.location.latitude, station.location.longitude).s12 <= radius.radiusInMiles * 1.60934 * 1000.0
          }
          fuelPrices <- OptionT.liftF(fuelStations.traverse(station => getSqlQueries.findPricesForStation(station.nodeId).map(station -> _)).map(_.toMap))
        } yield {
          val fuelPriceForStations = fuelPrices.map { case (station, prices) =>
              FuelPriceForStation(station.nodeId, None, station.tradingName, prices.filter(price => s"${price.fuelType}" == s"${fuelType.fuelType}").sortBy(_.priceLastUpdated.toEpochMilli).takeRight(1))
          }
            .toSeq
            .filter(_.fuelPrices.nonEmpty)
            .sortBy(_.fuelPrices.take(1).headOption.map(_.price).getOrElse(0.0))
          Ok(tableView(fuelPriceForStations, postcode.postcode))
        }).getOrElse {
          InternalServerError("Something gone wrong")
        }
    }
  }
}

