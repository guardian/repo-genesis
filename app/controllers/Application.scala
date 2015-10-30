package controllers

import lib.Bot
import lib.actions.Actions._
import lib.scalagithub.{CreateRepo, GitHub}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {

  def index = Action { implicit req =>
    Ok(views.html.index())
  }

  def newPublicRepo = GitHubAuthenticatedAction.async { implicit req =>
    val gitHub = new GitHub(Bot.ghCreds)

    val org = "gu-who-demo-org"
    val repoName = System.currentTimeMillis().toHexString
    val repo = CreateRepo(name = repoName, `private` = false)
    for {
      createdRepo <- gitHub.createOrgRepo(org, repo)
      _ <- gitHub.addTeamRepo(1831886, org, repoName)
    } yield Redirect(createdRepo.result.html_url + "/settings/collaboration")
  }
}
