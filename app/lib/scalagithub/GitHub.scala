package lib.scalagithub

import java.time.Instant
import java.util.concurrent.TimeUnit.SECONDS

import com.madgag.github.GitHubCredentials
import com.madgag.okhttpscala._
import com.squareup.okhttp.Request.Builder
import com.squareup.okhttp._
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc.Results

import scala.concurrent.{ExecutionContext, Future}

object RateLimit {
  case class Status(
    remaining: Int,
    reset: Instant
  )
}

case class RateLimit(
  consumed: Int,
  statusOpt: Option[RateLimit.Status]
)

case class RequestScopes(
                          authedScopes: Set[String],
                          acceptedScopes: Set[String]
)

case class GitHubResponse[Result](
  rateLimit: RateLimit,
  requestScopes: RequestScopes,
  result: Result
)

object CreateRepo {
  implicit val writesCreateRepo = Json.writes[CreateRepo]
}

case class CreateRepo(
  name: String,
  `private`: Boolean
)

object Repo {
  implicit val readsRepo = Json.reads[Repo]
}

case class Repo(
  name: String,
  full_name: String,
  html_url: String,
  teams_url: String
) {
  val settingsUrl = s"$html_url/settings"

  val collaborationSettingsUrl = s"$settingsUrl/collaboration"

}

object User {
  implicit val readsUser = Json.reads[User]
}

case class User(
  login: String,
  url: String
)


/*
{
  "url": "https://api.github.com/orgs/octocat/memberships/defunkt",
  "state": "active",
  "role": "admin"
 */

object Membership {
  implicit val readsMembership = Json.reads[Membership]
}

case class Membership(
  url: String,
  state: String,
  role: String
)

object Team {
  implicit val readsTeam = Json.reads[Team]
}

case class Team(
  id: Long,
  name: String,
  slug: String
) {
  val atSlug = "@" + slug
}

class GitHub(ghCredentials: GitHubCredentials) {

  implicit def jsonToRequestBody(json: JsValue): RequestBody = RequestBody.create(JsonMediaType, json.toString)

  val JsonMediaType = MediaType.parse("application/json; charset=utf-8")

  private val AlwaysHitNetwork = new CacheControl.Builder().maxAge(0, SECONDS).build()


  private val IronmanPreview = "application/vnd.github.ironman-preview+json"

  def checkMembership(org: String, username: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    //GET /orgs/:org/members/:username
    val url = apiUrlBuilder
      .addPathSegment("orgs")
      .addPathSegment(org)
      .addPathSegment("members")
      .addPathSegment(username)
      .build()

    ghCredentials.okHttpClient.execute(addAuthAndCaching(new Builder().url(url)
      .addHeader("Accept", IronmanPreview)
      .get)).map(_.code() == 204)
  }

  def getTeam(teamId: Long)(implicit ec: ExecutionContext): Future[GitHubResponse[Team]] = {
    // GET /orgs/:org/memberships/:username
    val url = apiUrlBuilder
      .addPathSegment("teams")
      .addPathSegment(teamId.toString)
      .build()

    executeAndReadJson[Team](addAuthAndCaching(new Builder().url(url)))
  }

  def getMembership(org: String, username: String)(implicit ec: ExecutionContext): Future[GitHubResponse[Membership]] = {
    // GET /orgs/:org/memberships/:username
    val url = apiUrlBuilder
      .addPathSegment("orgs")
      .addPathSegment(org)
      .addPathSegment("memberships")
      .addPathSegment(username)
      .build()

    executeAndReadJson(addAuthAndCaching(new Builder().url(url)))
  }

  def getTeamMembership(teamId: Long, username: String)(implicit ec: ExecutionContext): Future[GitHubResponse[Membership]] = {
    val url = apiUrlBuilder
      .addPathSegment("teams")
      .addPathSegment(teamId.toString)
      .addPathSegment("memberships")
      .addPathSegment(username)
      .build()

    executeAndReadJson(addAuthAndCaching(new Builder().url(url)))
  }

  def addAuthAndCaching(builder: Builder): Request = builder
    .cacheControl(AlwaysHitNetwork)
    .addHeader("Authorization", s"token ${ghCredentials.accessKey}")
    .build()

  def getUser()(implicit ec: ExecutionContext): Future[GitHubResponse[User]] = {
    val url = apiUrlBuilder
      .addPathSegment("user")
      .build()

    executeAndReadJson(addAuthAndCaching(new Builder().url(url)))
  }

  def addTeamRepo(teamId: Long, org: String, repoName: String)(implicit ec: ExecutionContext) = {
    // curl -X PUT -H "Authorization: token $REPO_MAKER_GITHUB_ACCESS_TOKEN" -H "Accept: application/vnd.github.ironman-preview+json"
    // -d@bang2.json https://api.github.com/teams/1831886/repos/gu-who-demo-org/150b89c114a

    val url = apiUrlBuilder
      .addPathSegment("teams")
      .addPathSegment(teamId.toString)
      .addPathSegment("repos")
      .addPathSegment(org)
      .addPathSegment(repoName)
      .build()

    ghCredentials.okHttpClient.execute(addAuthAndCaching(new Builder().url(url)
      .addHeader("Accept", IronmanPreview)
      .put(Json.obj("permission" -> "admin"))))
  }

  def createOrgRepo(org: String, repo: CreateRepo)(implicit ec: ExecutionContext): Future[GitHubResponse[Repo]] = {
    val url = apiUrlBuilder
      .addPathSegment("orgs")
      .addPathSegment(org)
      .addPathSegment("repos")
      .build()

    executeAndReadJson(addAuthAndCaching(new Builder().url(url).post(toJson(repo))))
  }

  def executeAndReadJson[T](request: Request)(implicit ev: Reads[T], ec: ExecutionContext): Future[GitHubResponse[T]] = {
    for {
      response <- ghCredentials.okHttpClient.execute(request)
    } yield {
      val rateLimit = rateLimitFrom(response)
      val requestScopes = requestScopesFrom(response)

      println(rateLimit+ " " + requestScopes + " " +request.httpUrl())

      val json = Json.parse(response.body().byteStream())

      // println("YYY"+json)

      val result = json.validate[T]

      // println("XXX"+result)

      GitHubResponse(rateLimit, requestScopes, result.get)
    }
  }

  def rateLimitFrom[T](response: Response): RateLimit = {
    val networkResponse = Option(response.networkResponse())
    RateLimit(
      consumed = if (networkResponse.exists(_.code != NOT_MODIFIED)) 1 else 0,
      networkResponse.map(rateLimitStatusFrom)
    )
  }

  def apiUrlBuilder: HttpUrl.Builder = new HttpUrl.Builder().scheme("https").host("api.github.com")

  def rateLimitStatusFrom(response: Response) = RateLimit.Status(
    response.header("X-RateLimit-Remaining").toInt,
    Instant.ofEpochSecond(response.header("X-RateLimit-Reset").toLong)
  )

  def requestScopesFrom(response: Response) = RequestScopes(
    response.header("X-OAuth-Scopes").split(',').map(_.trim).toSet,
    response.header("X-Accepted-OAuth-Scopes").split(',').map(_.trim).toSet
  )
}
