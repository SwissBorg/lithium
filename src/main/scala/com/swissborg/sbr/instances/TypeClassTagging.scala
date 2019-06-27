package com.swissborg.sbr.instances

import shapeless.tag.@@

trait TypeClassTagging {

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  implicit def liftTypeClass[TC[_], A, B](implicit tc: TC[A]): TC[A @@ B] =
    tc.asInstanceOf[TC[A @@ B]]
}
