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
    coursier.transitiveDependencies.toSet.flatMap(dep =>
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

  val dependencyPattern: Regex = """([^:]+):([^@]+)@(.+)""".r

  def parseDep(str: String): CoursierDep =
    str match {
      case dependencyPattern(org, name, ver) =>
        CoursierDep(Module(Organization(org), ModuleName(name)), ver)
      case _ => sys.error(s"Invalid format: $str")
    }

  def resolve(sought: String): Task[ResolutionResults] =
    ZIO.fromFuture(
      Resolve()
        .addRepositories(MavenRepository("s3://cube-artifacts/maven/release"))
        .addDependencies(parseDep(sought))
        .future()
        .map(ResolutionResults(_))
    )
