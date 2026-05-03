/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import cats.data.EitherT
import com.google.inject.Inject
import models.LoggingWithRequest
import play.api.http.Status.*
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class HttpClientResponse @Inject()(implicit ec: ExecutionContext) extends LoggingWithRequest {

  def read(
    response: Future[Either[UpstreamErrorResponse, HttpResponse]]
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    EitherT(response.map {
      case Right(success) => Right(success)
      case Left(error) if error.statusCode >= INTERNAL_SERVER_ERROR =>
        logger.warn(error.message)
        Left(error)
      case Left(error) if error.statusCode == NOT_FOUND =>
        logger.debug(error.message)
        Left(error)
      case Left(error) =>
        logger.error(error.message, error)
        Left(error)
    } recover {
      case exception: HttpException =>
        logger.warn(exception.message)
        Left(UpstreamErrorResponse(exception.message, BAD_GATEWAY, BAD_GATEWAY))
    })

}
