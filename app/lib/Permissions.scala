package lib

import com.madgag.playgithub.auth.GHRequest
import com.madgag.scalagithub.model.Team
import lib.Bot.github

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Permissions {

  def privateRepoTeams(): Future[Set[Team]] =
    Future.traverse(Bot.teamsAllowedToCreatePrivateRepos)(github.getTeam(_).map(_.result))

  def allowedToCreatePrivateRepos(req: GHRequest[_]): Future[Boolean] = for (userTeams <- req.userTeamsF) yield {
    userTeams.exists(team => Bot.teamsAllowedToCreatePrivateRepos(team.id))
  }
}
