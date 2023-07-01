package com.iceservices.dependalyzer.coursiersupport

import coursier.*
import coursier.cache.{Cache, CacheDefaults, FileCache}
import coursier.complete.Complete
import coursier.core.Configuration

import zio.*

case class Completer(cache: Cache[Task], repositories: RepositorySet):

  lazy val coreCompleter: Complete[Task] =
    Complete(cache).addRepositories(repositories.all*)

  def repoCompleter(repoName: String): Option[Repository.Complete[Task]] =
    repositories.get(repoName).flatMap(_.completeOpt(cache.fetch))

  def recursiveCompletion(organization: String): Task[Map[String, Seq[String]]] =
    for {
      // default on macos is ~/Library/Caches/Coursier/v1
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
      topLevel <- coreCompleter.withInput(organization).result()
      results <- ZIO.foreach(topLevel.results) { entry =>
        for {
          result <- ZIO.fromEither(entry._2)
          _ <- if debug && result.nonEmpty then ZIO.debug(entry) else ZIO.unit
        } yield result
      }
    } yield results.flatten
