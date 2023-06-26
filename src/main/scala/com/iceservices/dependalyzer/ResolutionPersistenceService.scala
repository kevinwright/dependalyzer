package com.iceservices.dependalyzer

import org.neo4j.graphdb.GraphDatabaseService
import zio.*
import zio.Console.printLine
import NeoEnrichment.*

class ResolutionPersistenceService(dataService: PersistenceService):
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
      resolution <- DependencyResolver.resolve(sought)
      persistedModules <- dataService.bulkUpsert(resolution.allKnownModules.toSeq)
      _ <- dataService.bulkUpsertRelationships(
        resolution.dependencyAdjacencySet.toSeq.map(da => da.from -> da.to),
        Rel.DEPENDS_ON
      )
      _ <- dataService.bulkUpsertRelationships(
        resolution.parentageAdjacencySet.toSeq.map(pa => pa.child -> pa.parent),
        Rel.CHILD_OF
      )
    } yield persistedModules.toSet

object ResolutionPersistenceService:
  val live = ZLayer.fromFunction(new ResolutionPersistenceService(_))
