package com.iceservices.dependalyzer

import com.iceservices.dependalyzer.coursiersupport.{Completer, RepositorySet}
import zio.*
import zio.http.*
import zio.http.html.*
import org.neo4j.graphdb.GraphDatabaseService
import zio.http.Server
import zio.stream.ZStream
import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}

import java.io.File
import java.nio.file.Paths
import zio.json.*
import com.iceservices.dependalyzer.neo.{EmbeddedNeoService, NeoCodec, PersistenceService, given}
import com.iceservices.dependalyzer.neo.NeoEnrichment.*
import com.iceservices.dependalyzer.neo.NeoEnrichment.given
import com.iceservices.dependalyzer.models.{GoJsModel, NodeStub, Organisation, VersionedModule}

object WebMain extends ZIOAppDefault:

  val sought = VersionedModule(
    "tech.stage",
    "stage-spark-utils",
    "20230621.234928.f21e9ef-direct-conversions-SNAPSHOT",
  )

  def exceptionToResponse(e: Throwable): Response =
    Response.html(
      data = div(
        b(e.toString),
        ul(
          e.getStackTrace.map(elem => li(elem.toString))*,
        ),
      ),
      status = Status.InternalServerError,
    )

  case class CompletionResult(
    names: Either[String, Seq[String]],
    organisations: Either[String, Seq[String]],
    packages: Either[String, Seq[String]],
  )

  object CompletionResult:
    given JsonCodec[CompletionResult] = DeriveJsonCodec.gen[CompletionResult]

  val testSocketApp: SocketApp[Ref[RepositorySet]] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("end")) =>
          channel.shutdown

        case Read(WebSocketFrame.Text(text)) =>
          for {
            repoSet <- ZIO.service[Ref[RepositorySet]]
            repositories <- repoSet.get
            completer = Completer(DependencyResolver.cache, repositories)
            optNameCompletions <- completer.completeOrg(text).timeout(5.seconds)
            optOrgCompletions <- completer.completeOrg(text + ".").timeout(5.seconds)
            optPackageCompletions <- completer.completeOrg(text + ":").timeout(5.seconds)
//            optFilteredOrganisations = for {
//              orgs <- optOrgCompletions
//              names <- optNameCompletions
//              pkgs <- optNameCompletions
//            } yield {
//              orgs.filterNot(org => pkgs.exists(org.endsWith) && names.exists(org.startsWith))
//            }
            optFilteredOrganisations = for {
              orgs <- optOrgCompletions
            } yield orgs.filterNot(org =>
              org.drop(text.length + 1).takeWhile(_ != '.').forall(_.isDigit)
            )
            allCompletions =
              CompletionResult(
                names = optNameCompletions.fold(Left("Timed out"))(Right.apply),
                organisations = optFilteredOrganisations.fold(Left("Timed out"))(Right.apply),
                packages = optPackageCompletions.fold(Left("Timed out"))(Right.apply),
              )
            _ <- ZIO.debug(allCompletions)
            _ <- channel.send(Read(WebSocketFrame.text(allCompletions.toJson)))
          } yield ()

        // Send a "greeting" message to the server once the connection is established
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          channel.send(Read(WebSocketFrame.text("""{ "message": "Greetings!" } """)))

        // Log when the channel is getting closed
        case Read(WebSocketFrame.Close(status, reason)) =>
          Console.printLine("Closing channel with status: " + status + " and reason: " + reason)

        // Print the exception if it's not a normal close
        case ExceptionCaught(cause) =>
          Console.printLine(s"Channel error!: ${cause.getMessage}")

        case _ =>
          ZIO.unit
      }
    }

  val ws = Http
    .collectZIO[Request] { case Method.GET -> Root / "ws" / "testSocket" =>
      testSocketApp.toResponse
    }

  val DynamicRoot = Root / "dynamic"

  case class RepoResponse(
    name: String,
    url: Option[String],
    builtin: Boolean,
  )

  object RepoResponse:
    given JsonCodec[RepoResponse] = DeriveJsonCodec.gen[RepoResponse]

  val dynamic: App[BizLogicService with GraphDatabaseService with Ref[RepositorySet]] =
    Http
      .collectZIO[Request] {
        case Method.GET -> DynamicRoot / "testCompletion" =>
          for {
            repoSet <- ZIO.service[Ref[RepositorySet]]
            repositories <- repoSet.get
            completer = Completer(DependencyResolver.cache, repositories)
            results <- completer.recursiveCompletion("tech.stage")
            _ <- ZIO.debug(s"${results.size} results")
          } yield Response.json(results.toJson)

        case Method.GET -> DynamicRoot / "text" =>
          ZIO.service[GraphDatabaseService].map { db =>
            Response.text(db.databaseName() + " available = " + db.isAvailable)
          }
        case Method.GET -> DynamicRoot / "repositories" =>
          for {
            db <- ZIO.service[GraphDatabaseService]
            stubs <- db.allByLabel("Repository")
            repoMap = stubs
              .map(stub => RepoResponse(stub.props("name"), stub.props.get("url"), builtin = false))
              .toSeq
          } yield Response.text(repoMap.toJson)
        case req @ Method.PUT -> DynamicRoot / "repositories" / name =>
          for {
            bodyText <- req.body.asString
            body = bodyText.fromJson[Map[String, String]].toOption.get
            url = body("url")
            _ <- ZIO.debug(s"putting repo $name = $url")
            db <- ZIO.service[GraphDatabaseService]
            _ <- db.simpleUpsert(
              NodeStub(
                label = "Repository",
                keys = Set("name"),
                props = Map("name" -> name, "url" -> url)
              )
            )
          } yield Response.text(RepoResponse(name, Some(url), builtin = false).toJson)
        case Method.GET -> DynamicRoot / "subgraph" =>
          val codec = summon[NeoCodec[VersionedModule]]
          for {
            _ <- ZIO.debug("requested subgraph")
            gdb <- ZIO.service[GraphDatabaseService]
            optSubGraph <- gdb.subGraph(sought.toStub)
            resultText <- ZIO.attempt(
              optSubGraph match {
                case Some(sg) => GoJsModel.fromSubGraph(sg).toJson
                case None     => """{"error": "Not found"}"""
              },
            )
            _ <- ZIO.debug(s"completed subgraph")
          } yield Response.json(resultText)
        case Method.GET -> DynamicRoot / "populate" =>
          for {
            bls <- ZIO.service[BizLogicService]
            _ <- bls.resolveAndPersist(sought)
          } yield Response.text("Dependencies Persisted")
        case Method.GET -> DynamicRoot / "debugConfigs" =>
          for {
            bls <- ZIO.service[BizLogicService]
            _ <- bls.debugConfigs(sought)
          } yield Response.text("Complete")
      }
      .mapError(exceptionToResponse)

  // TODO: configurable directory, with fallback to classpath resources
  def serveStaticFile(subPath: String) = {
    val pathStr = s"web-static/$subPath"
    val file = new File(pathStr)
    if file.exists
    then Http.fromFile(file).mapError(exceptionToResponse)
    else Response.text(s"File not found: $subPath").withStatus(Status.NotFound).toHandler.toHttp
  }

  val static: App[Any] = Http.collectHttp[Request] {
    case Method.GET -> Root / "health" => Handler.ok.toHttp
    case Method.GET -> Root => Response.redirect(URL(Root / "index.html")).toHandler.toHttp
    case Method.GET -> Root / "index.html"       => serveStaticFile("index.html")
    case Method.GET -> "" /: "static" /: subPath => serveStaticFile(subPath.toString)
  }

  val app = ws ++ static ++ dynamic

  def run =
    Server
      .serve(app)
      .provide(
        ZLayer.fromZIO(Ref.make(RepositorySet.empty)),
        Server.default,
        PersistenceService.live,
        BizLogicService.live,
        EmbeddedNeoService.live,
      )
