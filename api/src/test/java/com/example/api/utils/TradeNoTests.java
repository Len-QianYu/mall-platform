package com.example.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TradeNoTests {
    @Test
    void parallelOrderNumbersRemainUniqueAtOneHundredThousandSamples() {
        List<String> numbers = IntStream.range(0, 100000).parallel().mapToObj(index -> TradeNo.order()).toList();
        assertEquals(numbers.size(), numbers.stream().distinct().count());
        assertTrue(numbers.stream().allMatch(number -> number.length() <= 32
            && number.chars().allMatch(Character::isLetterOrDigit)));
    }

    @Test
    void paymentNumbersKeepTheirPrefixAndFitTheStorageAndGatewayContract() {
        List<String> numbers = IntStream.range(0, 100000).parallel().mapToObj(index -> TradeNo.payment()).toList();
        assertEquals(numbers.size(), numbers.stream().distinct().count());
        assertTrue(numbers.stream().allMatch(number -> number.startsWith("P") && number.length() <= 32
            && number.chars().allMatch(Character::isLetterOrDigit)));
    }
}
