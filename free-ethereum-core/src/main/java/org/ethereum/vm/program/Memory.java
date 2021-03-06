/*
 * The MIT License (MIT)
 *
 * Copyright 2017 Alexander Orlov <alexander.orlov@loxal.net>. All rights reserved.
 * Copyright (c) [2016] [ <ether.camp> ]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.ethereum.vm.program;

import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.listener.ProgramListener;
import org.ethereum.vm.program.listener.ProgramListenerAware;

import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.lang.String.format;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.oneByteToHexString;

public class Memory implements ProgramListenerAware {

    private static final int CHUNK_SIZE = 1024;
    private static final int WORD_SIZE = 32;

    private final List<byte[]> chunks = new LinkedList<>();
    private int softSize;
    private ProgramListener programListener;

    @Override
    public void setProgramListener(final ProgramListener traceListener) {
        this.programListener = traceListener;
    }

    public byte[] read(final int address, final int size) {
        if (size <= 0) return EMPTY_BYTE_ARRAY;

        extend(address, size);
        final byte[] data = new byte[size];

        int chunkIndex = address / CHUNK_SIZE;
        int chunkOffset = address % CHUNK_SIZE;

        int toGrab = data.length;
        int start = 0;

        while (toGrab > 0) {
            final int copied = grabMax(chunkIndex, chunkOffset, toGrab, data, start);

            // read next chunk from the start
            ++chunkIndex;
            chunkOffset = 0;

            // mark remind
            toGrab -= copied;
            start += copied;
        }

        return data;
    }

    public void write(final int address, final byte[] data, int dataSize, final boolean limited) {

        if (data.length < dataSize)
            dataSize = data.length;

        if (!limited)
            extend(address, dataSize);

        int chunkIndex = address / CHUNK_SIZE;
        int chunkOffset = address % CHUNK_SIZE;

        int toCapture = 0;
        if (limited)
            toCapture = (address + dataSize > softSize) ? softSize - address : dataSize;
        else
            toCapture = dataSize;

        int start = 0;
        while (toCapture > 0) {
            final int captured = captureMax(chunkIndex, chunkOffset, toCapture, data, start);

            // capture next chunk
            ++chunkIndex;
            chunkOffset = 0;

            // mark remind
            toCapture -= captured;
            start += captured;
        }

        if (programListener != null) programListener.onMemoryWrite(address, data, dataSize);
    }


    public void extendAndWrite(final int address, final int allocSize, final byte[] data) {
        extend(address, allocSize);
        write(address, data, data.length, false);
    }

    public void extend(final int address, final int size) {
        if (size <= 0) return;

        final int newSize = address + size;

        int toAllocate = newSize - internalSize();
        if (toAllocate > 0) {
            addChunks((int) ceil((double) toAllocate / CHUNK_SIZE));
        }

        toAllocate = newSize - softSize;
        if (toAllocate > 0) {
            toAllocate = (int) ceil((double) toAllocate / WORD_SIZE) * WORD_SIZE;
            softSize += toAllocate;

            if (programListener != null) programListener.onMemoryExtend(toAllocate);
        }
    }

    public DataWord readWord(final int address) {
        return new DataWord(read(address, 32));
    }

    // just access expecting all data valid
    public byte readByte(final int address) {

        final int chunkIndex = address / CHUNK_SIZE;
        final int chunkOffset = address % CHUNK_SIZE;

        final byte[] chunk = chunks.get(chunkIndex);

        return chunk[chunkOffset];
    }

    @Override
    public String toString() {

        final StringBuilder memoryData = new StringBuilder();
        final StringBuilder firstLine = new StringBuilder();
        final StringBuilder secondLine = new StringBuilder();

        for (int i = 0; i < softSize; ++i) {

            final byte value = readByte(i);

            // Check if value is ASCII
            final String character = ((byte) 0x20 <= value && value <= (byte) 0x7e) ? new String(new byte[]{value}) : "?";
            firstLine.append(character).append("");
            secondLine.append(oneByteToHexString(value)).append(" ");

            if ((i + 1) % 8 == 0) {
                final String tmp = format("%4s", Integer.toString(i - 7, 16)).replace(" ", "0");
                memoryData.append("").append(tmp).append(" ");
                memoryData.append(firstLine).append(" ");
                memoryData.append(secondLine);
                if (i + 1 < softSize) memoryData.append("\n");
                firstLine.setLength(0);
                secondLine.setLength(0);
            }
        }

        return memoryData.toString();
    }

    public int size() {
        return softSize;
    }

    public int internalSize() {
        return chunks.size() * CHUNK_SIZE;
    }

    public List<byte[]> getChunks() {
        return new LinkedList<>(chunks);
    }

    private int captureMax(final int chunkIndex, final int chunkOffset, final int size, final byte[] src, final int srcPos) {

        final byte[] chunk = chunks.get(chunkIndex);
        final int toCapture = min(size, chunk.length - chunkOffset);

        System.arraycopy(src, srcPos, chunk, chunkOffset, toCapture);
        return toCapture;
    }

    private int grabMax(final int chunkIndex, final int chunkOffset, final int size, final byte[] dest, final int destPos) {

        final byte[] chunk = chunks.get(chunkIndex);
        final int toGrab = min(size, chunk.length - chunkOffset);

        System.arraycopy(chunk, chunkOffset, dest, destPos, toGrab);

        return toGrab;
    }

    private void addChunks(final int num) {
        for (int i = 0; i < num; ++i) {
            chunks.add(new byte[CHUNK_SIZE]);
        }
    }
}
