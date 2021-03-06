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

package org.ethereum.net.rlpx;

import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.spongycastle.util.encoders.Hex;

import static org.ethereum.util.ByteUtil.longToBytes;
import static org.ethereum.util.ByteUtil.stripLeadingZeroes;

public class PongMessage extends Message {

    private byte[] token; // token is the MDC of the ping
    private long expires;

    public static PongMessage create(final byte[] token, final Node toNode, final ECKey privKey) {

        final long expiration = 90 * 60 + System.currentTimeMillis() / 1000;

        final byte[] rlpToList = toNode.getBriefRLP();

        /* RLP Encode data */
        final byte[] rlpToken = RLP.encodeElement(token);
        final byte[] tmpExp = longToBytes(expiration);
        final byte[] rlpExp = RLP.encodeElement(stripLeadingZeroes(tmpExp));

        final byte[] type = new byte[]{2};
        final byte[] data = RLP.encodeList(rlpToList, rlpToken, rlpExp);

        final PongMessage pong = new PongMessage();
        pong.encode(type, data, privKey);

        pong.token = token;
        pong.expires = expiration;

        return pong;
    }

    public static PongMessage create(final byte[] token, final ECKey privKey) {
        return create(token, privKey, 3 + System.currentTimeMillis() / 1000);
    }

    static PongMessage create(final byte[] token, final ECKey privKey, final long expiration) {

        /* RLP Encode data */
        final byte[] rlpToken = RLP.encodeElement(token);
        final byte[] rlpExp = RLP.encodeElement(ByteUtil.longToBytes(expiration));

        final byte[] type = new byte[]{2};
        final byte[] data = RLP.encodeList(rlpToken, rlpExp);

        final PongMessage pong = new PongMessage();
        pong.encode(type, data, privKey);

        pong.token = token;
        pong.expires = expiration;

        return pong;
    }


    @Override
    public void parse(final byte[] data) {
        final RLPList list = (RLPList) RLP.decode2OneItem(data, 0);

        this.token = list.get(0).getRLPData();
        final RLPItem expires = (RLPItem) list.get(1);
        this.expires = ByteUtil.byteArrayToLong(expires.getRLPData());
    }


    public byte[] getToken() {
        return token;
    }

    public long getExpires() {
        return expires;
    }

    @Override
    public String toString() {
        final long currTime = System.currentTimeMillis() / 1000;

        final String out = String.format("[PongMessage] \n token: %s \n expires in %d seconds \n %s\n",
                Hex.toHexString(token), (expires - currTime), super.toString());

        return out;
    }
}
