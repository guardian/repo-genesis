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

  def about = Action { implicit req =>
    Ok(views.html.about())
  }

  case class RepoCreation(name: String, teamId: Long)

  val repoCreationForm = Form(mapping(
    "name" -> text(minLength = 1, maxLength = 100),
    "teamId" -> longNumber
  )(RepoCreation.apply)(RepoCreation.unapply))

  case class Team(id: Long, name: String, size: Int)

  def newRepo = OrgAuthenticated { implicit req =>
    val teams = req.gitHub.getMyTeams.get(Bot.org).map(t => Team(t.getId, t.getName, t.getMembers.size)).toSeq.sortBy(_.size)
    Ok(views.html.createNewRepo(repoCreationForm, teams))
  }

  def createRepo = OrgAuthenticated.async(parse.form(repoCreationForm)) { implicit req =>
    val repoCreation = req.body
    val repo = CreateRepo(name = repoCreation.name, `private` = false)
    for {
      createdRepo <- Bot.neoGitHub.createOrgRepo(Bot.org, repo)
      _ <- Bot.neoGitHub.addTeamRepo(repoCreation.teamId, Bot.org, repoCreation.name)
    } yield Redirect(createdRepo.result.collaborationSettingsUrl)
  }
}
