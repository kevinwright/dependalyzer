package com.iceservices.dependalyzer

import org.neo4j.graphdb.GraphDatabaseService
import zio.*
import zio.Console.printLine
import NeoEnrichment.*
import com.iceservices.dependalyzer.models.{
  DependsAdjacency,
  ParentAdjacency,
  Persisted,
  Rel,
  VersionedModule,
}

class BizLogicService(dataService: PersistenceService):
  def getTopLevelModules(sought: VersionedModule): Task[Set[VersionedModule]] =
    DependencyResolver.resolve(sought).map { _.topLevelModules }

  def getTransitiveModules(sought: VersionedModule): Task[Set[VersionedModule]] =
    DependencyResolver.resolve(sought).map { _.transitiveModules }

  def getParentageAdjacency(sought: VersionedModule): Task[Set[ParentAdjacency]] =
    DependencyResolver.resolve(sought).map { _.parentageAdjacencySet }

  def getDependencyAdjacency(sought: VersionedModule): Task[Set[DependsAdjacency]] =
    DependencyResolver.resolve(sought).map { _.dependencyAdjacencySet }

  def resolveAndPersist(sought: VersionedModule): Task[Set[Persisted[VersionedModule]]] =
    for {
      resolution <- ZIO.debug("resolving") *> DependencyResolver.resolve(sought)
      persistedModules <- ZIO.debug("upserting modules") *> dataService.bulkUpsert(
        resolution.allKnownModules.toSeq,
      )
      _ <- ZIO.debug("upserting deps") *>
        ZIO
          .attempt(resolution.dependencyAdjacencySet.toSeq)
          .flatMap(dataService.bulkUpsertDependencies)
      _ <- ZIO.debug("upserting parents") *>
        ZIO
          .attempt(resolution.parentageAdjacencySet.toSeq)
          .flatMap(dataService.bulkUpsertParentage)
    } yield persistedModules.toSet

object BizLogicService:
  val live = ZLayer.fromFunction(new BizLogicService(_))
