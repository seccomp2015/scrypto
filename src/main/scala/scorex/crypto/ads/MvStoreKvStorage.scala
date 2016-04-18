package scorex.crypto.ads

import org.h2.mvstore.MVStore

import scala.util.{Failure, Random, Success, Try}

trait MvStoreKvStorage[Key, Value] extends KVStorage[Key, Value, MvStoreStorageType] {

  val fileNameOpt: Option[String]

  protected lazy val mvs: MVStore = {
    val b = new MVStore.Builder()
    fileNameOpt.foreach(filename => b.fileName(filename))
    b.autoCommitDisabled()
    b.open()
  }

  protected lazy val map = mvs.openMap[Key, Value]("data")

  override def size: Long = map.sizeAsLong()

  override def unset(key: Key): Unit = map.remove(key)

  override def set(key: Key, value: Value): Unit = map.put(key, value)

  override def get(key: Key): Option[Value] = Option(map.get(key))

  override def close(): Unit = mvs.close()

  override def commit(): Unit = {
    mvs.commit()
    if (Random.nextInt(100) == 50) mvs.compactMoveChunks() // todo: magic number
  }
}

trait MvStoreVersionedStorage extends VersionedStorage[MvStoreStorageType] {

  import scala.collection.JavaConversions._

  protected val mvs: MVStore

  type InternalVersionTag = Long

  private val versionsMap = mvs.openMap[VersionTag, InternalVersionTag]("versions")

  protected def currentInternalVersion: InternalVersionTag = mvs.getCurrentVersion

  override def putVersionTag(versionTag: VersionTag): Unit = {
    versionsMap.put(versionTag, currentInternalVersion)
    mvs.commit()
  }

  override def allVersions(): Seq[VersionTag] = versionsMap.toSeq.sortBy(_._2).map(_._1)

  override def rollbackTo(versionTag: VersionTag): Try[VersionedStorage[MvStoreStorageType]] =
    Option(versionsMap.get(versionTag)) match {
      case Some(ivt) =>
        Try(mvs.rollbackTo(ivt + 1)) match {
          case Success(_) =>
            Success(this)
          case Failure(e) =>
            Failure(new Exception(s"Problem while rolling back to $versionTag, versionMap is ${versionsMap.toMap}", e))
        }

      case None => Failure(new Exception(s"No version $versionTag found; versionMap is ${versionsMap.toMap}"))
    }
}


trait MvStoreVersionedKvStorage[Key, Value]
  extends VersionedKVStorage[Key, Value, MvStoreStorageType]
  with MvStoreKvStorage[Key, Value]
  with MvStoreVersionedStorage