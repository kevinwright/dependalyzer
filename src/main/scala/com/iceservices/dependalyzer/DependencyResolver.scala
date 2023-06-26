package com.iceservices.dependalyzer

import com.iceservices.dependalyzer.models.{DependsAdjacency, ParentAdjacency, VersionedModule}
import zio.*
import coursier.{Dependency as CoursierDep, *}
import coursier.cache.*
import coursier.core.Configuration
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

  private def projectFor(m: Module, v: String): Option[Project] =
    coursier.projectCache.collectFirst {
      case ((cm, cv), (_, project)) if cm == m && cv == v => project
    }

  private def fullPropertyMap(project: Project): Map[String, String] = {
    val localProps = project.properties.toMap
    project.parent.flatMap(projectFor) match {
      case Some(parent) => localProps ++ fullPropertyMap(parent)
      case None         => localProps
    }
  }

  private def resolveVar(name: String, propMap: Map[String, String]): String = {
    val varRegex: Regex = """\$\{([^}]+)}""".r

    val replaced = varRegex.replaceAllIn(
      name,
      m => {
        val matchStr = m.group(1)
        val optSub = propMap.get(matchStr)
        optSub match {
          case Some(sub) =>
            println(s"subbing $sub for $matchStr")
            Regex.quoteReplacement(sub)
          case None =>
            println(s"NO MATCH for $matchStr in $propMap")
            s"???$matchStr???"
        }
      },
    )

    if replaced.contains("$") then resolveVar(replaced, propMap) else replaced

  }

  def dependencyAdjacencySet: Set[DependsAdjacency] =
    dependenciesViaProjectCache ++ resolvedDependencies


  private def fullDependencyManagement(project: Project): Map[String, String] = {
    project.dependencyManagement.flatMap{
      case (Configuration.`import`, dep: CoursierDep) =>
        fullDependencyManagement(projectFor(dep.module, dep.version))
        println(s"${d.module} @ ${d.version}"))
    }

//    project.parent.flatMap(projectFor) match {
//      case Some(parent) => localProps ++ fullPropertyMap(parent)
//      case None => localProps
//    }
  }

  def dependenciesViaProjectCache: Set[DependsAdjacency] =
    coursier.projectCache.flatMap { case ((module, version), (src, proj)) =>
      val propMap = fullPropertyMap(proj)
        ++ proj.parent.map(_._2).map("project.parent.version" -> _)
        + ("project.version" -> version)

      proj.dependencies.collect {
        case (cfg, target) if !cfg.value.isBlank =>
          val targetModule = coursierToModel(
            target.module,
            resolveVar(target.version, propMap),
          )

          if targetModule.version.isBlank then {
            println(s"BLANK VERSION: $module@$version -> $target")
            proj.dependencyManagement.foreach((_, d) => println(s"${d.module} @ ${d.version}"))
          }
          DependsAdjacency(
            from = coursierToModel(module, version),
            to = targetModule,
            scope = cfg.value,
          )
      }
    }.toSet

  def resolvedDependencies: Set[DependsAdjacency] =
    (coursier.dependencies ++ coursier.transitiveDependencies.toSet).flatMap(dep =>
      coursier
        .dependenciesOf(dep)
        .toSet
        .map(target =>
          DependsAdjacency(
            from = coursierToModel(dep),
            to = coursierToModel(target),
            scope = dep.configuration.value,
          ),
        ),
    )

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

  def resolve(sought: VersionedModule): Task[ResolutionResults] =
    ZIO.fromFuture(
      Resolve()
        .addRepositories(MavenRepository("s3://cube-artifacts/maven/release"))
        .addDependencies(depFromVm(sought))
        .future()
        .map(ResolutionResults(_)),
    )
