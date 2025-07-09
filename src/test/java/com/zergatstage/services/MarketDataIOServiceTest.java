package com.zergatstage.services;

import com.zergatstage.domain.makret.MarketDataUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MarketDataIOServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Path logDirectory;

    @Mock
    private Path marketFile;

    private MarketDataIOService marketDataIOService;

    @BeforeEach
    public void setUp() throws IOException {
        // Mock ApplicationContextProvider to return our mocked application context
        try (MockedStatic<ApplicationContextProvider> mockedContextProvider =
                     mockStatic(ApplicationContextProvider.class)) {

            mockedContextProvider.when(ApplicationContextProvider::getApplicationContext)
                    .thenReturn(applicationContext);

            // Configure mocks
            when(applicationContext.getBean(Path.class)).thenReturn(logDirectory);
            when(logDirectory.resolve("Market.json")).thenReturn(marketFile);

            // Create the service
            marketDataIOService = new MarketDataIOService(eventPublisher);
        }
    }

    @Test
    public void testCheckMarketFile_WhenFileContentChanges() throws IOException {
        // Arrange
        String initialContent = "{\"initial\": \"data\"}";
        String updatedContent = "{\"updated\": \"data\"}";

        // Mock file reading
        when(Files.readAllBytes(marketFile))
                .thenReturn(initialContent.getBytes())
                .thenReturn(updatedContent.getBytes());

        // Act
        marketDataIOService.checkMarketFile();
        marketDataIOService.checkMarketFile();

        // Assert
        verify(eventPublisher, times(1)).publishEvent(
                argThat(event ->
                        event instanceof MarketDataUpdateEvent &&
                                event.getSource() == marketDataIOService)
        );
    }

    @Test
    public void testCheckMarketFile_WhenFileContentUnchanged() throws IOException {
        // Arrange
        String unchangedContent = "{\"data\": \"same\"}";

        // Mock file reading
        when(Files.readAllBytes(marketFile))
                .thenReturn(unchangedContent.getBytes())
                .thenReturn(unchangedContent.getBytes());

        // Act
        marketDataIOService.checkMarketFile();
        marketDataIOService.checkMarketFile();

        // Assert
        verify(eventPublisher, never()).publishEvent(any(MarketDataUpdateEvent.class));
    }

    @Test
    public void testCheckMarketFile_WhenIOExceptionOccurs() throws IOException {
        // Arrange
        when(Files.readAllBytes(marketFile)).thenThrow(new IOException("File read error"));

        // Act
        marketDataIOService.checkMarketFile();

        // Assert
        // This test ensures no exception is thrown and the error is logged
        // In a real-world scenario, you might want to verify log statements
        verify(eventPublisher, never()).publishEvent(any(MarketDataUpdateEvent.class));
    }
}