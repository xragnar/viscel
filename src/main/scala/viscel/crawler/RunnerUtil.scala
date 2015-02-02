package viscel.crawler

import java.net.URL
import java.security.MessageDigest

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spray.client.pipelining.{Get, SendReceive, WithTransformation, WithTransformerConcatenation, addHeader, decode}
import spray.http.HttpHeaders.{Location, `Accept-Encoding`, `Content-Type`}
import spray.http.{HttpCharsets, HttpEncodings, HttpRequest, HttpResponse, Uri}
import spray.httpx.encoding.{Gzip, Deflate}
import viscel.shared.{Story, ViscelUrl}
import viscel.store.BlobStore
import viscel.{Deeds, Log}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.Predef.identity


object RunnerUtil {

	def urlToUri(vurl: ViscelUrl): Uri = {
		val in = new URL(vurl.self)
		implicit class X(s: String) {def ? = Option(s).getOrElse("") }
		Uri.from(
			scheme = in.getProtocol.?,
			userinfo = in.getUserInfo.?,
			host = in.getHost.?,
			port = if (in.getPort < 0) 0 else in.getPort,
			path = in.getPath.?,
			query = Uri.Query(Option(in.getQuery)),
			fragment = Option(in.getRef)
		)
	}

	def addReferrer(referrer: Uri): (HttpRequest) => HttpRequest = addHeader("Referer" /*[sic, http spec]*/ , referrer.toString())

	def getResponse(request: HttpRequest, iopipe: SendReceive): Future[HttpResponse] = {
		val result = request ~> addHeader(`Accept-Encoding`(HttpEncodings.deflate, HttpEncodings.gzip)) ~> iopipe
		Log.info(s"get ${ request.uri } (${ request.headers })")
		result.andThen(PartialFunction(Deeds.responses.apply)).map { decode(Gzip) ~> decode(Deflate) }
	}

	def request[R](source: ViscelUrl, origin: Option[ViscelUrl] = None): HttpRequest = {
		Get(urlToUri(source)) ~> origin.fold[HttpRequest => HttpRequest](identity)(origin => addReferrer(urlToUri(origin)))
	}

	def parseDocument(absUri: ViscelUrl)(res: HttpResponse): Document = Jsoup.parse(
		res.entity.asString(defaultCharset = HttpCharsets.`UTF-8`),
		res.header[Location].fold(ifEmpty = urlToUri(absUri))(_.uri).toString())

	def parseBlob[R](res: HttpResponse): Story.Blob = {
		val bytes = res.entity.data.toByteArray
		val sha1 = BlobStore.write(bytes)
		Story.Blob(
			sha1 = sha1,
			mediatype = res.header[`Content-Type`].fold("")(_.contentType.mediaType.toString()))
	}

}
