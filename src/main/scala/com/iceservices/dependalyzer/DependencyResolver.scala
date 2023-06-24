package com.iceservices.dependalyzer

import zio.*
import coursier.{Dependency as CoursierDep, *}
import coursier.cache.*

import java.net.URL
import scala.util.matching.Regex

class DependencyResolution(coursier: Resolution):
  def coursierToModel(module: Module, version: String): VersionedModule =
    VersionedModule(module.organization.value, module.name.value, version)

  def coursierToModel(dep: CoursierDep): VersionedModule =
    coursierToModel(dep.module, dep.version)

  def root: VersionedModule =
    coursierToModel(coursier.rootDependencies.head)

  def topLevel: Set[VersionedModule] = coursier.dependencies.map(coursierToModel)

  def transitives: Set[VersionedModule] = coursier.transitiveDependencies.map(coursierToModel).toSet

  def parentsOfTransitives: Set[VersionedModule] = (coursier.projectCache.flatMap { case (_, (_, proj)) =>
    proj.parent match {
      case Some((parentmodule, parentversion)) =>
        Some(coursierToModel(parentmodule, parentversion))
      case None => None
    }
  }).toSet

  def allKnownModules: Set [VersionedModule] = topLevel ++ transitives ++ parentsOfTransitives + root
  
  def moduleDependencies: Set[ModuleDependency] =
    coursier.transitiveDependencies.toSet.flatMap(dep =>
      coursier.dependenciesOf(dep).toSet.map(target => ModuleDependency(coursierToModel(dep), coursierToModel(target)))
    )

  def moduleParentage: Set[ModuleParentage] =
    coursier.projectCache.flatMap { case ((module, version), (src, proj)) =>
      proj.parent match {
        case Some((parentmodule, parentversion)) =>
          Some(
            ModuleParentage(
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

  def resolve(sought: String): Task[DependencyResolution] =
    ZIO.fromFuture(
      Resolve()
        .addRepositories(MavenRepository("s3://cube-artifacts/maven/release"))
        .addDependencies(parseDep(sought))
        .future()
        .map(DependencyResolution(_))
    )
