package com.yingshi.server.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageInfo(
        Integer page,
        Integer pageSize,
        String nextCursor,
        Boolean hasMore
) {
}
