package lib

import com.madgag.github.Implicits._
import org.kohsuke.github.GHMyself

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Permissions {

  def privateRepoTeams() = {
    Future.traverse(Bot.teamsAllowedToCreatePrivateRepos)(tid => Bot.neoGitHub.getTeam(tid).map(_.result))
  }

  def allowedToCreatePrivateRepos(user: GHMyself): Future[Boolean] = {
    import Bot.teamsAllowedToCreatePrivateRepos
    println(s"user = ${user.atLogin}")

    println(s"teamsAllowedToCreatePrivateRepos = $teamsAllowedToCreatePrivateRepos")

    for (membershipResponses <- Future.traverse(teamsAllowedToCreatePrivateRepos)(teamId => Bot.neoGitHub.getTeamMembership(teamId, user.getLogin).trying)) yield {
      membershipResponses.exists(_.map(_.result.state == "active").getOrElse(false))
    }
  }
}
