package com.kavinda.auth_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public int revokeAllSessions(UUID userId) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(userId.toString());

        if (sessions.isEmpty()) {
            log.debug(
                    "No active sessions found for userId={}",
                    userId
            );

            return 0;
        }

        sessions.keySet().forEach(sessionRepository::deleteById);

//        for (String sessionId : sessions.keySet()) {
//            sessionRepository.deleteById(sessionId);
//        }

        log.info(
                "Revoked all sessions for userId={}, sessionCount={}",
                userId,
                sessions.size()
        );

        return sessions.size();
    }
}
