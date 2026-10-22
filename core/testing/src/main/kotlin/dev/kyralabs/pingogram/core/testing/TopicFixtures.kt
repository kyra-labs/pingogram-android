package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/** This message in [topic] (td_api.tl `message.topic_id`): a forum topic or a message thread. */
fun TdApi.Message.inTopic(topic: TdApi.MessageTopic): TdApi.Message = also { it.topicId = topic }
