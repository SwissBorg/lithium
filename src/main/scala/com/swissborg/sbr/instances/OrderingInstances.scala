package com.swissborg.sbr.instances

import shapeless.tag.@@

trait OrderingInstances {
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  implicit def taggedOrdering[A: Ordering, B]: Ordering[A @@ B] =
    Ordering[A].asInstanceOf[Ordering[A @@ B]]
}
