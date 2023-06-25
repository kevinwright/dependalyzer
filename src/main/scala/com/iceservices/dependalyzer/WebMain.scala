package com.iceservices.dependalyzer

import zio.*
import zio.http.*
import org.neo4j.graphdb.GraphDatabaseService

import zio.http.Server
import zio.stream.ZStream
import java.io.File
import java.nio.file.Paths

object WebMain extends ZIOAppDefault:

  val soughtDep =
    "tech.stage:stage-spark-utils@20230621.234928.f21e9ef-direct-conversions-SNAPSHOT"

  def exceptionToResponse(e: Throwable): Response =
    Response.text(e.toString).withStatus(Status.InternalServerError)

  val DynamicRoot = Root / "dynamic"

  val dynamic: App[ResolutionPersistenceService with GraphDatabaseService] =
    Http
      .collectZIO[Request] {
        case Method.GET -> DynamicRoot / "text" =>
          ZIO.service[GraphDatabaseService].map { db =>
            Response.text(db.databaseName() + " available = " + db.isAvailable)
          }
        case Method.GET -> DynamicRoot / "debugTopLevel" =>
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getTopLevelModules(soughtDep)
          } yield Response.text(entries.mkString("\n"))
        case Method.GET -> DynamicRoot / "debugTransitives" =>
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getTransitiveModules(soughtDep)
          } yield Response.text(entries.mkString("\n"))
        case Method.GET -> DynamicRoot / "debugParentage" =>
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getParentageAdjacency(soughtDep)
          } yield Response.text(entries.map(_.toString).mkString("\n"))
        case Method.GET -> DynamicRoot / "debugDependsOn" =>
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getDependencyAdjacency(soughtDep)
          } yield Response.text(entries.map(_.toString).mkString("\n"))
        case Method.GET -> DynamicRoot / "populate" =>
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            _ <- rps.resolveAndPersist(soughtDep)
          } yield Response.text("Dependencies Persisted")
      }
      .mapError(exceptionToResponse)

  def serveStaticFile(subPath: String) = {
    val pathStr = s"src/main/resources/web-static/$subPath"
    val file = new File(pathStr)
    if file.exists
    then Http.fromFile(file).mapError(exceptionToResponse)
    else Response.text(s"File not found: $subPath").withStatus(Status.NotFound).toHandler.toHttp
  }

  val app: App[ResolutionPersistenceService with GraphDatabaseService] = Http.collectHttp[Request] {
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
        ResolutionPersistenceService.live,
        EmbeddedNeoService.live
      )
