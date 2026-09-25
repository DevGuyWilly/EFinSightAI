package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.dto.ConversationDetailDto;
import ai.efinsight.e_finsight.dto.ConversationMessageDto;
import ai.efinsight.e_finsight.dto.ConversationSummaryDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.exception.ConversationNotFoundException;
import ai.efinsight.e_finsight.model.Conversation;
import ai.efinsight.e_finsight.model.ConversationMessage;
import ai.efinsight.e_finsight.repository.ConversationMessageRepository;
import ai.efinsight.e_finsight.repository.ConversationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent advisor conversations. Every lookup is scoped by user id: a conversation that belongs to someone else
 * behaves exactly like one that doesn't exist.
 */
@Service
public class ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    public Conversation requireOwned(Long userId, Long conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    // The latest `limit` messages of a conversation, oldest first
    public List<ConversationMessage> recentMessages(Long conversationId, int limit) {
        List<ConversationMessage> latest = new ArrayList<>(
                messageRepository.findLatest(conversationId, PageRequest.of(0, limit)));
        return latest.reversed();
    }

    /**
     * Saves one question and its answer, creating the conversation (titled after the question) when
     * {@code conversation} is null. Only called for successful answers, so a failed question never leaves an empty
     * conversation behind.
     */
    @Transactional
    public Conversation recordTurn(Long userId, Conversation conversation, String question, PlanResponseDto answer) {
        Conversation target = conversation != null
                ? requireOwned(userId, conversation.getId())
                : new Conversation(userId, titleFrom(question));
        target.setUpdatedAt(Instant.now());
        target = conversationRepository.save(target);

        messageRepository.save(new ConversationMessage(target, ConversationMessage.Role.USER, question, null));
        String summary = answer.getSummary() != null ? answer.getSummary() : "";
        messageRepository.save(new ConversationMessage(target, ConversationMessage.Role.ASSISTANT, summary, toJson(answer)));
        return target;
    }

    public List<ConversationSummaryDto> list(Long userId) {
        return conversationRepository.summarizeByUserId(userId).stream()
                .map(c -> new ConversationSummaryDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt(),
                        c.getMessageCount() != null ? c.getMessageCount() : 0))
                .toList();
    }

    public ConversationDetailDto get(Long userId, Long conversationId) {
        Conversation conversation = requireOwned(userId, conversationId);
        List<ConversationMessageDto> messages = new ArrayList<>();
        String lastQuestion = null;
        for (ConversationMessage message : messageRepository.findAllInOrder(conversation.getId())) {
            PlanResponseDto response = null;
            if (message.getRole() == ConversationMessage.Role.USER) {
                lastQuestion = message.getContent();
            } else {
                response = fromJson(message.getPayload(), message.getContent());
                response.setQuestion(lastQuestion);
                response.setConversationId(conversation.getId());
                response.setConversationTitle(conversation.getTitle());
            }
            messages.add(new ConversationMessageDto(message.getId(), message.getRole().name().toLowerCase(),
                    message.getContent(), message.getCreatedAt(), response));
        }
        return new ConversationDetailDto(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getUpdatedAt(), messages);
    }

    @Transactional
    public void delete(Long userId, Long conversationId) {
        conversationRepository.delete(requireOwned(userId, conversationId));
    }

    @Transactional
    public int deleteAll(Long userId) {
        return conversationRepository.deleteAllByUserId(userId);
    }

    // First line of the question, whitespace collapsed, cut at a word boundary to fit the title column
    static String titleFrom(String question) {
        String title = question.strip().lines().findFirst().orElse("").replaceAll("\\s+", " ");
        int max = 80;
        if (title.length() <= max) {
            return title.isEmpty() ? "New conversation" : title;
        }
        int cut = title.lastIndexOf(' ', max - 1);
        return title.substring(0, cut > max / 2 ? cut : max - 1).stripTrailing() + "…";
    }

    private String toJson(PlanResponseDto answer) {
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise advisor answer", e);
        }
    }

    // A payload that can't be read (e.g. written by an older format) still shows the summary text
    private PlanResponseDto fromJson(String payload, String summary) {
        if (payload != null) {
            try {
                PlanResponseDto response = objectMapper.readValue(payload, PlanResponseDto.class);
                response.setSuccess(true);
                return response;
            } catch (JsonProcessingException e) {
                log.warn("Unreadable stored advisor answer; falling back to its summary", e);
            }
        }
        PlanResponseDto response = new PlanResponseDto();
        response.setSuccess(true);
        response.setSummary(summary);
        return response;
    }
}
