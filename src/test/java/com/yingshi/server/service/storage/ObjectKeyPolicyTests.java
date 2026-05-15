package com.yingshi.server.service.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectKeyPolicyTests {

    @Test
    void objectKeysMustStayRelativeAndProviderNeutral() {
        assertEquals(
                "originals/2026/04/media_001.jpg",
                ObjectKeyPolicy.tryNormalizeRelativeObjectKey("originals\\2026\\04\\media_001.jpg")
        );
        assertFalse(ObjectKeyPolicy.isRelativeObjectKey("http://127.0.0.1:9000/yingshi-media/originals/a.jpg"));
        assertFalse(ObjectKeyPolicy.isRelativeObjectKey("https://example.trycloudflare.com/api/media/files/media_001"));
        assertFalse(ObjectKeyPolicy.isRelativeObjectKey("oss://bucket/originals/a.jpg"));
        assertFalse(ObjectKeyPolicy.isRelativeObjectKey("C:\\media\\a.jpg"));
        assertNull(ObjectKeyPolicy.tryNormalizeRelativeObjectKey("../originals/a.jpg"));
        assertTrue(ObjectKeyPolicy.looksLikeFullUrl("https://oss-cn-hangzhou.aliyuncs.com/bucket/a.jpg"));
    }
}
