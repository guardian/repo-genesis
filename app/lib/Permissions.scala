package lib

import com.madgag.github.Implicits._
import org.kohsuke.github.GHMyself

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Permissions {

  def allowedToCreatePrivateRepos(user: GHMyself): Future[Boolean] = {
    import Bot.teamsAllowedToCreatePrivateRepos
    println(s"user = ${user.atLogin}")

    println(s"teamsAllowedToCreatePrivateRepos = $teamsAllowedToCreatePrivateRepos")

    println(s"team ids = ${teamsAllowedToCreatePrivateRepos.map(_.getId)}")
    for (membershipResponses <- Future.traverse(teamsAllowedToCreatePrivateRepos)(t => Bot.neoGitHub.getTeamMembership(t.getId, user.getLogin).trying)) yield {
      membershipResponses.exists(_.map(_.result.state == "active").getOrElse(false))
    }
  }
}
