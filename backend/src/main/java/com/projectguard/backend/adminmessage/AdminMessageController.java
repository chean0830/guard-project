package com.projectguard.backend.adminmessage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/** 관리자 → 변호사 1:1 메시지 보내기와 보낸 기록(읽음 여부 포함). /api/admin/** 라 관리자 인증이 필요하다. */
@RestController
@RequestMapping("/api/admin/lawyers/{lawyerId}/messages")
public class AdminMessageController {

    private final AdminMessageService service;

    public AdminMessageController(AdminMessageService service) {
        this.service = service;
    }

    public record SendRequest(String content) {
    }

    public record MessageDto(Long id, String content, Instant createdAt, Instant readAt) {
        static MessageDto of(AdminMessage m) {
            return new MessageDto(m.getId(), m.getContent(), m.getCreatedAt(), m.getReadAt());
        }
    }

    @GetMapping
    public List<MessageDto> list(@PathVariable Long lawyerId) {
        return service.listFor(lawyerId).stream().map(MessageDto::of).toList();
    }

    @PostMapping
    public MessageDto send(@PathVariable Long lawyerId, @RequestBody SendRequest request) {
        return MessageDto.of(service.send(lawyerId, request.content()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
