package viscel.crawl

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.{Deflate, Gzip}
import akka.http.scaladsl.model.headers.{HttpEncodings, Location, Referer, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import viscel.scribe.{AppendLogBlob, BlobStore, Vurl}
import viscel.shared.{Blob, Log}

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}


class RequestUtil(blobs: BlobStore, ioHttp: HttpExt)(implicit val ec: ExecutionContext, materializer: Materializer) {

	val timeout = FiniteDuration(300, SECONDS)

	def getResponse(request: HttpRequest): Future[HttpResponse] = {
		Log.info(s"request ${request.uri} (${request.header[Referer]})")
		val result: Future[HttpResponse] = ioHttp.singleRequest(request).flatMap(_.toStrict(timeout))
		result //.andThen(PartialFunction(responseHandler))
	}

	def getResponseLocation(httpResponse: HttpResponse): Option[Vurl] = {
		httpResponse.header[Location].map(l => new Vurl(l.uri))
	}

	def request[R](source: Vurl, origin: Option[Vurl] = None): Future[HttpResponse] = {
		val req = HttpRequest(
			method = HttpMethods.GET,
			uri = source.uri,
			headers =
				`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip) ::
					origin.map(x => Referer.apply(x.uri)).toList)
		getResponse(req).map(r => Deflate.decode(Gzip.decode(r)))
	}

	def requestDocument(source: Vurl, origin: Option[Vurl] = None): Future[Document] = {
		request(source, origin).flatMap { (res: HttpResponse) =>
			Unmarshal(res).to[String].map { html =>
				Jsoup.parse(html, getResponseLocation(res).getOrElse(source.uri).toString())
			}
		}
	}


	def requestBlob[R](source: Vurl, origin: Option[Vurl] = None): Future[AppendLogBlob] = {
		request(source, origin).flatMap { res =>
			res.entity.toStrict(timeout).map { entity =>
				val bytes = entity.data.toArray[Byte]
				val sha1 = blobs.write(bytes)
				AppendLogBlob(source, getResponseLocation(res).getOrElse(source),
					Blob(
						sha1 = sha1,
						mime = res.entity.contentType.mediaType.toString()))
			}
		}
	}

}