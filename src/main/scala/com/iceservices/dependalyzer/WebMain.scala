package com.iceservices.dependalyzer

import zio.*
import zio.http.*
import zio.http.html.*
import org.neo4j.graphdb.GraphDatabaseService
import zio.http.Server
import zio.stream.ZStream

import java.io.File
import java.nio.file.Paths
import zio.json.*
import NeoEnrichment.*
import NeoEnrichment.given
import com.iceservices.dependalyzer.models.{GoJsModel, VersionedModule}

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

  val DynamicRoot = Root / "dynamic"

  val dynamic: App[BizLogicService with GraphDatabaseService] =
    Http
      .collectZIO[Request] {
        case Method.GET -> DynamicRoot / "text" =>
          ZIO.service[GraphDatabaseService].map { db =>
            Response.text(db.databaseName() + " available = " + db.isAvailable)
          }
        case Method.GET -> DynamicRoot / "subgraph" =>
          val codec = summon[NeoCodec[VersionedModule]]
          for {
            _ <- ZIO.debug("requested subgraph")
            gdb <- ZIO.service[GraphDatabaseService]
            optSubGraph <- gdb.subGraph(codec.toStub(sought))
            resultText <- ZIO.attempt(
              optSubGraph match {
                case Some(sg) => GoJsModel.fromSubGraph(sg).toJson
                case None     => "Not found"
              },
            )
            _ <- ZIO.debug(s"completed subgraph")
          } yield Response.text(resultText)
        case Method.GET -> DynamicRoot / "debugTopLevel" =>
          for {
            rps <- ZIO.service[BizLogicService]
            entries <- rps.getTopLevelModules(sought)
          } yield Response.text(entries.mkString("\n"))
        case Method.GET -> DynamicRoot / "debugTransitives" =>
          for {
            rps <- ZIO.service[BizLogicService]
            entries <- rps.getTransitiveModules(sought)
          } yield Response.text(entries.mkString("\n"))
        case Method.GET -> DynamicRoot / "debugParentage" =>
          for {
            rps <- ZIO.service[BizLogicService]
            entries <- rps.getParentageAdjacency(sought)
          } yield Response.text(entries.map(_.toString).mkString("\n"))
        case Method.GET -> DynamicRoot / "debugDependsOn" =>
          for {
            rps <- ZIO.service[BizLogicService]
            entries <- rps.getDependencyAdjacency(sought)
          } yield Response.text(entries.map(_.toString).mkString("\n"))
        case Method.GET -> DynamicRoot / "populate" =>
          for {
            rps <- ZIO.service[BizLogicService]
            _ <- rps.resolveAndPersist(sought)
          } yield Response.text("Dependencies Persisted")
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

  val app: App[BizLogicService with GraphDatabaseService] = Http.collectHttp[Request] {
    case Method.GET -> Root / "health" => Handler.ok.toHttp
    case Method.GET -> Root => Response.redirect(URL(Root / "index.html")).toHandler.toHttp
    case Method.GET -> Root / "index.html"       => serveStaticFile("index.html")
    case Method.GET -> "" /: "static" /: subPath => serveStaticFile(subPath.toString)
    case _ -> "" /: "dynamic" /: _               => dynamic
  }

  def run =
    Server
      .serve(app)
      .provide(
        Server.default,
        PersistenceService.live,
        BizLogicService.live,
        EmbeddedNeoService.live,
      )
