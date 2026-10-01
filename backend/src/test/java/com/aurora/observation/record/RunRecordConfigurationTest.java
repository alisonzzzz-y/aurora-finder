package com.aurora.observation.record;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "app.run-record.enabled=true",
        "app.run-record.jdbc-url=jdbc:h2:mem:configured-records;DB_CLOSE_DELAY=-1",
        "app.run-record.username=sa"
})
class RunRecordConfigurationTest {
    @Autowired RunRecordStore store;

    @Test
    void enabledApplicationUsesMigratedPersistentStore() {
        assertInstanceOf(JdbcRunRecordStore.class, store);
        String id = store.begin("ASSISTANT", 2964574L);
        store.finish(id, "COMPLETED");
        assertEquals("COMPLETED", store.find(id).orElseThrow().resultStatus());
    }
}
