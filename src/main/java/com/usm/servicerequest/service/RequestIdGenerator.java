package com.usm.servicerequest.service;

import com.usm.servicerequest.repository.ServiceRequestRepository;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

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
