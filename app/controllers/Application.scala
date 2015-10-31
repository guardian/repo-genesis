package controllers

import lib.Bot
import lib.actions.Actions._
import lib.scalagithub.CreateRepo
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import scala.collection.convert.wrapAsScala._

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {

  def index = Action { implicit req =>
    Ok(views.html.index())
  }

  def newRepo = OrgAuthenticated { implicit req =>
    val allTeam = req.gitHub.getMyTeams
    val teams = allTeam.get(Bot.org).toSeq.sortBy(_.getMembers.size)
    Ok(views.html.createNewRepo(repoCreationForm, teams))
  }

  case class RepoCreation(name: String, teamId: Long)

  val repoCreationForm = Form(mapping(
    "name" -> text(maxLength = 40),
    "teamId" -> longNumber
  )(RepoCreation.apply)(RepoCreation.unapply))

  def createPublicRepo = OrgAuthenticated.async(parse.form(repoCreationForm)) { implicit req =>
    val repoCreation = req.body
    val repo = CreateRepo(name = repoCreation.name, `private` = false)
    for {
      createdRepo <- Bot.neoGitHub.createOrgRepo(Bot.org, repo)
      _ <- Bot.neoGitHub.addTeamRepo(repoCreation.teamId, Bot.org, repoCreation.name)
    } yield Redirect(createdRepo.result.html_url + "/settings/collaboration")
  }
}
