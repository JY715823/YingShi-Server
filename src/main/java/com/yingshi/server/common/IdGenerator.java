package com.yingshi.server.common;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
