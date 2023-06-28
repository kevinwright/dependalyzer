package com.iceservices.dependalyzer.coursiersupport

import coursier.cache.CacheLogger
import coursier.util.{Artifact, Sync, Task}

final class SimpleCacheLogger(log: String => Unit) extends CacheLogger {
  override def foundLocally(url: String): Unit = log(s"foundLocally($url)")

  override def checkingArtifact(url: String, artifact: Artifact): Unit =
    log(s"checkingArtifact($url)")

  override def downloadingArtifact(url: String): Unit =
    log(s"downloadingArtifact($url)")

  override def downloadingArtifact(url: String, artifact: Artifact): Unit =
    log(s"downloadingArtifact($url)")

  override def downloadProgress(url: String, downloaded: Long): Unit = {}

  override def downloadedArtifact(url: String, success: Boolean): Unit =
    log(s"downloadedArtifact($url, $success)")

  override def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit =
    log(s"checkingUpdates($url, $currentTimeOpt)")

  override def checkingUpdatesResult(
    url: String,
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long]
  ): Unit =
    log(s"checkingUpdates($url, $currentTimeOpt, $remoteTimeOpt)")

  override def downloadLength(
    url: String,
    totalLength: Long,
    alreadyDownloaded: Long,
    watching: Boolean
  ): Unit =
    log(s"downloadLength($url, $totalLength, $alreadyDownloaded, $watching)")

  override def gettingLength(url: String): Unit =
    log(s"gettingLength($url)")

  override def gettingLengthResult(url: String, length: Option[Long]): Unit =
    log(s"gettingLengthResult($url, $length)")

  override def removedCorruptFile(url: String, reason: Option[String]): Unit =
    log(s"removedCorruptFile($url, $reason)")

  override def pickedModuleVersion(module: String, version: String): Unit =
    log(s"pickedModuleVersion($module, $version)")

  // sizeHint: estimated # of artifacts to be downloaded (doesn't include side stuff like checksums)
  override def init(sizeHint: Option[Int] = None): Unit =
    log(s"init($sizeHint)")

  override def stop(): Unit =
    log(s"stop()")

}
