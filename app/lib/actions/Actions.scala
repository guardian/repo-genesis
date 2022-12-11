package lib.actions

import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken
import com.madgag.playgithub.auth.GHRequest
import controllers.routes
import lib._
import play.api.mvc.Results.Redirect
import play.api.mvc.{ActionFilter, AnyContent, BodyParser, Result}

import scala.concurrent.{ExecutionContext, Future}

class Actions(
  bot: Bot,
  bodyParser: BodyParser[AnyContent]
)(implicit
  authClient: com.madgag.playgithub.auth.Client,
  ec: ExecutionContext
) {
  private val authScopes = Seq("read:org")

  implicit val provider = AccessToken.FromSession

  val GitHubAuthenticatedAction =
    com.madgag.playgithub.auth.Actions.gitHubAction(authScopes, bot.workingDir, bodyParser)

  val OrganisationMembershipFilter: ActionFilter[GHRequest] = new ActionFilter[GHRequest] {

    def executionContext = ec

    override protected def filter[A](req: GHRequest[A]): Future[Option[Result]] = for {
      user <- req.userF
      isOrgMember <- bot.github.checkMembership(bot.orgLogin, user.login)
    } yield {
      println(s"******* ${user.atLogin} ${bot.orgLogin} $isOrgMember")

      Option.when(!isOrgMember) {
        Redirect(routes.Application.about()).flashing("message" -> s"You're not a member of @${bot.orgLogin}")
      }
    }
  }

  val OrgAuthenticated = GitHubAuthenticatedAction andThen OrganisationMembershipFilter
}