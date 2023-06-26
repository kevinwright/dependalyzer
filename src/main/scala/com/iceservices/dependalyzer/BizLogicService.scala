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
  VersionedModule
}
import coursier.core.Configuration

class BizLogicService(dataService: PersistenceService):
  val defaultConfig: Configuration = Configuration.defaultCompile

  val allConfigs: Seq[Configuration] = Seq(
    Configuration.empty,
    Configuration.compile,
    Configuration.runtime,
    Configuration.test,
    Configuration.default,
    Configuration.defaultCompile,
    Configuration.provided,
    Configuration.`import`,
    Configuration.optional,
    Configuration.all,
  )

  val importantConfigs: Seq[Configuration] = Seq(
    Configuration.optional,
    Configuration.provided,
    Configuration.test,
    Configuration.runtime,
  )

  def debugConfigs(sought: VersionedModule): Task[Unit] =
    ZIO
      .foreach(allConfigs) { config =>
        for {
          _ <- ZIO.debug(s"resolving $config")
          resolution <- DependencyResolver.resolve(sought, config)
          _ <- ZIO.foreach(resolution.dependencyAdjacencySet)(ZIO.debug(_))
        } yield ()
      }
      .unit

  def resolveAndPersist(sought: VersionedModule): Task[Set[Persisted[VersionedModule]]] =
    ZIO.foldLeft(allConfigs)(Set.empty[Persisted[VersionedModule]]) { case (acc, config) =>
      for {
        _ <- ZIO.debug(s"resolving $config")
        resolution <- DependencyResolver.resolve(sought, config)
        persistedModules <- ZIO.debug("upserting modules") *> dataService.bulkUpsert(
          resolution.allKnownModules.toSeq,
        )
        _ <- ZIO.debug(s"upserting deps for $config") *>
          ZIO
            .attempt(resolution.dependencyAdjacencySet.toSeq)
            .flatMap(dataService.bulkUpsertDependencies)
        _ <- ZIO.debug(s"upserting parents for $config") *>
          ZIO
            .attempt(resolution.parentageAdjacencySet.toSeq)
            .flatMap(dataService.bulkUpsertParentage)
      } yield acc ++ persistedModules
    }

object BizLogicService:
  val live = ZLayer.fromFunction(new BizLogicService(_))
