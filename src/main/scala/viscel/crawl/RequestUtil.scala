package viscel.crawl

import java.time.Instant

import akka.http.javadsl.model.headers.LastModified
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.{Deflate, Gzip}
import akka.http.scaladsl.model.headers.{HttpEncodings, Location, Referer, `Accept-Encoding`}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import viscel.scribe.{BlobData, Vurl}
import viscel.shared.{Blob, Log}
import viscel.store.BlobStore

import scala.async.Async.{async, await}
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}


class RequestUtil(blobs: BlobStore, ioHttp: HttpExt)(implicit val ec: ExecutionContext, materializer: Materializer) {

	val timeout = FiniteDuration(300, SECONDS)

	private def decompress(r: HttpResponse): Future[HttpResponse] =
		Deflate.decodeMessage(Gzip.decodeMessage(r)).toStrict(timeout)
	private def requestDecompressed(request: HttpRequest): Future[HttpResponse] =
		ioHttp.singleRequest(request.withHeaders(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)))
			.flatMap(decompress)

	def extractResponseLocation(base: Vurl, httpResponse: HttpResponse): Vurl =
		httpResponse.header[Location].fold(base)(l => Vurl.fromUri(l.uri.resolvedAgainst(base.uri)))
	def extractLastModified(httpResponse: HttpResponse): Option[Instant] =
		httpResponse.header[LastModified].map(h => Instant.ofEpochMilli(h.date().clicks()))


	private def requestWithRedirects(request: HttpRequest, redirects: Int = 10): Future[HttpResponse] = {
		Log.info(s"request ${request.uri}" + request.header[Referer].fold("")(r => s" ($r)"))
		async {
			val response = await(requestDecompressed(request))

			if (response.status.isRedirection() && response.header[Location].isDefined) {
				val loc = response.header[Location].get.uri.resolvedAgainst(request.uri)
				await {requestWithRedirects(request.withUri(loc), redirects = redirects - 1)}
			}
			else if (response.status.isSuccess()) {
				// if the response has no location header, we insert the url from the request as a location,
				// this allows all other systems to use the most accurate location available
				response.addHeader(Location.apply(extractResponseLocation(Vurl.fromUri(request.uri), response).uri))
			}
			else throw RequestException(request, response)
		}
	}


	def request[R](source: Vurl, origin: Option[Vurl] = None): Future[HttpResponse] = {
		val req = HttpRequest(
			method = HttpMethods.GET,
			uri = source.uri,
			headers = origin.map(x => Referer.apply(x.uri)).toList)
		requestWithRedirects(req)
	}

	def requestDocument(source: Vurl): Future[(Document, HttpResponse)] =
		for {
			resp <- request(source)
			html <- Unmarshal(resp).to[String]
			doc = Jsoup.parse(html, extractResponseLocation(source, resp).uriString())
		} yield (doc, resp)

	def requestBlob[R](source: Vurl, origin: Option[Vurl] = None): Future[BlobData] =
		for {
			res <- request(source, origin)
			entity <- res.entity.toStrict(timeout) //should be strict already, but we do not know here
			bytes = entity.data.toArray[Byte]
			sha1 = blobs.write(bytes)
		} yield
			BlobData(source, extractResponseLocation(source, res),
				blob = Blob(
					sha1 = sha1,
					mime = res.entity.contentType.mediaType.toString()),
				date = extractLastModified(res).getOrElse(Instant.now()))

}
