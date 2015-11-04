package lib

import com.madgag.github.Implicits._
import lib.Bot.neoGitHub
import lib.scalagithub.Team
import org.kohsuke.github.GHMyself

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Permissions {

  def privateRepoTeams(): Future[Set[Team]] =
    Future.traverse(Bot.teamsAllowedToCreatePrivateRepos)(neoGitHub.getTeam(_).map(_.result))

  def allowedToCreatePrivateRepos(user: GHMyself): Future[Boolean] = {
    import Bot.teamsAllowedToCreatePrivateRepos

    for (membershipResponses <- Future.traverse(teamsAllowedToCreatePrivateRepos)(teamId => neoGitHub.getTeamMembership(teamId, user.getLogin).trying)) yield {
      membershipResponses.exists(_.map(_.result.state == "active").getOrElse(false))
    }
  }
}
