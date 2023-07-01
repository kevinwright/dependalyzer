package com.iceservices.dependalyzer.coursiersupport

import coursier.*

case class RepositorySet private (repoMap: Map[String, Repository]):
  def withDefaults: RepositorySet =
    RepositorySet(
      repoMap ++ Map(
        "ivy2 Local" -> LocalRepositories.ivy2Local,
        "Maven Central" -> Repositories.central,
      )
    )

  def get(name: String): Option[Repository] = repoMap.get(name)

  def all: Seq[Repository] = repoMap.values.toList

object RepositorySet:
  def fromNameUrlMap(theMap: Map[String, String]): RepositorySet =
    RepositorySet(theMap.view.mapValues(MavenRepository(_)).toMap)
  def empty: RepositorySet = RepositorySet(Map.empty)
