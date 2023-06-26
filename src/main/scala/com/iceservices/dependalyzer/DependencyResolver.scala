package com.iceservices.dependalyzer

import zio.*
import coursier.{Dependency as CoursierDep, *}
import coursier.cache.*

import java.net.URL
import scala.util.matching.Regex

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
    (coursier.dependencies ++ coursier.transitiveDependencies.toSet).flatMap(dep =>
      coursier
        .dependenciesOf(dep)
        .toSet
        .map(target => DependsAdjacency(coursierToModel(dep), coursierToModel(target)))
    )

  def parentageAdjacencySet: Set[ParentAdjacency] =
    coursier.projectCache.flatMap { case ((module, version), (src, proj)) =>
      proj.parent match {
        case Some((parentmodule, parentversion)) =>
          Some(
            ParentAdjacency(
              coursierToModel(module, version),
              coursierToModel(parentmodule, parentversion)
            )
          )
        case None => None
      }
    }.toSet

object DependencyResolver:
  URL.setURLStreamHandlerFactory(S3HandlerFactory)

  def depFromVm(vm: VersionedModule): CoursierDep =
    CoursierDep(Module(Organization(vm.orgName), ModuleName(vm.moduleName)), vm.version)

  def resolve(sought: VersionedModule): Task[ResolutionResults] =
    ZIO.fromFuture(
      Resolve()
        .addRepositories(MavenRepository("s3://cube-artifacts/maven/release"))
        .addDependencies(depFromVm(sought))
        .future()
        .map(ResolutionResults(_))
    )
