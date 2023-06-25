package com.iceservices.dependalyzer

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

import java.io.InputStream
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.charset.CodingErrorAction
import java.nio.file.{Path, Paths}
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region

import scala.io.{Codec, Source}
import scala.util.control.NonFatal
import scala.util.{Properties, Try}

class S3HandlerInstance extends URLStreamHandler {
  override def openConnection(urlToOpen: URL): URLConnection = {
    new URLConnection(urlToOpen) {
      override def getInputStream: InputStream = {
        getClient.map { s3Client =>
          val bucketName = urlToOpen.getHost
          val key = urlToOpen.getPath.tail // drop the leading /

          try {
            val gor = GetObjectRequest.builder().bucket(bucketName).key(key).build()
            s3Client.getObject(gor, ResponseTransformer.toInputStream)
          } catch {
            case e: Throwable =>
              e.printStackTrace()
              throw e
          }
        }
      }.getOrElse {
        throw new Exception("Failed to retrieve credentials")
      }

      override def connect(): Unit = {}

    }
  }

  private def getClient: Option[S3Client] = {
    Try(
      S3Client
        .builder()
        .region(Region.EU_NORTH_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()
    ).toOption
  }
}

object S3HandlerFactory extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = protocol match {
    case "s3" => new S3HandlerInstance()
    case _    => null
  }
}
