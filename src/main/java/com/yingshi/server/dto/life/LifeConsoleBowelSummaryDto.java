package com.yingshi.server.dto.life;

import java.util.List;

public record LifeConsoleBowelSummaryDto(
        List<LifeConsoleBowelUserSummaryDto> users
) {
}
