package com.iceservices.dependalyzer.coursiersupport

import coursier.util.Sync
import zio.*

given ZioCoursierTask: Sync[Task] with
  def point[A](a: A): Task[A] = ZIO.succeed(a)

  def bind[A, B](elem: Task[A])(f: A => Task[B]): Task[B] = elem.flatMap(f)

  def delay[A](a: => A): Task[A] = ZIO.attempt(a)

  def handle[A](a: Task[A])(f: PartialFunction[Throwable, A]): Task[A] =
    a.catchSome {
      case x if f.isDefinedAt(x) => ZIO.attempt(f(x))
    }

  def fromAttempt[A](a: Either[Throwable, A]): Task[A] =
    ZIO.fromEither(a)

  def gather[A](elems: Seq[Task[A]]): Task[Seq[A]] =
    ZIO.foreach(elems)(identity)

  def schedule[A](pool: java.util.concurrent.ExecutorService)(f: => A): zio.Task[A] =
    ZIO.fromFutureJava(pool.submit(() => f))
