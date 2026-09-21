package com.usm.servicerequest.service;

import com.usm.servicerequest.repository.ServiceRequestRepository;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Produces IDs shaped like SR-2026-0001 (guide §3.1: "PK, generated reference
 * (e.g. SR-2026-0001)").
 *
 * Prototype-grade: an in-memory counter seeded from the current row count at
 * startup, guarded by a lock so two concurrent requests never get the same
 * number on one instance. This is fine for Sprint 1 / a single-instance
 * dev deployment; a real sequence (DB sequence table, or a UUID) is the
 * thing to swap in before this service is ever run with more than one
 * instance behind a load balancer.
 */
@Component
public class RequestIdGenerator {

    private final AtomicLong counter;
    private volatile int currentYear;
    private final Object lock = new Object();

    public RequestIdGenerator(ServiceRequestRepository repository) {
        this.currentYear = Year.now().getValue();
        this.counter = new AtomicLong(repository.count());
    }

    public String nextId() {
        int year = Year.now().getValue();
        synchronized (lock) {
            if (year != currentYear) {
                currentYear = year;
                counter.set(0);
            }
            long next = counter.incrementAndGet();
            return String.format("SR-%d-%04d", year, next);
        }
    }
}
