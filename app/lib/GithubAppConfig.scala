package lib

import com.madgag.playgithub.auth.Client
import play.api.Configuration

object GithubAppConfig {

  def authClientFrom(config: Configuration): Client = {
    val clientId = config.get[String]("github.clientId")
    val clientSecret = config.get[String]("github.clientSecret")

    Client(clientId, clientSecret)
  }

}

