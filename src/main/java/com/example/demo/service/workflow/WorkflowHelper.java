package com.example.demo.service.workflow;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.repository.TransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowHelper {

    private final TransitionRepository transitionRepository;

    /**
     * Ottiene i nomi delle Transition rimosse nel formato "Nome (Da Stato -> A Stato)" o "Da Stato -> A Stato"
     */
    public List<String> getTransitionNames(Set<Long> transitionIds, Tenant tenant) {
        return transitionIds.stream()
                .map(id -> {
                    try {
                        Transition transition = transitionRepository.findByTransitionIdAndTenant(id, tenant).orElse(null);
                        if (transition == null) {
                            return "Transition " + id;
                        }
                        return formatTransitionName(transition);
                    } catch (Exception e) {
                        return "Transition " + id;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Formatta il nome di una Transition nel formato "Nome (Da Stato -> A Stato)" o "Da Stato -> A Stato"
     */
    public String formatTransitionName(Transition transition) {
        String fromStatusName = transition.getFromStatus().getStatus().getName();
        String toStatusName = transition.getToStatus().getStatus().getName();
        String transitionName = transition.getName();

        // Se la transizione ha un nome, usa il formato "Nome (Da Stato -> A Stato)"
        if (transitionName != null && !transitionName.trim().isEmpty()) {
            return String.format("%s (%s -> %s)", transitionName.trim(), fromStatusName, toStatusName);
        } else {
            // Se non ha nome, usa solo "Da Stato -> A Stato"
            return String.format("%s -> %s", fromStatusName, toStatusName);
        }
    }

    /**
     * Formatta il nome di una Transition da due nomi di stato nel formato "Da Stato -> A Stato"
     * Utile quando si hanno solo i nomi degli stati senza l'oggetto Transition completo
     */
    public String formatTransitionName(String fromStatusName, String toStatusName) {
        if (fromStatusName == null && toStatusName == null) {
            return null;
        }
        return String.format("%s -> %s",
                fromStatusName != null ? fromStatusName : "?",
                toStatusName != null ? toStatusName : "?"
        );
    }
}

