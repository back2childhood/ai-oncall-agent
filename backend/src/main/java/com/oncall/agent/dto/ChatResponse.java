package com.oncall.agent.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        List<Citation> citations
) {
}
