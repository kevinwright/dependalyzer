package com.iceservices.dependalyzer.coursiersupport

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  GetObjectResponse,
  ListObjectsRequest,
  ListObjectsResponse,
  NoSuchKeyException
}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.charset.CodingErrorAction
import java.nio.file.{Path, Paths}
import scala.io.{Codec, Source}
import scala.util.control.NonFatal
import scala.util.{Properties, Try}
import scala.jdk.CollectionConverters.*

class S3HandlerInstance extends URLStreamHandler {

  class S3ClientWrapper(underlying: S3Client) {
    def getObjectRequest(
      bucketName: String,
      key: String,
    ): GetObjectRequest =
      GetObjectRequest.builder().bucket(bucketName).key(key).build()

    def listObjectsRequest(
      bucketName: String,
      delimiter: String,
      prefix: String,
    ): ListObjectsRequest = {
      val builder = ListObjectsRequest
        .builder()
        .bucket(bucketName)
        .delimiter(delimiter)
      val normalisedPrefix =
        Some(prefix)
          .filterNot(p => p.isBlank || p.trim == delimiter)
          .map(p => if p.endsWith(delimiter) then p else p + delimiter)
      normalisedPrefix.fold(builder)(builder.prefix).build()
    }

    def getObject[T](
      bucketName: String,
      key: String,
      responseTransformer: ResponseTransformer[GetObjectResponse, T] =
        ResponseTransformer.toInputStream
    ): T =
      underlying.getObject(
        getObjectRequest(bucketName, key),
        responseTransformer
      )

    def listObjects(bucketName: String, prefix: String): Seq[String] = {
      val response: ListObjectsResponse =
        underlying.listObjects(listObjectsRequest(bucketName, "/", prefix))
      response.commonPrefixes().asScala.map(_.prefix).toSeq
    }
  }

  def inputStreamFor(url: URL): InputStream =
    getClient.fold(
      throw new Exception("Failed to retrieve credentials")
    ) { s3Client =>
      val bucketName = url.getHost
      val key = url.getPath.tail // drop the leading /

      try {
        println(s"s3 opening: $url")

        if key.endsWith("/") then {
          val found = s3Client.listObjects(bucketName, key)
          println("directory listing:")
          val links =
            found.map(_.substring(key.length)).map(entry => s"""<a href="$entry">$entry</a>""")
          val html =
            s"""
               |<html>
               |<body>
               |${links.mkString("\n")}
               |</body>
               |</html>""".stripMargin
          new ByteArrayInputStream(html.getBytes())
        } else s3Client.getObject(bucketName, key)
      } catch {
        case e: NoSuchKeyException =>
          println(s"s3 No such key: $url")
          throw e

        case e: Throwable =>
          e.printStackTrace()
          throw e
      }
    }

  override def openConnection(urlToOpen: URL): URLConnection = {
    println(s"s3 openConnection: $urlToOpen")
    new URLConnection(urlToOpen) {
      override def getInputStream: InputStream = inputStreamFor(urlToOpen)
      override def connect(): Unit = {}
    }
  }

  private def getClient: Option[S3ClientWrapper] = {
    Try(
      S3Client
        .builder()
        .region(Region.EU_NORTH_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()
    ).toOption.map(new S3ClientWrapper(_))
  }
}

object S3HandlerFactory extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = protocol match {
    case "s3" => new S3HandlerInstance()
    case _    => null
  }
}
