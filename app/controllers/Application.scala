package controllers

import com.madgag.github.Implicits._
import com.madgag.playgithub.auth.GHRequest
import com.madgag.slack.Slack.Message
import lib.Permissions.allowedToCreatePrivateRepos
import lib.actions.Actions._
import lib.scalagithub.CreateRepo
import lib.{Bot, Permissions}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc._

import scala.collection.convert.wrapAsScala._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {

  def about = Action { implicit req =>
    Ok(views.html.about())
  }

  case class RepoCreation(name: String, teamId: Long, isPrivate: Boolean)

  val repoCreationForm = Form(mapping(
    "name" -> text(minLength = 1, maxLength = 100),
    "teamId" -> longNumber,
    "private" -> boolean
  )(RepoCreation.apply)(RepoCreation.unapply))

  case class Team(id: Long, name: String, size: Int)

  def newRepo = OrgAuthenticated.async { implicit req =>
    for {
      allowPrivate <- allowedToCreatePrivateRepos(req.user)
      privateRepoTeams <- Permissions.privateRepoTeams()
    } yield {
      val userTeams = req.gitHub.getMyTeams.get(Bot.org).map(t => Team(t.getId, t.getName, t.getMembers.size)).toSeq.sortBy(_.size)
      println(s"${req.user.atLogin} userTeams : ${userTeams.mkString(",")}")
      println(s"privateRepoTeams = $privateRepoTeams")
      Ok(views.html.createNewRepo(repoCreationForm, userTeams, allowPrivate, privateRepoTeams))
    }
  }

  def createRepo = OrgAuthenticated.async(parse.form(repoCreationForm)) { implicit req =>
    for {
      allowPrivate <- allowedToCreatePrivateRepos(req.user)
      result <- barg(req, allowPrivate)
    } yield result
  }

  def barg(req: GHRequest[RepoCreation], allowPrivate: Boolean): Future[Result] = {
    val repoCreation = req.body
    if (repoCreation.isPrivate && !allowPrivate) {
      Future(Forbidden(s"${req.user.atLogin} is not currently allowed to create private repos"))
    } else {
      val command = CreateRepo(
        name = repoCreation.name,
        description = Some(s"Placeholder description: ${req.user.atLogin} created this with repo-genesis"),
        `private` = repoCreation.isPrivate)
      for {
        createdRepo <- Bot.neoGitHub.createOrgRepo(Bot.org, command)
        _ <- Bot.neoGitHub.addTeamRepo(repoCreation.teamId, Bot.org, repoCreation.name)
      } yield {
        for (slack <- Bot.slackOpt) {
          slack.send(Message(
            s"${req.user.atLogin} created ${command.publicOrPrivateString} repo ${createdRepo.result.html_url} with repo-genesis. ${routes.Application.about().absoluteURL()(req)}",
            Some("repo-genesis"),
            Some(req.user.getAvatarUrl),
            Seq.empty
          ))
        }
        Redirect(createdRepo.result.collaborationSettingsUrl)
      }
    }
  }
}
