// LapRF Timing App for Android systems
//
// Developed by Lemantech Labs, SARL for ImmersionRC Limited, HK
//
// Copyright 2019 ImmersionRC Limited
//
// For licensing, please refer to README.md
//
package com.immersionrc.LapRFTiming;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * Created by urssc on 03-Apr-17.
 *
 * compute CRC
 *
 * inspired from algorithm found here:
 * https://barrgroup.com/Embedded-Systems/How-To/CRC-Calculation-C-Code
 *
 * This CRC is CRC-16 from that page
 */

public class crc {

    public static byte[] toByteArray(char[] array) {
        return toByteArray(array, Charset.defaultCharset());
    }

    public static byte[] toByteArray(char[] array, Charset charset) {
        CharBuffer cbuf = CharBuffer.wrap(array);
        ByteBuffer bbuf = charset.encode(cbuf);
        return bbuf.array();
    }

    public crc()
    {
        initCrcTable();

        byte[] bytes = {(byte)0x5a, (byte)0x3d,
                (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0a,(byte)0xda,(byte)0x21,(byte)0x02,
                (byte)0x3c,(byte)0x0d,(byte)0x23,(byte)0x01,(byte)0x01,(byte)0x24,(byte)0x04,
                (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x01,(byte)0x01,
                (byte)0x22,(byte)0x04,(byte)0x00,(byte)0x80,(byte)0x62,(byte)0x44,(byte)0x01,
                (byte)0x01,
                (byte)0x02,(byte)0x22,(byte)0x04,(byte)0x00,(byte)0x00,(byte)0x62,(byte)0x44,
                (byte)0x01,(byte)0x01,(byte)0x03,(byte)0x22,(byte)0x04,(byte)0x00,(byte)0x80,
                (byte)0x6a,
                (byte)0x44,(byte)0x01,(byte)0x01,(byte)0x04,(byte)0x22,(byte)0x04,(byte)0x00,
                (byte)0x00,(byte)0x62,(byte)0x44,(byte)0x03,(byte)0x02,(byte)0x00,(byte)0x00,
                (byte)0x5b};

        int crc = computeCrc(bytes, bytes.length);

    }

    // Table for fast CRC computation
    int crcTable[] = new int[256];


    public int computeCrc(byte[] message, int length)
    {
        int remainder = (int)0x0000;

        for ( int i = 0; i < length; i++ )
        {
            int a = (reflect((int)message[i],8) & (int)0xFF);
            int b = ((remainder >> 8) & ((int)0xFF));
            int c = ( (remainder << 8) & 0xFFFF );
            int data = a ^ b;
            remainder = crcTable[data] ^ c;
        }

        return reflect(remainder, 16);
    }

    // Helper functions
    // reflect
    int reflect(int input, int nbits)
    {
        int output = 0;
        for (int i = 0; i < nbits; i++)
        {

            if ( (input & (int)0x01) == (int)0x01)
            {
                output |= (1 << ((nbits - 1) - i) );
            }

            input = input >> 1;
        }

        return output;
    }

    // prepare the CRC table
    void initCrcTable()
    {
        int remainder = 0;

        for (int i = 0; i < 256; i += 1 )
        {
            remainder = (int)( (i << 8) & 0xFF00 );
            for ( int j = 8; j > 0; --j )
            {
                if ( (remainder & (int)0x8000) == (int)0x8000 )
                    remainder = ( ( (remainder << 1) & 0xFFFF )  ^ 0x8005 );
                else
                    remainder = ( ( (remainder << 1) & 0xFFFF ) );
            }

            crcTable[i] = remainder;
        }
    }
}
