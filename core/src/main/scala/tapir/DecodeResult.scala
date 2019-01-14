package tapir

sealed trait DecodeResult[+T] {
  def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): T
  def map[TT](f: T => TT): DecodeResult[TT]
  def flatMap[U](f: T => DecodeResult[U]): DecodeResult[U]
}
object DecodeResult {
  case class Value[T](v: T) extends DecodeResult[T] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): T = v
    override def map[TT](f: T => TT): DecodeResult[TT] = Value(f(v))
    override def flatMap[U](f: T => DecodeResult[U]): DecodeResult[U] = f(v)
  }
  case object Missing extends DecodeResult[Nothing] { // TODO: add field name?
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, None)
    override def map[TT](f: Nothing => TT): DecodeResult[TT] = this
    override def flatMap[U](f: Nothing => DecodeResult[U]): DecodeResult[U] = this
  }
  case class Error(original: String, error: Throwable, message: String) extends DecodeResult[Nothing] {
    def getOrThrow(e: (DecodeResult[Nothing], Option[Throwable]) => Throwable): Nothing = throw e(this, Some(error))
    override def map[TT](f: Nothing => TT): DecodeResult[TT] = this
    override def flatMap[U](f: Nothing => DecodeResult[U]): DecodeResult[U] = this
  }

  // TODO: to reduce allocations, maybe replace with exceptions (which would all be handled by formats / codecs?)

  def sequence(results: List[DecodeResult[_]]): DecodeResult[List[Any]] = {
    results.foldRight(Value(List.empty[Any]): DecodeResult[List[Any]]) {
      case (result, acc) =>
        (result, acc) match {
          case (Value(v), Value(vs)) => Value(v :: vs)
          case (Value(_), r)         => r
          case (Missing, _)          => Missing
          case (e: Error, _)         => e
        }
    }
  }
}
