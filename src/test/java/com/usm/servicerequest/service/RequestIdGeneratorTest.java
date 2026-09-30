package com.usm.servicerequest.service;

import com.usm.servicerequest.repository.ServiceRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdGeneratorTest {

    @Mock
    private ServiceRequestRepository repository;

    @Test
    void nextId_whenDatabaseEmpty_generatesFirstId() {
        when(repository.findMaxRequestIdForYear(anyInt())).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(0L);

        RequestIdGenerator generator = new RequestIdGenerator(repository);
        String id = generator.nextId();

        int year = Year.now().getValue();
        assertThat(id).isEqualTo(String.format("SR-%d-0001", year));
    }

    @Test
    void nextId_whenMaxIdExists_startsAfterMaxId() {
        int year = Year.now().getValue();
        when(repository.findMaxRequestIdForYear(year)).thenReturn(Optional.of(String.format("SR-%d-0042", year)));

        RequestIdGenerator generator = new RequestIdGenerator(repository);
        String id = generator.nextId();

        assertThat(id).isEqualTo(String.format("SR-%d-0043", year));
    }

    @Test
    void nextId_whenCandidateAlreadyExists_incrementsToNextAvailable() {
        int year = Year.now().getValue();
        when(repository.findMaxRequestIdForYear(year)).thenReturn(Optional.empty());
        when(repository.count()).thenReturn(0L);

        String id1 = String.format("SR-%d-0001", year);
        String id2 = String.format("SR-%d-0002", year);

        // Simulate id1 already existing in DB
        when(repository.existsById(id1)).thenReturn(true);
        when(repository.existsById(id2)).thenReturn(false);

        RequestIdGenerator generator = new RequestIdGenerator(repository);
        String next = generator.nextId();

        // Should have skipped id1 and returned id2
        assertThat(next).isEqualTo(id2);
    }
}
