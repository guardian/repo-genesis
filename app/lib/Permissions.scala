package lib

import com.madgag.playgithub.auth.GHRequest
import com.madgag.scalagithub.model.Team

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Permissions(bot: Bot) {

  def privateRepoTeams(): Future[Set[Team]] =
    Future.traverse(bot.teamsAllowedToCreatePrivateRepos)(bot.github.getTeam(_).map(_.result))

  def allowedToCreatePrivateRepos(req: GHRequest[_]): Future[Boolean] = for (userTeams <- req.userTeamsF) yield {
    userTeams.exists(team => bot.teamsAllowedToCreatePrivateRepos(team.id))
  }
}
