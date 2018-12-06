package arrow.instances

import arrow.Kind
import arrow.core.*
import arrow.data.*
import arrow.deprecation.ExtensionsDSLDeprecated
import arrow.extension
import arrow.instances.either.foldable.foldable
import arrow.instances.either.monad.monad
import arrow.instances.either.traverse.traverse
import arrow.typeclasses.*

@extension
interface EitherTFunctorInstance<F, L> : Functor<EitherTPartialOf<F, L>> {

  fun FF(): Functor<F>

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.map(f: (A) -> B): EitherT<F, L, B> = fix().map(FF()) { f(it) }
}

@extension
interface EitherTApplicativeInstance<F, L> : Applicative<EitherTPartialOf<F, L>>, EitherTFunctorInstance<F, L> {

  fun AF(): Applicative<F>

  override fun FF(): Functor<F> = AF()

  override fun <A> just(a: A): EitherT<F, L, A> = EitherT.just(AF(), a)

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.map(f: (A) -> B): EitherT<F, L, B> = fix().map(AF()) { f(it) }

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.ap(ff: Kind<EitherTPartialOf<F, L>, (A) -> B>): EitherT<F, L, B> =
    fix().ap(AF(), ff)
}

@extension
interface EitherTMonadInstance<F, L> : Monad<EitherTPartialOf<F, L>>, EitherTApplicativeInstance<F, L> {

  fun MF(): Monad<F>

  override fun AF(): Applicative<F> = MF()

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.map(f: (A) -> B): EitherT<F, L, B> = fix().map(MF()) { f(it) }

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.ap(ff: Kind<EitherTPartialOf<F, L>, (A) -> B>): EitherT<F, L, B> =
    fix().ap(MF(), ff)

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.flatMap(f: (A) -> Kind<EitherTPartialOf<F, L>, B>): EitherT<F, L, B> = fix().flatMap(MF()) { f(it).fix() }

  override fun <A, B> tailRecM(a: A, f: (A) -> EitherTOf<F, L, Either<A, B>>): EitherT<F, L, B> =
    EitherT.tailRecM(MF(), a, f)
}

@extension
interface EitherTApplicativeErrorInstance<F, L> : ApplicativeError<EitherTPartialOf<F, L>, L>, EitherTApplicativeInstance<F, L> {

  fun AE(): ApplicativeError<F, L>

  override fun AF(): Applicative<F> = AE()

  override fun <A> Kind<EitherTPartialOf<F, L>, A>.handleErrorWith(f: (L) -> Kind<EitherTPartialOf<F, L>, A>): EitherT<F, L, A> = AE().run {
    EitherT(value().handleErrorWith { l -> f(l).value() })
  }

  override fun <A> raiseError(e: L): EitherT<F, L, A> = EitherT(AF().just(Left(e)))
}

@extension
interface EitherTMonadErrorInstance<F, L> : MonadError<EitherTPartialOf<F, L>, L>, EitherTApplicativeErrorInstance<F, L>, EitherTMonadInstance<F, L> {
  override fun MF(): Monad<F>
  override fun AE(): ApplicativeError<F, L>
  override fun AF(): Applicative<F> = MF()
}

@extension
interface EitherTMonadThrowInstance<F> : MonadThrow<EitherTPartialOf<F, Throwable>>, EitherTMonadErrorInstance<F, Throwable> {
  override fun MF(): Monad<F>
  override fun AE(): ApplicativeError<F, Throwable>
}

@extension
interface EitherTFoldableInstance<F, L> : Foldable<EitherTPartialOf<F, L>> {

  fun FFF(): Foldable<F>

  override fun <B, C> Kind<EitherTPartialOf<F, L>, B>.foldLeft(b: C, f: (C, B) -> C): C = fix().foldLeft(FFF(), b, f)

  override fun <B, C> Kind<EitherTPartialOf<F, L>, B>.foldRight(lb: Eval<C>, f: (B, Eval<C>) -> Eval<C>): Eval<C> =
    fix().foldRight(FFF(), lb, f)
}

@extension
interface EitherTTraverseInstance<F, L> : Traverse<EitherTPartialOf<F, L>>, EitherTFunctorInstance<F, L>, EitherTFoldableInstance<F, L> {

  fun TF(): Traverse<F>

  override fun FF(): Functor<F>

  override fun FFF(): Foldable<F>

  override fun <A, B> Kind<EitherTPartialOf<F, L>, A>.map(f: (A) -> B): EitherT<F, L, B> = fix().map(TF()) { f(it) }

  override fun <G, B, C> Kind<EitherTPartialOf<F, L>, B>.traverse(AP: Applicative<G>, f: (B) -> Kind<G, C>): Kind<G, EitherT<F, L, C>> =
    fix().traverse(TF(), AP, f)
}

interface EitherTSemigroupKInstance<F, L> : SemigroupK<EitherTPartialOf<F, L>> {
  fun MF(): Monad<F>

  override fun <A> Kind<EitherTPartialOf<F, L>, A>.combineK(y: Kind<EitherTPartialOf<F, L>, A>): EitherT<F, L, A> =
    fix().combineK(MF(), y)
}

