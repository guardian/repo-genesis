package lib

import com.madgag.github.GitHubCredentials
import lib.scalagithub.GitHub

import scalax.file.ImplicitConversions._
import scalax.file.Path

object Bot {

  val workingDir = Path.fromString("/tmp") / "bot" / "working-dir"

  import play.api.Play.current
  val config = play.api.Play.configuration

  val org = config.getString("github.org").get

  val accessToken = config.getString("github.botAccessToken").get

  val ghCreds = GitHubCredentials.forAccessKey(accessToken, workingDir.toPath).get

  def conn() = ghCreds.conn()

  def neoGitHub = new GitHub(ghCreds)
}
