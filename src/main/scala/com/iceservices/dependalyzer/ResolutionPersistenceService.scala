package com.iceservices.dependalyzer

import org.neo4j.graphdb.GraphDatabaseService
import zio.*
import zio.Console.printLine
import NeoEnrichment.*

class ResolutionPersistenceService(dataService: DataService, graphDb: GraphDatabaseService):
  def getTopLevel(soughtDep: String): Task[Set[String]] =
    DependencyResolver.resolve(soughtDep).map { resolution =>
      resolution.topLevel.map(_.toString)
    }

  def getTransitives(soughtDep: String): Task[Seq[String]] =
    DependencyResolver.resolve(soughtDep).map { resolution =>
      resolution.transitives.distinct.map(_.toString)
    }

  def getParentage(soughtDep: String): Task[Set[RichParentage]] =
    DependencyResolver.resolve(soughtDep).map { resolution =>
      resolution.parentage
    }

  def getRelations(soughtDep: String): Task[Set[RichDependsOn]] =
    DependencyResolver.resolve(soughtDep).map { resolution =>
      resolution.relations
    }

  def resolveAndPersist(soughtDep: String, debug: Boolean): Task[Unit] =
    for {
      resolution <- DependencyResolver.resolve(soughtDep)
      _ <- resolution.transitives.distinct.foldLeft(ZIO.unit: Task[Unit]) { case (io, dep) =>
        io *>
          dataService.upsertOrg(dep.organisation) *>
          dataService.upsertDependency(dep).flatMap(dep => if debug then printLine(dep) else ZIO.unit) *>
          graphDb.simpleInsert(
            "Dependency",
            Map(
              "org" -> dep.orgName,
              "name" -> dep.moduleName,
              "version" -> dep.version
            )
          ).unit
      }
      _ <- resolution.parentage.foldLeft(ZIO.unit: Task[Unit]) { case (io, rp) =>
        for {
          _ <- io
          _ <- dataService.upsertOrg(rp.child.organisation)
          _ <- dataService.upsertOrg(rp.parent.organisation)
          child <- dataService.upsertDependency(rp.child)
          parent <- dataService.upsertDependency(rp.parent)
          _ <- dataService.upsertParentage(Parentage(child.id, parent.id))
          text =
            if rp.child.orgName.contains("tech.stage")
            then fansi.Bold.On(rp.toString)
            else rp.toString
          _ <- if debug then printLine(text) else ZIO.unit
        } yield ()
      }
      _ <- resolution.relations.foldLeft(ZIO.unit: Task[Unit]) { case (io, rel) =>
        for {
          _ <- io
          _ <- printLine(rel)
          _ <- dataService.upsertOrg(rel.from.organisation)
          _ <- dataService.upsertOrg(rel.to.organisation)
          from <- dataService.upsertDependency(rel.from)
          to <- dataService.upsertDependency(rel.to)
          _ <- dataService.upsertDependsOn(DependsOn(from.id, to.id))
          text =
            if rel.from.orgName.contains("tech.stage")
            then fansi.Bold.On(rel.toString)
            else rel.toString
          _ <- if debug then printLine(text) else ZIO.unit
        } yield ()
      }
    } yield ()

object ResolutionPersistenceService:
  val live = ZLayer.fromFunction(new ResolutionPersistenceService(_, _))