fun <F, A, B, C> EitherTOf<F, A, B>.foldLeft(FF: Foldable<F>, b: C, f: (C, B) -> C): C =
  FF.compose(Either.foldable<A>()).foldLC(fix().value, b, f)

fun <F, A, B, C> EitherTOf<F, A, B>.foldRight(FF: Foldable<F>, lb: Eval<C>, f: (B, Eval<C>) -> Eval<C>): Eval<C> = FF.compose(Either.foldable<A>()).run {
  fix().value.foldRC(lb, f)
}

fun <F, A, B, G, C> EitherTOf<F, A, B>.traverse(FF: Traverse<F>, GA: Applicative<G>, f: (B) -> Kind<G, C>): Kind<G, EitherT<F, A, C>> {
  val fa: Kind<G, Kind<Nested<F, EitherPartialOf<A>>, C>> = ComposedTraverse(FF, Either.traverse(), Either.monad<A>()).run { fix().value.traverseC(f, GA) }
  val mapper: (Kind<Nested<F, EitherPartialOf<A>>, C>) -> EitherT<F, A, C> = { EitherT(FF.run { it.unnest().map { it.fix() } }) }
  return GA.run { fa.map(mapper) }
}

fun <F, G, A, B> EitherTOf<F, A, Kind<G, B>>.sequence(FF: Traverse<F>, GA: Applicative<G>): Kind<G, EitherT<F, A, B>> =
  traverse(FF, GA, ::identity)

fun <F, L> EitherT.Companion.applicativeError(MF: Monad<F>): ApplicativeError<EitherTPartialOf<F, L>, L> =
  object : ApplicativeError<EitherTPartialOf<F, L>, L>, EitherTApplicativeInstance<F, L> {

    override fun AF(): Applicative<F> = MF

    override fun <A> raiseError(e: L): Kind<EitherTPartialOf<F, L>, A> =
      EitherT(MF.just(Left(e)))

    override fun <A> Kind<EitherTPartialOf<F, L>, A>.handleErrorWith(f: (L) -> Kind<EitherTPartialOf<F, L>, A>): EitherT<F, L, A> =
      handleErrorWith(this, f, MF)
  }

fun <F, L> EitherT.Companion.monadError(MF: Monad<F>): MonadError<EitherTPartialOf<F, L>, L> =
  object : MonadError<EitherTPartialOf<F, L>, L>, EitherTMonadInstance<F, L> {
    override fun MF(): Monad<F> = MF

    override fun <A> raiseError(e: L): Kind<EitherTPartialOf<F, L>, A> =
      EitherT(MF.just(Left(e)))

    override fun <A> EitherTOf<F, L, A>.handleErrorWith(f: (L) -> EitherTOf<F, L, A>): EitherT<F, L, A> =
      handleErrorWith(this, f, MF())
  }

private fun <F, L, A> handleErrorWith(fa: EitherTOf<F, L, A>, f: (L) -> EitherTOf<F, L, A>, MF: Monad<F>): EitherT<F, L, A> =
  MF.run {
    EitherT(fa.value().flatMap {
      when (it) {
        is Either.Left -> f(it.a).value()
        is Either.Right -> just(it)
      }
    })
  }

fun <F, L> EitherT.Companion.monadError(ME: MonadError<F, L>): MonadError<EitherTPartialOf<F, L>, L> =
  object : EitherTMonadErrorInstance<F, L> {
    override fun MF(): Monad<F> = ME
    override fun AE(): ApplicativeError<F, L> = ME
  }

fun <F, A> EitherT.Companion.traverse(FF: Traverse<F>): Traverse<EitherTPartialOf<F, A>> =
  object : EitherTTraverseInstance<F, A> {
    override fun FF(): Functor<F> = FF

    override fun FFF(): Foldable<F> = FF

    override fun TF(): Traverse<F> = FF
  }

fun <F, A> EitherT.Companion.foldable(FF: Traverse<F>): Foldable<EitherTPartialOf<F, A>> =
  object : EitherTFoldableInstance<F, A> {
    override fun FFF(): Foldable<F> = FF
  }

fun <F, L> EitherT.Companion.semigroupK(MF: Monad<F>): SemigroupK<EitherTPartialOf<F, L>> =
  object : EitherTSemigroupKInstance<F, L> {
    override fun MF(): Monad<F> = MF
  }

class EitherTContext<F, E>(val MF: MonadError<F, E>) : EitherTMonadErrorInstance<F, E>, EitherTSemigroupKInstance<F, E> {
  override fun FF(): Functor<F> = MF
  override fun MF(): Monad<F> = MF
  override fun AE(): ApplicativeError<F, E> = MF
}

class EitherTContextPartiallyApplied<F, E>(val MF: MonadError<F, E>) {
  @Deprecated(ExtensionsDSLDeprecated)
  infix fun <A> extensions(f: EitherTContext<F, E>.() -> A): A =
    f(EitherTContext(MF))
}

fun <F, E> ForEitherT(MF: MonadError<F, E>): EitherTContextPartiallyApplied<F, E> =
  EitherTContextPartiallyApplied(MF)
