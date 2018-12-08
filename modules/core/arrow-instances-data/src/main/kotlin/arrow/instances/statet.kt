package arrow.instances

import arrow.Kind
import arrow.core.*
import arrow.data.*
import arrow.deprecation.ExtensionsDSLDeprecated
import arrow.instances.id.monad.monad
import arrow.typeclasses.*

interface StateTFunctorInstance<F, S> : Functor<StateTPartialOf<F, S>> {

  fun FF(): Functor<F>

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.map(f: (A) -> B): StateT<F, S, B> =
    fix().map(FF(), f)

}

fun <F, S> StateT.Companion.functor(FF: Functor<F>): Functor<StateTPartialOf<F, S>> = object : StateTFunctorInstance<F, S> {
  override fun FF(): Functor<F> = FF
}

interface StateTApplicativeInstance<F, S> : Applicative<StateTPartialOf<F, S>>, StateTFunctorInstance<F, S> {

  fun MF(): Monad<F>

  override fun FF(): Functor<F> = MF()

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.map(f: (A) -> B): StateT<F, S, B> =
    fix().map(MF(), f)

  override fun <A> just(a: A): StateT<F, S, A> =
    StateT(MF().just({ s: S -> MF().just(Tuple2(s, a)) }))

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.ap(ff: Kind<StateTPartialOf<F, S>, (A) -> B>): StateT<F, S, B> =
    fix().ap(MF(), ff)

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.product(fb: Kind<StateTPartialOf<F, S>, B>): StateT<F, S, Tuple2<A, B>> =
    fix().product(MF(), fb.fix())

}

fun <F, S> StateT.Companion.applicative(MF: Monad<F>): Applicative<StateTPartialOf<F, S>> = object : StateTApplicativeInstance<F, S> {
  override fun MF(): Monad<F> = MF
}

interface StateTMonadInstance<F, S> : Monad<StateTPartialOf<F, S>>, StateTApplicativeInstance<F, S> {

  override fun MF(): Monad<F>

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.map(f: (A) -> B): StateT<F, S, B> =
    fix().map(MF(), f)

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.flatMap(f: (A) -> Kind<StateTPartialOf<F, S>, B>): StateT<F, S, B> =
    fix().flatMap(MF(), f)

  override fun <A, B> tailRecM(a: A, f: (A) -> StateTOf<F, S, Either<A, B>>): StateT<F, S, B> =
    StateT.tailRecM(MF(), a, f)

  override fun <A, B> Kind<StateTPartialOf<F, S>, A>.ap(ff: Kind<StateTPartialOf<F, S>, (A) -> B>): StateT<F, S, B> =
    ff.fix().map2(MF(), fix(), { f, a -> f(a) })

}

fun <F, S> StateT.Companion.monad(MF: Monad<F>): Monad<StateTPartialOf<F, S>> = object : StateTMonadInstance<F, S> {
  override fun MF(): Monad<F> = MF
}

interface StateTSemigroupKInstance<F, S> : SemigroupK<StateTPartialOf<F, S>> {

  fun FF(): Monad<F>

  fun SS(): SemigroupK<F>

  override fun <A> Kind<StateTPartialOf<F, S>, A>.combineK(y: Kind<StateTPartialOf<F, S>, A>): StateT<F, S, A> =
    fix().combineK(FF(), SS(), y)

}

fun <F, S> StateT.Companion.semigroupK(FF: Monad<F>, SS: SemigroupK<F>): SemigroupK<StateTPartialOf<F, S>> = object : StateTSemigroupKInstance<F, S> {
  override fun FF(): Monad<F> = FF

  override fun SS(): SemigroupK<F> = SS
}

interface StateTApplicativeErrorInstance<F, S, E> : ApplicativeError<StateTPartialOf<F, S>, E>, StateTApplicativeInstance<F, S> {

  fun ME(): MonadError<F, E>

  override fun FF(): Functor<F> = ME()

  override fun MF(): Monad<F> = ME()

  override fun <A> raiseError(e: E): Kind<StateTPartialOf<F, S>, A> = StateT.liftF(ME(), ME().raiseError(e))

  override fun <A> Kind<StateTPartialOf<F, S>, A>.handleErrorWith(f: (E) -> Kind<StateTPartialOf<F, S>, A>): StateT<F, S, A> =
    ME().run {
      State(this) { s ->
        runM(this, s).handleErrorWith { e ->
          f(e).runM(this, s)
        }
      }
    }

}

fun <F, S, E> StateT.Companion.applicativeError(ME: MonadError<F, E>): ApplicativeError<StateTPartialOf<F, S>, E> = object : StateTApplicativeErrorInstance<F, S, E> {
  override fun ME(): MonadError<F, E> = ME
}

interface StateTMonadErrorInstance<F, S, E> : MonadError<StateTPartialOf<F, S>, E>, StateTApplicativeErrorInstance<F, S, E>, StateTMonadInstance<F, S> {

  override fun MF(): Monad<F> = ME()

  override fun ME(): MonadError<F, E>

}

fun <F, S, E> StateT.Companion.monadError(ME: MonadError<F, E>): MonadError<StateTPartialOf<F, S>, E> = object : StateTMonadErrorInstance<F, S, E> {
  override fun ME(): MonadError<F, E> = ME
}

interface StateTMonadThrowInstance<F, S> : MonadThrow<StateTPartialOf<F, S>>, StateTMonadErrorInstance<F, S, Throwable>

fun <F, S> StateT.Companion.monadThrow(ME: MonadError<F, Throwable>): MonadThrow<StateTPartialOf<F, S>> = object : StateTMonadThrowInstance<F, S> {
  override fun ME(): MonadError<F, Throwable> = ME
}

/**
 * Alias for[StateT.Companion.applicative]
 */
fun <S> StateApi.applicative(): Applicative<StateTPartialOf<ForId, S>> = StateT.applicative(Id.monad())

/**
 * Alias for [StateT.Companion.functor]
 */
fun <S> StateApi.functor(): Functor<StateTPartialOf<ForId, S>> = StateT.functor(Id.monad())

/**
 * Alias for [StateT.Companion.monad]
 */
fun <S> StateApi.monad(): Monad<StateTPartialOf<ForId, S>> = StateT.monad(Id.monad())

class StateTContext<F, S, E>(val ME: MonadError<F, E>) : StateTMonadErrorInstance<F, S, E> {
  override fun MF(): Monad<F> = ME()

  override fun ME(): MonadError<F, E> = ME
}

class StateTContextPartiallyApplied<F, S, E>(val ME: MonadError<F, E>) {
  @Deprecated(ExtensionsDSLDeprecated)
  infix fun <A> extensions(f: StateTContext<F, S, E>.() -> A): A =
    f(StateTContext(ME))
}

fun <F, S, E> ForStateT(ME: MonadError<F, E>): StateTContextPartiallyApplied<F, S, E> =
  StateTContextPartiallyApplied(ME)

class StateTMonadContext<S> : StateTMonadInstance<ForId, S> {
  override fun MF(): Monad<ForId> = Id.monad()
}

class StateContextPartiallyApplied<S>() {
  @Deprecated(ExtensionsDSLDeprecated)
  infix fun <A> extensions(f: StateTMonadContext<S>.() -> A): A =
    f(StateTMonadContext())
}

fun <S> ForState(): StateContextPartiallyApplied<S> =
  StateContextPartiallyApplied()
