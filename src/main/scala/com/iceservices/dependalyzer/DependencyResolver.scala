package com.iceservices.dependalyzer

import com.iceservices.dependalyzer.models.{DependsAdjacency, ParentAdjacency, VersionedModule}
import zio.*
import coursier.{Dependency as CoursierDep, *}
import coursier.cache.*
import coursier.core.Configuration
import coursier.util.{Gather, PlatformSync}

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

  def depFromVm(vm: VersionedModule): CoursierDep =
    CoursierDep(Module(Organization(vm.orgName), ModuleName(vm.moduleName)), vm.version)



  def resolve(sought: VersionedModule, config: Configuration): Task[ResolutionResults] = {
    import coursier.cache.{Cache, FileCache}

    val cache = FileCache[Task]()
    val s3repo = MavenRepository("s3://cube-artifacts/maven/release")


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