package com.iceservices.dependalyzer

import com.iceservices.dependalyzer.coursiersupport.RepositorySet
import org.neo4j.graphdb.GraphDatabaseService
import zio.*
import zio.Console.printLine
import com.iceservices.dependalyzer.neo.{*, given}
import com.iceservices.dependalyzer.neo.NeoEnrichment.*
import com.iceservices.dependalyzer.models.*
import coursier.core.Configuration

class BizLogicService(dataService: PersistenceService, repositorySetRef: Ref[RepositorySet]):
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
          repositorySet <- repositorySetRef.get
          resolution <- DependencyResolver.resolve(repositorySet, sought, config)
          _ <- ZIO.foreach(resolution.dependencyAdjacencySet)(ZIO.debug(_))
        } yield ()
      }
      .unit

  def flattenDeps(xss: Seq[Set[DependsAdjacency]]): Seq[DependsAdjacency] = {
    val daMap = xss.foldLeft(Map.empty[(VersionedModule, VersionedModule), String]) {
      case (acc, xs) =>
        xs.foldLeft(acc) { case (subacc, da) =>
          subacc + ((da.from, da.to) -> da.scope)
        }
    }
    daMap.map { case ((from, to), scope) => DependsAdjacency(from, to, scope) }.toSeq
  }

  def resolveAndPersist(sought: VersionedModule): Task[Set[Persisted[VersionedModule]]] =
    for {
      repositorySet <- repositorySetRef.get
      resolutions <- ZIO.foreach(importantConfigs)(
        DependencyResolver.resolve(repositorySet, sought, _)
      )
      _ <- ZIO.debug("upserting modules")
      persistedModules <- dataService.bulkUpsert(resolutions.flatMap(_.allKnownModules))
      _ <- ZIO.debug("upserting deps")
      _ <- dataService.bulkUpsertDependencies(
        flattenDeps(resolutions.map(_.dependencyAdjacencySet))
      )
      _ <- ZIO.debug("upserting parents")
      _ <- dataService.bulkUpsertParentage(resolutions.flatMap(_.parentageAdjacencySet).toSeq)
    } yield persistedModules.toSet

object BizLogicService:
  val live = ZLayer.fromFunction(new BizLogicService(_, _))
