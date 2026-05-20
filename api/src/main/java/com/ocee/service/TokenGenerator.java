package com.ocee.service;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TokenGenerator {
    public UUID newToken() { return UuidCreator.getTimeOrderedEpoch(); }
}
