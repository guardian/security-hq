package utils

import java.util.concurrent.atomic.AtomicReference

object Box {
  def apply[T](initialValue: T): Box[T] = new Box[T](initialValue)
}

class Box[T] private (t: T) {
  private final val ref: AtomicReference[T] = new AtomicReference[T](t)
  def get(): T = ref.get()
  def send(t: T): Unit = ref.set(t)
}