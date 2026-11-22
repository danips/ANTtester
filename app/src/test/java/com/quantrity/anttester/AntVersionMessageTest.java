package com.quantrity.anttester;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AntVersionMessageTest {
    private static final byte[] VERSION_RESPONSE = {
            (byte) 0xa4, 0x0b, 0x3e, 'A', 'J', 'K', '2', '.', '0', '5', 'R', 'A', 'F', 0, (byte) 0x9d
    };

    @Test public void requestsAntVersionOnChannelZero() {
        assertArrayEquals(new byte[] {(byte) 0xa4, 0x02, 0x4d, 0x00, 0x3e, (byte) 0xd5},
                AntVersionMessage.request());
    }

    @Test public void readsVersionAfterAnUnrelatedMessageAndPadding() {
        byte[] bytes = new byte[32];
        byte[] startup = {(byte) 0xa4, 0x01, 0x6f, 0x00, (byte) 0xca};
        System.arraycopy(startup, 0, bytes, 0, startup.length);
        System.arraycopy(VERSION_RESPONSE, 0, bytes, startup.length + 2, VERSION_RESPONSE.length);

        assertEquals("AJK2.05RAF", AntVersionMessage.findVersion(bytes, bytes.length));
    }

    @Test public void waitsForTheLastByteOfASplitReply() {
        assertNull(AntVersionMessage.findVersion(VERSION_RESPONSE, VERSION_RESPONSE.length - 1));
        assertEquals("AJK2.05RAF", AntVersionMessage.findVersion(VERSION_RESPONSE, VERSION_RESPONSE.length));
    }

    @Test public void rejectsAReplyWithAnIncorrectChecksum() {
        byte[] corrupted = VERSION_RESPONSE.clone();
        corrupted[5] = 'Z';

        assertNull(AntVersionMessage.findVersion(corrupted, corrupted.length));
    }

    @Test public void readsTheBuiltInRadioVersionFromAHalCallback() {
        byte[] packet = {0x0c, 0x3e, 'A', 'O', 'V', '1', '.', '0', '8', 'B', '0', '0', 0, 0};

        assertEquals("AOV1.08B00", AntVersionMessage.readHalVersion(packet));
        assertArrayEquals(new byte[] {0x02, 0x4d, 0x00, 0x3e},
                AntVersionMessage.halRequest());
    }

    @Test public void ignoresATruncatedHalPacketOrAnUnrelatedMessage() {
        assertNull(AntVersionMessage.readHalVersion(new byte[] {3, 0x3e, 'A', 'B'}));
        assertNull(AntVersionMessage.readHalVersion(new byte[] {1, 0x6f, 0}));
    }
}
