package com.example.demo.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 채팅 메시지 DTO
 * Spring AI의 Message 타입과 호환되도록 설계
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "role"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UserChatMessage::class, name = "user"),
    JsonSubTypes.Type(value = SystemChatMessage::class, name = "system"),
    JsonSubTypes.Type(value = AssistantChatMessage::class, name = "assistant")
)
@Schema(description = "채팅 메시지")
sealed class ChatMessage {
    abstract val role: String
    abstract val content: String
}

@Schema(description = "사용자 메시지")
data class UserChatMessage(
    @field:Schema(description = "메시지 역할", example = "user", required = true)
    override val role: String = "user",
    
    @field:Schema(description = "메시지 내용", example = "안녕하세요", required = true)
    override val content: String
) : ChatMessage()

@Schema(description = "시스템 메시지")
data class SystemChatMessage(
    @field:Schema(description = "메시지 역할", example = "system", required = true)
    override val role: String = "system",
    
    @field:Schema(description = "메시지 내용", example = "당신은 도움이 되는 AI 어시스턴트입니다.", required = true)
    override val content: String
) : ChatMessage()

@Schema(description = "어시스턴트 메시지 (AI 응답)")
data class AssistantChatMessage(
    @field:Schema(description = "메시지 역할", example = "assistant", required = true)
    override val role: String = "assistant",
    
    @field:Schema(description = "메시지 내용", example = "안녕하세요! 무엇을 도와드릴까요?", required = true)
    override val content: String
) : ChatMessage()
