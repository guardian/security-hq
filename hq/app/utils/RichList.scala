package utils

object RichList {
  implicit class ListWithGroupAndMap[A](as: List[A]) {
    def groupAndMap[B, K](key: A => K)(f: A => B): Map[K, List[B]] = {
      as.foldRight(Map.empty[K, List[B]]) { case (a, acc) =>
        val k = key(a)
        val b = f(a)
        val currentKs = acc.getOrElse(k, List[B]())
        acc + (k -> (b :: currentKs))
      }
    }
  }
}
