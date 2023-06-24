package com.iceservices.dependalyzer

import org.neo4j.graphdb.{GraphDatabaseService, Node}
import zio.{Task, ZIO, ZLayer}
import zio.Console.*
import NeoEnrichment.*


class DataService(neo: GraphDatabaseService):
  def insertOrganisations(orgs: Set[Organisation]): Task[Set[Persisted[Organisation]]] =
    neo.allByLabel("Organisation").map(
      _.map(node =>
        Persisted(
          Organisation(node.getProperty("name").toString),
          node.getElementId
        )
      )
    )

  
  def getAllOrganisations: Task[Set[Persisted[Organisation]]] =
    neo.allByLabel("Organisation").map(
      _.map(node =>
        Persisted(
          Organisation(node.getProperty("name").toString),
          node.getElementId
        )
      )
    )

  def getAllUnversionedModules: Task[Set[Persisted[UnversionedModule]]] =
    neo.allByLabel("UnversionedModule").map(
      _.map(node =>
        Persisted(
          UnversionedModule(
            orgName = node.getProperty("orgName").toString,
            moduleName = node.getProperty("moduleName").toString,
          ),
          node.getElementId
        )
      )
    )

  def getAllVersionedModules: Task[Set[Persisted[VersionedModule]]] =
    neo.allByLabel("VersionedModule").map(
      _.map(node =>
        Persisted(
          VersionedModule(
            orgName = node.getProperty("orgName").toString,
            moduleName = node.getProperty("moduleName").toString,
            version = node.getProperty("version").toString,
          ),
          node.getElementId
        )
      )
    )




object DataService:
  val live = ZLayer.fromFunction(new DataService(_))
