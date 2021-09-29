package utils

import java.util.concurrent.atomic.AtomicReference

object Box {
  def apply[T](initialValue: T): Box[T] = new Box[T](initialValue)
}

class Box[T](t: T) {
  private val ref: AtomicReference[T] = new AtomicReference[T](t)
  def get(): T = ref.get()
  def send(t: T): Unit = ref.set(t)
}
