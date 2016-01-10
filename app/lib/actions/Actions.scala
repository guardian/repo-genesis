package lib.actions

import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken
import com.madgag.playgithub.auth.{Client, GHRequest}
import controllers.Application._
import controllers.{Auth, routes}
import lib._
import play.api.mvc.{ActionFilter, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalax.file.ImplicitConversions._

object Actions {
  private val authScopes = Seq("read:org")

  implicit val authClient: Client = Auth.authClient

  implicit val provider = AccessToken.FromSession

  val GitHubAuthenticatedAction = com.madgag.playgithub.auth.Actions.gitHubAction(authScopes, Bot.workingDir.toPath)

  val OrganisationMembershipFilter = new ActionFilter[GHRequest] {
    override protected def filter[A](req: GHRequest[A]): Future[Option[Result]] = {
      for {
        user <- req.userF
        isOrgMember <- Bot.github.checkMembership(Bot.org, user.login)
      } yield {
        println(s"******* ${user.atLogin} ${Bot.org} $isOrgMember")
        if (isOrgMember) None else Some(
          Redirect(routes.Application.about).flashing("message" -> s"You're not a member of @${Bot.org}")
        )
      }
    }
  }

  val OrgAuthenticated = GitHubAuthenticatedAction andThen OrganisationMembershipFilter
}