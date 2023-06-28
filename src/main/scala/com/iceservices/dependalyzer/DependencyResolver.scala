package com.iceservices.dependalyzer

import com.iceservices.dependalyzer.coursiersupport.{DebuggingMavenRepository, S3HandlerFactory}
import com.iceservices.dependalyzer.models.{DependsAdjacency, ParentAdjacency, VersionedModule}
import coursier.cache.loggers.RefreshLogger
import zio.*
import coursier.{Dependency as CoursierDep, *}

import scala.util.matching.Regex
import java.net.URL

class ResolutionResults(coursier: Resolution):
  private def coursierToModel(module: Module, version: String): VersionedModule =
    VersionedModule(module.organization.value, module.name.value, version)

  private def coursierToModel(dep: CoursierDep): VersionedModule =
    coursierToModel(dep.module, dep.version)

  def rootModule: VersionedModule =
    coursierToModel(coursier.rootDependencies.head)

  def topLevelModules: Set[VersionedModule] = coursier.dependencies.map(coursierToModel)

  def transitiveModules: Set[VersionedModule] =
    coursier.transitiveDependencies.map(coursierToModel).toSet

  def transitiveParentModules: Set[VersionedModule] = coursier.projectCache.flatMap {
    case (_, (_, proj)) =>
      proj.parent match {
        case Some((parentmodule, parentversion)) =>
          Some(coursierToModel(parentmodule, parentversion))
        case None => None
      }
  }.toSet

  def allKnownModules: Set[VersionedModule] =
    topLevelModules ++ transitiveModules ++ transitiveParentModules + rootModule

  def dependencyAdjacencySet: Set[DependsAdjacency] =
    coursier.finalDependenciesCache.flatMap { case (src, targets) =>
      val srcModel = coursierToModel(src)
      targets.toSet.map(target =>
        DependsAdjacency(
          from = srcModel,
          to = coursierToModel(target),
          scope = src.configuration.value
        )
      )
    }.toSet

  def parentageAdjacencySet: Set[ParentAdjacency] =
    coursier.projectCache.flatMap { case ((module, version), (src, proj)) =>
      proj.parent match {
        case Some((parentmodule, parentversion)) =>
          Some(
            ParentAdjacency(
              coursierToModel(module, version),
              coursierToModel(parentmodule, parentversion),
            ),
          )
        case None => None
      }
    }.toSet

object DependencyResolver:
  URL.setURLStreamHandlerFactory(S3HandlerFactory)

  import coursier.cache.{Cache, CacheDefaults, FileCache}
  import coursier.core.Configuration
  import coursier.complete.Complete
  import coursiersupport.*
  import coursiersupport.given

  val cache: Cache[Task] = FileCache[Task]().withLogger(new SimpleCacheLogger(println))
//  val s3repo: Repository = DebuggingMavenRepository("s3://cube-artifacts/maven/release")
  val s3repo: Repository = MavenRepository("s3://cube-artifacts/maven/release")
//  val localMavenRepo = LocalRepositories.Dangerous.maven2Local
  val completer: Complete[Task] = Complete(cache).addRepositories(s3repo)

  def depFromVm(vm: VersionedModule): CoursierDep =
    CoursierDep(Module(Organization(vm.orgName), ModuleName(vm.moduleName)), vm.version)

  def recursiveCompletion(organization: String): Task[Map[String, Seq[String]]] =
    for {
      _ <- ZIO.debug(s"Cache location: ${CacheDefaults.location}")
      allOrgs <- recurseOrgs(organization)
      versions <- ZIO.foreach(allOrgs) { org =>
        completeOrg(s"$org:", debug = true).map(org -> _)
      }
    } yield versions.filterNot(_._2.isEmpty).toMap

  def recurseOrgs(organization: String): Task[Seq[String]] =
    for {
      suborgs <- completeOrg(s"$organization.")
      nested <- ZIO.foreach(suborgs)(recurseOrgs)
    } yield organization +: nested.flatten

  def completeOrg(organization: String, debug: Boolean = false): Task[Seq[String]] =
    for {
      _ <- if debug then ZIO.debug(s"trying to complete: [$organization]") else ZIO.unit
      topLevel <- completer.withInput(organization).result()
      results <- ZIO.foreach(topLevel.results) { entry =>
        for {
          result <- ZIO.fromEither(entry._2)
          _ <- if debug && result.nonEmpty then ZIO.debug(entry) else ZIO.unit
        } yield result
      }
    } yield results.flatten

  def resolve(sought: VersionedModule, config: Configuration): Task[ResolutionResults] = {

//    val fetch = ResolutionProcess.fetch(
//      Resolve.defaultRepositories :+ s3repo,
//      cache
//    )
//
//    val start = Resolution(
//      Seq(depFromVm(sought))
//    ).withDefaultConfiguration(config)
//
//    start.process.run(fetch).map(ResolutionResults(_))

    Resolve(cache)
      .addRepositories(s3repo)
      .addDependencies(depFromVm(sought))
      .mapResolutionParams(
        _.withDefaultConfiguration(config)
          .withExclusions(
            Set(
              (Organization("xml-apis"), ModuleName("xml-apis")),
              (Organization("xerces"), ModuleName("xerces-impl")),
            )
          )
      )
      .io
      .map(ResolutionResults(_))
  }
