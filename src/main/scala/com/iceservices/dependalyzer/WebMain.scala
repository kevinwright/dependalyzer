package com.iceservices.dependalyzer

import zio.*
import zio.http.*
import org.neo4j.graphdb.GraphDatabaseService

object WebMain extends ZIOAppDefault:

  val soughtDep =
    "tech.stage:stage-spark-utils@20230621.234928.f21e9ef-direct-conversions-SNAPSHOT"

  def errToResponse(e: Throwable): ZIO[Any, Response, Response] =
    ZIO.succeed(Response.text(e.toString).withStatus(Status.InternalServerError))

  def exposeErrors[R](x: ZIO[R, Throwable, Response]): ZIO[R, Response, Response] =
    x.catchAll(errToResponse)

  val app: App[ResolutionPersistenceService with GraphDatabaseService] =
    Http.collectZIO[Request] {
      case Method.GET -> Root / "text" =>
        ZIO.service[GraphDatabaseService].map { db =>
          Response.text(db.databaseName() + " available = " + db.isAvailable)
        }
      case Method.GET -> Root / "debugTopLevel" =>
        exposeErrors {
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getTopLevel(soughtDep)
          } yield Response.text(entries.mkString("\n"))
        }
      case Method.GET -> Root / "debugTransitives" =>
        exposeErrors {
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getTransitives(soughtDep)
          } yield Response.text(entries.mkString("\n"))
        }
      case Method.GET -> Root / "debugParentage" =>
        exposeErrors {
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getParentage(soughtDep)
          } yield Response.text(entries.map(_.toString).mkString("\n"))
        }
      case Method.GET -> Root / "debugDependsOn" =>
        exposeErrors {
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            entries <- rps.getRelations(soughtDep)
          } yield Response.text(entries.map(_.toString).mkString("\n"))
        }
      case Method.GET -> Root / "populate" =>
        exposeErrors {
          for {
            rps <- ZIO.service[ResolutionPersistenceService]
            _ <- rps.resolveAndPersist(soughtDep, debug = true)
          } yield Response.text("Dependency Persisted")
        }
    }

  def run =
    Server
      .serve(app)
      .provide(
        Server.default,
        DataService.live,
        ResolutionPersistenceService.live,
        EmbeddedNeoService.live
      )
