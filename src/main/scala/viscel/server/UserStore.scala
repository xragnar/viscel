package viscel.server

import org.scalactic.TypeCheckedTripleEquals._
import org.scalactic.{Bad, Good}
import spray.routing.authentication.{BasicAuth, UserPass, UserPassAuthenticator}
import viscel.Log
import viscel.store.{Users, User}

import scala.Predef.ArrowAssoc
import scala.collection.immutable.Map
import scala.concurrent.{ExecutionContext, Future}

class UserStore(implicit ec: ExecutionContext) {
	var userCache = Map[String, User]()

	def userUpdate(user: User): User = {
		userCache += user.id -> user
		Users.store(user)
		user
	}

	def getUserNode(name: String, password: String): User =
		userCache.getOrElse(name,
			userUpdate(Users.load(name) match {
				case Good(g) => g
				case Bad(e) =>
					Log.warn(s"could not open user $name: $e")
					val firstUser = Users.all().fold(_.isEmpty, _ => false)
					User(name, password, isAdmin = firstUser, Map())
			}))

	val loginOrCreate = BasicAuth(UserPassAuthenticator[User] {
		case Some(UserPass(user, password)) =>
			Log.trace(s"login: $user $password")
			// time("login") {
			if (user.matches("\\w+")) {
				Future.successful { Some(getUserNode(user, password)).filter(_.password === password) }
			}
			else { Future.successful(None) }
		// }
		case None =>
			Future.successful(None)
	}, "Username is used to store configuration; Passwords are saved in plain text; User is created on first login")
}
