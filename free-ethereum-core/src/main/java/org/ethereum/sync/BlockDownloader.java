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

package org.ethereum.sync;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.ethereum.core.*;
import org.ethereum.net.server.Channel;
import org.ethereum.validator.BlockHeaderValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

abstract class BlockDownloader {

    private final static Logger logger = LoggerFactory.getLogger("sync");
    // Max number of Blocks / Headers in one request
    private static final int MAX_IN_REQUEST = 192;
    private final BlockHeaderValidator headerValidator;
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    boolean headersDownloadComplete;
    private int blockQueueLimit = 2000;
    private int headerQueueLimit = 10000;
    private SyncPool pool;
    private SyncQueueIfc syncQueue;
    private boolean headersDownload = true;
    private boolean blockBodiesDownload = true;
    private CountDownLatch receivedHeadersLatch = new CountDownLatch(0);
    private CountDownLatch receivedBlocksLatch = new CountDownLatch(0);
    private Thread getHeadersThread;
    private Thread getBodiesThread;
    private boolean downloadComplete;

    BlockDownloader(final BlockHeaderValidator headerValidator) {
        this.headerValidator = headerValidator;
    }

    protected abstract void pushBlocks(List<BlockWrapper> blockWrappers);
    protected abstract void pushHeaders(List<BlockHeaderWrapper> headers);
    protected abstract int getBlockQueueFreeSize();

    void finishDownload() {
    }

    public boolean isDownloadComplete() {
        return downloadComplete;
    }

    void setBlockBodiesDownload(final boolean blockBodiesDownload) {
        this.blockBodiesDownload = blockBodiesDownload;
    }

    void setHeadersDownload(final boolean headersDownload) {
        this.headersDownload = headersDownload;
    }

    void init(final SyncQueueIfc syncQueue, final SyncPool pool) {
        this.syncQueue = syncQueue;
        this.pool = pool;

        logger.info("Initializing BlockDownloader.");

        if (headersDownload) {
            getHeadersThread = new Thread(this::headerRetrieveLoop, "SyncThreadHeaders");
            getHeadersThread.start();
        }

        if (blockBodiesDownload) {
            getBodiesThread = new Thread(this::blockRetrieveLoop, "SyncThreadBlocks");
            getBodiesThread.start();
        }
    }

    void stop() {
        if (getHeadersThread != null) getHeadersThread.interrupt();
        if (getBodiesThread != null) getBodiesThread.interrupt();
        stopLatch.countDown();
    }

