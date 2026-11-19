package org.petkitlocal.core.domain.talk

import javax.inject.Inject

/** A talk through [deviceId]'s camera, opened when its events are collected ([TalkSession]). */
class OpenTalkUseCase @Inject constructor(private val talks: TalkRepository) {
    operator fun invoke(deviceId: Long): TalkSession = talks.open(deviceId)
}
