package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConcurrentTransformationService.
 * Tests concurrent processing capabilities and thread safety.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentTransformationServiceTest {

    @Mock
    private UCSToFHIRTransformer ucsToFHIRTransformer;

    @Mock
    private FHIRToUCSTransformer fhirToUCSTransformer;

    private Executor executor;
    private ConcurrentTransformationService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(5);
        service = new ConcurrentTransformationService(
            ucsToFHIRTransformer,
            fhirToUCSTransformer,
            executor
        );
    }

    @Test
    void testTransformUCSToFHIRConcurrently_EmptyList() {
        // Act
        List<ConcurrentTransformationService.TransformationResult<FHIRResourceWrapper<? extends Resource>>> results =
            service.transformUCSToFHIRConcurrently(new ArrayList<>());

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verifyNoInteractions(ucsToFHIRTransformer);
    }

    @Test
    void testTransformationResult_Success() {
        // Arrange
        String testData = "test";

        // Act
        ConcurrentTransformationService.TransformationResult<String> result =
            ConcurrentTransformationService.TransformationResult.success(testData, 0);

        // Assert
        assertTrue(result.isSuccess());
        assertFalse(result.isTimeout());
        assertEquals(testData, result.getResult());
        assertNull(result.getError());
        assertEquals(0, result.getIndex());
    }

    @Test
    void testTransformationResult_Failure() {
        // Arrange
        TransformationException error = new TransformationException("Test error");

        // Act
        ConcurrentTransformationService.TransformationResult<String> result =
            ConcurrentTransformationService.TransformationResult.failure(error, 1);

        // Assert
        assertFalse(result.isSuccess());
        assertFalse(result.isTimeout());
        assertNull(result.getResult());
        assertEquals(error, result.getError());
        assertEquals(1, result.getIndex());
    }

    @Test
    void testTransformationResult_Timeout() {
        // Act
        ConcurrentTransformationService.TransformationResult<String> result =
            ConcurrentTransformationService.TransformationResult.timeout(2);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.isTimeout());
        assertNull(result.getResult());
        assertNotNull(result.getError());
        assertEquals(2, result.getIndex());
    }
}