    public void waitForStop() {
        try {
            stopLatch.await();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void setHeaderQueueLimit(final int headerQueueLimit) {
        this.headerQueueLimit = headerQueueLimit;
    }

    int getBlockQueueLimit() {
        return blockQueueLimit;
    }

    public void setBlockQueueLimit(final int blockQueueLimit) {
        this.blockQueueLimit = blockQueueLimit;
    }

    private void headerRetrieveLoop() {
        List<SyncQueueIfc.HeadersRequest> hReq = emptyList();
        while(!Thread.currentThread().isInterrupted()) {
            try {
                    if (hReq.isEmpty()) {
                        synchronized (this) {
                            hReq = syncQueue.requestHeaders(MAX_IN_REQUEST, 128, headerQueueLimit);
                            if (hReq == null) {
                                logger.info("Headers download complete.");
                                headersDownloadComplete = true;
                                if (!blockBodiesDownload) {
                                    finishDownload();
                                    downloadComplete = true;
                                }
                                return;
                            }
                            final StringBuilder l = new StringBuilder("##########  New header requests (" + hReq.size() + "):\n");
                            for (final SyncQueueIfc.HeadersRequest request : hReq) {
                                l.append("    ").append(request).append("\n");
                            }
                            logger.debug(l.toString());
                        }
                    }
                    int reqHeadersCounter = 0;
                for (final Iterator<SyncQueueIfc.HeadersRequest> it = hReq.iterator(); it.hasNext(); ) {
                    final SyncQueueIfc.HeadersRequest headersRequest = it.next();

                        final Channel any = getAnyPeer();

                        if (any == null) {
                            logger.debug("headerRetrieveLoop: No IDLE peers found");
                            break;
                        } else {
                            logger.debug("headerRetrieveLoop: request headers (" + headersRequest.getStart() + ") from " + any.getNode());
                            final ListenableFuture<List<BlockHeader>> futureHeaders = headersRequest.getHash() == null ?
                                    any.getEthHandler().sendGetBlockHeaders(headersRequest.getStart(), headersRequest.getCount(), headersRequest.isReverse()) :
                                    any.getEthHandler().sendGetBlockHeaders(headersRequest.getHash(), headersRequest.getCount(), headersRequest.getStep(), headersRequest.isReverse());
                            if (futureHeaders != null) {
                                Futures.addCallback(futureHeaders, new FutureCallback<List<BlockHeader>>() {
                                    @Override
                                    public void onSuccess(final List<BlockHeader> result) {
                                        if (validateAndAddHeaders(result, any.getNodeId())) {
                                            onFailure(new RuntimeException("Received headers validation failed"));
                                        }
                                    }

                                    @Override
                                    public void onFailure(final Throwable t) {
                                        logger.debug("Error receiving headers. Dropping the peer.", t);
                                        any.getEthHandler().dropConnection();
                                    }
                                });
                                it.remove();
                                reqHeadersCounter++;
                            }
                        }
                    }
                    receivedHeadersLatch = new CountDownLatch(max(reqHeadersCounter / 2, 1));

                receivedHeadersLatch.await(isSyncDone() ? 10000 : 500, TimeUnit.MILLISECONDS);

            } catch (final InterruptedException e) {
                break;
            } catch (final Exception e) {
                logger.error("Unexpected: ", e);
            }
        }
    }

    private void blockRetrieveLoop() {
        class BlocksCallback implements FutureCallback<List<Block>> {
            private Channel peer;

            public BlocksCallback(final Channel peer) {
                this.peer = peer;
            }

            @Override
            public void onSuccess(final List<Block> result) {
                addBlocks(result, peer.getNodeId());
            }

            @Override
            public void onFailure(final Throwable t) {
                logger.debug("Error receiving Blocks. Dropping the peer.", t);
                peer.getEthHandler().dropConnection();
            }
        }

        List<SyncQueueIfc.BlocksRequest> bReqs = emptyList();
        while(!Thread.currentThread().isInterrupted()) {
            try {
                if (bReqs.isEmpty()) {
                    bReqs = syncQueue.requestBlocks(16 * 1024).split(MAX_IN_REQUEST);
                }

                if (bReqs.isEmpty() && headersDownloadComplete) {
                    logger.info("Block download complete.");
                    finishDownload();
                    downloadComplete = true;
                    return;
                }

                final int blocksToAsk = getBlockQueueFreeSize();
                if (blocksToAsk > MAX_IN_REQUEST) {
//                    SyncQueueIfc.BlocksRequest bReq = syncQueue.requestBlocks(maxBlocks);

                    if (bReqs.size() == 1 && bReqs.get(0).getBlockHeaders().size() <= 3) {
                        // new blocks are better to request from the header senders first
                        // to get more chances to receive block body promptly
                        for (final BlockHeaderWrapper blockHeaderWrapper : bReqs.get(0).getBlockHeaders()) {
                            final Channel channel = pool.getByNodeId(blockHeaderWrapper.getNodeId());
                            if (channel != null) {
                                final ListenableFuture<List<Block>> futureBlocks =
                                        channel.getEthHandler().sendGetBlockBodies(singletonList(blockHeaderWrapper));
                                if (futureBlocks != null) {
                                    Futures.addCallback(futureBlocks, new BlocksCallback(channel));
                                }
                            }
                        }
                    }

                    final int maxRequests = blocksToAsk / MAX_IN_REQUEST;
                    final int REQUESTS = 32;
                    final int maxBlocks = MAX_IN_REQUEST * Math.min(maxRequests, REQUESTS);
                    int reqBlocksCounter = 0;
                    int blocksRequested = 0;
                    final Iterator<SyncQueueIfc.BlocksRequest> it = bReqs.iterator();
                    while (it.hasNext() && blocksRequested < maxBlocks) {
//                    for (SyncQueueIfc.BlocksRequest blocksRequest : bReq.split(MAX_IN_REQUEST)) {
                        final SyncQueueIfc.BlocksRequest blocksRequest = it.next();
                        final Channel any = getAnyPeer();
                        if (any == null) {
                            logger.debug("blockRetrieveLoop: No IDLE peers found");
                            break;
                        } else {
                            logger.debug("blockRetrieveLoop: Requesting " + blocksRequest.getBlockHeaders().size() + " blocks from " + any.getNode());
                            final ListenableFuture<List<Block>> futureBlocks =
                                    any.getEthHandler().sendGetBlockBodies(blocksRequest.getBlockHeaders());
                            blocksRequested += blocksRequest.getBlockHeaders().size();
                            if (futureBlocks != null) {
                                Futures.addCallback(futureBlocks, new BlocksCallback(any));
                                reqBlocksCounter++;
                                it.remove();
                            }
                        }
                    }
                    receivedBlocksLatch = new CountDownLatch(max(reqBlocksCounter - 2, 1));
                } else {
                    logger.debug("blockRetrieveLoop: BlockQueue is full");
                    receivedBlocksLatch = new CountDownLatch(1);
                }
                receivedBlocksLatch.await(200, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                break;
            } catch (final Exception e) {
                logger.error("Unexpected: ", e);
            }
        }
    }

    /**
     * Adds a list of blocks to the queue
     *
     * @param blocks block list received from remote peer and be added to the queue
     * @param nodeId nodeId of remote peer which these blocks are received from
     */
    private void addBlocks(final List<Block> blocks, final byte[] nodeId) {

        if (blocks.isEmpty()) {
            return;
        }

        synchronized (this) {
            logger.debug("Adding new " + blocks.size() + " blocks to sync queue: " +
                    blocks.get(0).getShortDescr() + " ... " + blocks.get(blocks.size() - 1).getShortDescr());

            final List<Block> newBlocks = syncQueue.addBlocks(blocks);

            final List<BlockWrapper> wrappers = new ArrayList<>();
            for (final Block b : newBlocks) {
                wrappers.add(new BlockWrapper(b, nodeId));
            }


            logger.debug("Pushing " + wrappers.size() + " blocks to import queue: " + (wrappers.isEmpty() ? "" :
                    wrappers.get(0).getBlock().getShortDescr() + " ... " + wrappers.get(wrappers.size() - 1).getBlock().getShortDescr()));

            pushBlocks(wrappers);
        }

        receivedBlocksLatch.countDown();

        if (logger.isDebugEnabled()) logger.debug(
                "Blocks waiting to be proceed: lastBlock.number: [{}]",
                blocks.get(blocks.size() - 1).getNumber()
        );
    }

    /**
     * Adds list of headers received from remote host <br>
     * Runs header validation before addition <br>
     * It also won't add headers of those blocks which are already presented in the queue
     *
     * @param headers list of headers got from remote host
     * @param nodeId remote host nodeId
     *
     * @return true if blocks passed validation and were added to the queue,
     *          otherwise it returns false
     */
    private boolean validateAndAddHeaders(final List<BlockHeader> headers, final byte[] nodeId) {

        if (headers.isEmpty()) return false;

        final List<BlockHeaderWrapper> wrappers = new ArrayList<>(headers.size());

        for (final BlockHeader header : headers) {

            if (isValid(header)) {

                if (logger.isDebugEnabled()) {
                    logger.debug("Invalid header RLP: {}", Hex.toHexString(header.getEncoded()));
                }

                return true;
            }

            wrappers.add(new BlockHeaderWrapper(header, nodeId));
        }

        synchronized (this) {
            final List<BlockHeaderWrapper> headersReady = syncQueue.addHeaders(wrappers);
            if (headersReady != null && !headersReady.isEmpty()) {
                pushHeaders(headersReady);
            }
        }

        receivedHeadersLatch.countDown();

        logger.debug("{} headers added", headers.size());

        return false;
    }

    /**
     * Runs checks against block's header. <br>
     * All these checks make sense before block is added to queue
     * in front of checks running by {@link BlockchainImpl#isValid(BlockHeader)}
     *
     * @param header block header
     * @return true if block is valid, false otherwise
     */
    boolean isValid(final BlockHeader header) {
        return !headerValidator.validateAndLog(header, logger);
    }

    Channel getAnyPeer() {
        return pool.getAnyIdle();
    }

    boolean isSyncDone() {
        return false;
    }

    void close() {
        try {
            if (pool != null) pool.close();
            stop();
        } catch (final Exception e) {
            logger.warn("Problems closing SyncManager", e);
        }
    }

}
