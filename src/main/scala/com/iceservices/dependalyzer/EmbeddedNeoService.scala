package com.iceservices.dependalyzer

import org.neo4j.configuration.connectors.BoltConnector
import org.neo4j.configuration.helpers.SocketAddress
import org.neo4j.dbms.api.{DatabaseManagementService, DatabaseManagementServiceBuilder}
import org.neo4j.graphdb.GraphDatabaseService
import zio.*

import java.io.IOException
import java.nio.file.Path

object EmbeddedNeoService:
  private val homeDir: Path = Path.of(java.lang.System.getProperty("user.home"))
  private val dbPath: Path = homeDir.resolve("neo_dependalyzer")
  val dbName = "neo4j" // only the default is allowed

  private def initialize: Task[DatabaseManagementService] =
    ZIO.debug("Initialising dbms") *> ZIO.attemptBlockingIO(
      new DatabaseManagementServiceBuilder(dbPath)
        .setConfig(BoltConnector.enabled, true)
        .setConfig(BoltConnector.listen_address, new SocketAddress("localhost", 9669))
        .build()
//        .setConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.OPTIONAL)
    )

  private def openDefaultDb(dbms: DatabaseManagementService): Task[GraphDatabaseService] =
    ZIO.debug("Opening Default DB") *> ZIO.attemptBlockingIO(dbms.database(dbName))

  private def finalize(dbms: DatabaseManagementService): URIO[Any, Unit] =
    (ZIO.debug("shutting down dbms") *> ZIO.attempt(dbms.shutdown())).orDie

  val live: ZLayer[Any, Throwable, GraphDatabaseService] = ZLayer.scoped(
    ZIO.acquireRelease(initialize)(finalize).flatMap(openDefaultDb)
  )
