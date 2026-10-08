package com.aurora.observation.record;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetryingDriverDataSourceTest {
    private RetryingDriverDataSource source() {
        return spy(new RetryingDriverDataSource("jdbc:h2:mem:retry", "sa", ""));
    }

    @Test
    void retriesConnectionResetBeforeReturningConnection() throws Exception {
        var source = source();
        var connection = mock(Connection.class);
        doThrow(new SQLException("reset", "08S01")).doReturn(connection)
                .when(source).openConnection(anyString(), any(Properties.class));
        assertSame(connection, source.getConnection());
        verify(source, times(2)).openConnection(anyString(), any(Properties.class));
        verifyNoInteractions(connection);
    }

    @Test
    void givesUpAfterThreeAttempts() throws Exception {
        var source = source();
        doThrow(new SQLException("reset", "08001"))
                .when(source).openConnection(anyString(), any(Properties.class));
        assertThrows(SQLException.class, source::getConnection);
        verify(source, times(3)).openConnection(anyString(), any(Properties.class));
    }

    @Test
    void doesNotRetryInvalidCredentials() throws Exception {
        var source = source();
        doThrow(new SQLException("denied", "28000"))
                .when(source).openConnection(anyString(), any(Properties.class));
        assertThrows(SQLException.class, source::getConnection);
        verify(source).openConnection(anyString(), any(Properties.class));
    }

    @Test
    void interruptionStopsRetryAndKeepsInterruptFlag() throws Exception {
        var source = source();
        doThrow(new SQLException("reset", "08001"))
                .when(source).openConnection(anyString(), any(Properties.class));
        Thread.currentThread().interrupt();
        try {
            assertThrows(SQLException.class, source::getConnection);
            assertTrue(Thread.currentThread().isInterrupted());
            verify(source).openConnection(anyString(), any(Properties.class));
        } finally {
            Thread.interrupted();
        }
    }
}
