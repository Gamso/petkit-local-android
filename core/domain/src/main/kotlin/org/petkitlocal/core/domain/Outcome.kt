package org.petkitlocal.core.domain

/**
 * The result of a use case or repository call. Failures are values, never exceptions thrown
 * across layers (ARCHITECTURE.md §3).
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: DomainError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = also {
    if (it is Outcome.Success) action(it.value)
}

inline fun <T> Outcome<T>.onFailure(action: (DomainError) -> Unit): Outcome<T> = also {
    if (it is Outcome.Failure) action(it.error)
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
