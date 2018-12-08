package arrow.instances

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import arrow.data.*
import arrow.effects.Ref
import arrow.effects.typeclasses.*
import arrow.typeclasses.MonadError
import kotlin.coroutines.CoroutineContext

interface StateTBracketInstance<F, S> : Bracket<StateTPartialOf<F, S>, Throwable>, StateTMonadThrowInstance<F, S> {

  fun MD(): MonadDefer<F>

  override fun ME(): MonadError<F, Throwable> = MD()

  override fun <A, B> StateTOf<F, S, A>.bracketCase(
    release: (A, ExitCase<Throwable>) -> StateTOf<F, S, Unit>,
    use: (A) -> StateTOf<F, S, B>): StateT<F, S, B> = MD().run {

    StateT.liftF<F, S, Ref<F, Option<S>>>(this, Ref.of(None, this)).flatMap { ref ->
      StateT<F, S, B>(this) { startS ->
        runM(this, startS).bracketCase(use = { (s, a) ->
          use(a).runM(this, s).flatMap { sa ->
            ref.set(Some(sa.a)).map { sa }
          }
        }, release = { (s, a), exitCase ->
          when (exitCase) {
            is ExitCase.Completed ->
              ref.get().map { it.getOrElse { s } }.flatMap { s ->
                release(a, ExitCase.Completed).fix().runS(this, s).flatMap { s ->
                  ref.set(Some(s))
                }
              }
            else -> release(a, exitCase).runM(this, s).void()
          }
        }).flatMap { (s, b) -> ref.get().map { it.getOrElse { s } }.tupleRight(b) }
      }
    }
  }

}

fun <F, S> StateT.Companion.bracket(MD: MonadDefer<F>): Bracket<StateTPartialOf<F, S>, Throwable> = object : StateTBracketInstance<F, S> {
  override fun MD(): MonadDefer<F> = MD
}

interface StateTMonadDeferInstance<F, S> : MonadDefer<StateTPartialOf<F, S>>, StateTBracketInstance<F, S> {

  override fun MD(): MonadDefer<F>

  override fun <A> defer(fa: () -> StateTOf<F, S, A>): StateT<F, S, A> = MD().run {
    StateT(this) { s -> defer { fa().runM(this, s) } }
  }

}

fun <F, S> StateT.Companion.monadDefer(MD: MonadDefer<F>): MonadDefer<StateTPartialOf<F, S>> = object : StateTMonadDeferInstance<F, S> {
  override fun MD(): MonadDefer<F> = MD
}

interface StateTAsyncInstane<F, S> : Async<StateTPartialOf<F, S>>, StateTMonadDeferInstance<F, S> {

  fun AS(): Async<F>

  override fun MD(): MonadDefer<F> = AS()

  override fun <A> async(fa: Proc<A>): StateT<F, S, A> = AS().run {
    StateT.liftF(this, async(fa))
  }

  override fun <A> StateTOf<F, S, A>.continueOn(ctx: CoroutineContext): StateT<F, S, A> = AS().run {
    StateT(this) { s -> runM(this, s).continueOn(ctx) }
  }

}

fun <F, S> StateT.Companion.async(AS: Async<F>): Async<StateTPartialOf<F, S>> = object : StateTAsyncInstane<F, S> {
  override fun AS(): Async<F> = AS
}