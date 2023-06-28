package com.iceservices.dependalyzer.coursiersupport

import coursier.cache.*
import coursier.core.*
import coursier.maven.*
import coursier.util.*

object DebuggingMavenRepository:
  def apply(root: String): DebuggingMavenRepository =
    new DebuggingMavenRepository(MavenRepository(root))

class DebuggingMavenRepository private (inner: MavenRepositoryLike) extends MavenRepositoryLike {
  def withRoot(root: String): MavenRepositoryLike =
    new DebuggingMavenRepository(inner.withRoot(root))

  def withAuthentication(authentication: Option[Authentication]): MavenRepositoryLike =
    new DebuggingMavenRepository(inner.withAuthentication(authentication))

  def withVersionsCheckHasModule(versionsCheckHasModule: Boolean): MavenRepositoryLike =
    new DebuggingMavenRepository(inner.withVersionsCheckHasModule(versionsCheckHasModule))

  def root: String = inner.root
  def authentication: Option[Authentication] = inner.authentication
  override def versionsCheckHasModule: Boolean = inner.versionsCheckHasModule

//  type Fetch[F[_]] = Artifact => EitherT[F, String, String]
  def wrappedFetch[F[_]: Monad](f: Repository.Fetch[F]): Repository.Fetch[F] =
    a => {
      println(s"$root fetching $a")
      f(a)
    }

  // Methods below are mainly used by MavenComplete
  def urlFor(path: Seq[String], isDir: Boolean = false): String = {
    println(s"$root urlFor: $path")
    inner.urlFor(path, isDir)
  }

  def artifactFor(url: String, changing: Boolean): Artifact = {
    println(s"$root artifactFor: $url")
    inner.artifactFor(url, changing)
  }

  override def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit F: Monad[F]): EitherT[F, String, (ArtifactSource, Project)] = {
    inner.find(module, version, wrappedFetch(fetch))
  }

  def moduleDirectory(module: Module): String = {
    println(s"$root moduleDirectory: $module")
    inner.moduleDirectory(module)
  }

  override def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] = {
    inner.artifacts(dependency, project, overrideClassifiers)
  }

  override def completeOpt[F[_]: Monad](
    fetch: Repository.Fetch[F]
  ): Some[Repository.Complete[F]] = {
    println(s"$root completeOpt")
    Some(MavenComplete(this, wrappedFetch(fetch), Monad[F]))
  }

  override def versions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F],
    versionsCheckHasModule: Boolean
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] = {
    println(s"$root versions $module")

    inner.versions(module, wrappedFetch(fetch), versionsCheckHasModule)
  }
}
