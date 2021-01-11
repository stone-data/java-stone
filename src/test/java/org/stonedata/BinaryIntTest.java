package org.stonedata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.stonedata.coding.binary.translate.INT32;
import util.TestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;

class BinaryIntTest {

    @Test
    void testPositive1B() {
        testBytes(0,   "00000000");
        testBytes(1,   "00000001");
        testBytes(3,   "00000011");
        testBytes(7,   "00000111");
        testBytes(15,  "00001111");
        testBytes(31,  "00011111");
        testBytes(63,  "00111111");
        testBytes(127, "01111111");
    }

    @Test
    void testPositive2B() {
        testBytes(255,   "10000000 11111111");
        testBytes(511,   "10000001 11111111");
        testBytes(1023,  "10000011 11111111");
        testBytes(2047,  "10000111 11111111");
        testBytes(4095,  "10001111 11111111");
        testBytes(8191,  "10011111 11111111");
        testBytes(16383, "10111111 11111111");
    }

    @Test
    void testPositive3B() {
        testBytes(32767,   "11000000 01111111 11111111");
        testBytes(65535,   "11000000 11111111 11111111");
        testBytes(131071,  "11000001 11111111 11111111");
        testBytes(262143,  "11000011 11111111 11111111");
        testBytes(524287,  "11000111 11111111 11111111");
        testBytes(1048575, "11001111 11111111 11111111");
        testBytes(2097151, "11011111 11111111 11111111");
    }

    @Test
    void testPositive4B() {
        testBytes(4194303,   "11100000 00111111 11111111 11111111");
        testBytes(8388607,   "11100000 01111111 11111111 11111111");
        testBytes(16777215,  "11100000 11111111 11111111 11111111");
        testBytes(33554431,  "11100001 11111111 11111111 11111111");
        testBytes(67108863,  "11100011 11111111 11111111 11111111");
        testBytes(134217727, "11100111 11111111 11111111 11111111");
        testBytes(268435455, "11101111 11111111 11111111 11111111");
    }
    @Test
    void testPositive5B() {
        testBytes(536870911,  "11110000 00011111 11111111 11111111 11111111");
        testBytes(1073741823, "11110000 00111111 11111111 11111111 11111111");
        testBytes(2147483647, "11110000 01111111 11111111 11111111 11111111");
    }

    @Test
    void testNegative1() {
        testBytes(-1,   "11110000 11111111 11111111 11111111 11111111");
        testBytes(-2,   "11110000 11111111 11111111 11111111 11111110");
        testBytes(-4,   "11110000 11111111 11111111 11111111 11111100");
        testBytes(-8,   "11110000 11111111 11111111 11111111 11111000");
        testBytes(-16,  "11110000 11111111 11111111 11111111 11110000");
        testBytes(-32,  "11110000 11111111 11111111 11111111 11100000");
        testBytes(-64,  "11110000 11111111 11111111 11111111 11000000");
        testBytes(-128, "11110000 11111111 11111111 11111111 10000000");
        testBytes(-256, "11110000 11111111 11111111 11111111 00000000");
    }

    @Test
    void testNegative2() {
        testBytes(-512,   "11110000 11111111 11111111 11111110 00000000");
        testBytes(-1024,  "11110000 11111111 11111111 11111100 00000000");
        testBytes(-2048,  "11110000 11111111 11111111 11111000 00000000");
        testBytes(-4096,  "11110000 11111111 11111111 11110000 00000000");
        testBytes(-8192,  "11110000 11111111 11111111 11100000 00000000");
        testBytes(-16384, "11110000 11111111 11111111 11000000 00000000");
        testBytes(-32768, "11110000 11111111 11111111 10000000 00000000");
        testBytes(-65536, "11110000 11111111 11111111 00000000 00000000");
    }

    @Test
    void testNegative3() {
        testBytes(-131072,   "11110000 11111111 11111110 00000000 00000000");
        testBytes(-262144,   "11110000 11111111 11111100 00000000 00000000");
        testBytes(-524288,   "11110000 11111111 11111000 00000000 00000000");
        testBytes(-1048576,  "11110000 11111111 11110000 00000000 00000000");
        testBytes(-2097152,  "11110000 11111111 11100000 00000000 00000000");
        testBytes(-4194304,  "11110000 11111111 11000000 00000000 00000000");
        testBytes(-8388608,  "11110000 11111111 10000000 00000000 00000000");
        testBytes(-16777216, "11110000 11111111 00000000 00000000 00000000");
    }

    @Test
    void testNegative4() {
        testBytes(-33554432,   "11110000 11111110 00000000 00000000 00000000");
        testBytes(-67108864,   "11110000 11111100 00000000 00000000 00000000");
        testBytes(-134217728,  "11110000 11111000 00000000 00000000 00000000");
        testBytes(-268435456,  "11110000 11110000 00000000 00000000 00000000");
        testBytes(-536870912,  "11110000 11100000 00000000 00000000 00000000");
        testBytes(-1073741824, "11110000 11000000 00000000 00000000 00000000");
        testBytes(-2147483648, "11110000 10000000 00000000 00000000 00000000");
    }

    private static void testBytes(int number, String expectedBinary) {
        var actual = INT32.bytesOfInt(number);
        var expected = TestUtils.parseBinaryBytes(expectedBinary);

        assertEquals(expected.length, actual.length, "Bytes count");

        for (var i = 0; i < actual.length; i++) {
            var actualHex = Integer.toHexString(0xFF & actual[i]);
            var expectedHex = Integer.toHexString(0xFF & expected[i]);

            assertEquals(expectedHex, actualHex, String.format("Byte at %s", i));
        }

        try {
            var result = INT32.readRawInt(new ByteArrayInputStream(actual));

            assertEquals(number, result, "Resulting number");
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
