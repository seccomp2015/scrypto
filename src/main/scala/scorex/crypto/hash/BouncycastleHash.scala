package scorex.crypto.hash

import org.bouncycastle.crypto.ExtendedDigest

trait BouncycastleHash[T <: Digest] extends CryptographicHash[T] {
  protected def internalHash(input: Message): Array[Byte] = synchronized {
    digestFn.update(input, 0, input.length)
    val res = new Array[Byte](DigestSize)
    digestFn.doFinal(res, 0)
    res
  }

  protected def digestFn: ExtendedDigest

}
