package com.iceservices.dependalyzer

import org.neo4j.graphdb.GraphDatabaseService
import zio.*
import zio.Console.printLine
import NeoEnrichment.*

class ResolutionPersistenceService(dataService: PersistenceService):
  def getTopLevelModules(soughtDep: String): Task[Set[VersionedModule]] =
    DependencyResolver.resolve(soughtDep).map { _.topLevelModules }

  def getTransitiveModules(soughtDep: String): Task[Set[VersionedModule]] =
    DependencyResolver.resolve(soughtDep).map { _.transitiveModules }

  def getParentageAdjacency(soughtDep: String): Task[Set[ParentAdjacency]] =
    DependencyResolver.resolve(soughtDep).map { _.parentageAdjacencySet }

  def getDependencyAdjacency(soughtDep: String): Task[Set[DependsAdjacency]] =
    DependencyResolver.resolve(soughtDep).map { _.dependencyAdjacencySet }

  def resolveAndPersist(soughtDep: String): Task[Set[Persisted[VersionedModule]]] =
    for {
      resolution <- DependencyResolver.resolve(soughtDep)
      persistedModules <- dataService.bulkUpsert(resolution.allKnownModules.toSeq)
      persistedDeps <- dataService.bulkUpsertRelationships(
        resolution.dependencyAdjacencySet.toSeq.map(da => da.from -> da.to),
        Rel.DEPENDS_ON
      )
      persistedParents <- dataService.bulkUpsertRelationships(
        resolution.parentageAdjacencySet.toSeq.map(pa => pa.child -> pa.parent),
        Rel.CHILD_OF
      )
    } yield persistedModules.toSet

object ResolutionPersistenceService:
  val live = ZLayer.fromFunction(new ResolutionPersistenceService(_))
