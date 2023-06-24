package com.iceservices.dependalyzer


case class Persisted[T](value: T, id: String) {
  override def toString: String = s"«$id»$value"
}

case class Organisation(name: String)

case class UnversionedModule(orgName: String, moduleName: String) {
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName"
}

case class VersionedModule(orgName: String, moduleName: String, version: String) {
  def organisation: Organisation = Organisation(orgName)
  override def toString: String = s"$orgName:$moduleName@$version"
}

case class ModuleDependency(from: VersionedModule, to: VersionedModule) {
  override def toString: String = s"$from -[depends on]-> $to"
}

case class ModuleParentage(child: VersionedModule, parent: VersionedModule) {
  override def toString: String = s"$child -[child of]-> $parent"
}

case class DependsOn(fromId: Long, toId: Long)

case class Parentage(childId: Long, parentId: Long)
