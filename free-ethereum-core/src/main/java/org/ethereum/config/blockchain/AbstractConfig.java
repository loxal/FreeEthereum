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

package org.ethereum.config.blockchain;

import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.mine.EthashMiner;
import org.ethereum.mine.MinerIfc;
import org.ethereum.util.BIUtil;
import org.ethereum.validator.BlockHeaderValidator;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.program.Program;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BlockchainForkConfig is also implemented by this class - its (mostly testing) purpose to represent
 * the specific config for all blocks on the chain (kinda constant config).
 *
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public abstract class AbstractConfig implements BlockchainConfig, BlockchainNetConfig {
    private static final GasCost GAS_COST = new GasCost();
    private final List<Pair<Long, BlockHeaderValidator>> headerValidators = new ArrayList<>();
    Constants constants;
    private MinerIfc miner;

    AbstractConfig() {
        this(new Constants());
    }

    AbstractConfig(final Constants constants) {
        this.constants = constants;
    }

    @Override
    public Constants getConstants() {
        return constants;
    }

    @NotNull
    @Override
    public BlockchainConfig getConfigForBlock(final long blockHeader) {
        return this;
    }

    @Override
    public Constants getCommonConstants() {
        return getConstants();
    }

    @Override
    public MinerIfc getMineAlgorithm(final SystemProperties config) {
        if (miner == null) miner = new EthashMiner(config);
        return miner;
    }

    @Override
    public BigInteger calcDifficulty(final BlockHeader curBlock, final BlockHeader parent) {
        final BigInteger pd = parent.getDifficultyBI();
        final BigInteger quotient = pd.divide(getConstants().getDifficultyBoundDivisor());

        final BigInteger sign = getCalcDifficultyMultiplier(curBlock, parent);

        final BigInteger fromParent = pd.add(quotient.multiply(sign));
        BigInteger difficulty = BIUtil.INSTANCE.max(getConstants().getMinimumDifficulty(), fromParent);

        final int explosion = getExplosion(curBlock, parent);

        if (explosion >= 0) {
            difficulty = BIUtil.INSTANCE.max(getConstants().getMinimumDifficulty(), difficulty.add(BigInteger.ONE.shiftLeft(explosion)));
        }

        return difficulty;
    }

    protected abstract BigInteger getCalcDifficultyMultiplier(BlockHeader curBlock, BlockHeader parent);

    private int getExplosion(final BlockHeader curBlock, final BlockHeader parent) {
        final int periodCount = (int) (curBlock.getNumber() / getConstants().getExpDifficultyPeriod());
        return periodCount - 2;
    }

    @Override
    public boolean acceptTransactionSignature(final Transaction tx) {
        return Objects.equals(tx.getChainId(), getChainId());
    }

    @Override
    public String validateTransactionChanges(final BlockStore blockStore, final Block curBlock, final Transaction tx,
                                             final Repository repository) {
        return null;
    }

    @Override
    public void hardForkTransfers(final Block block, final Repository repo) {
    }

    @Override
    public byte[] getExtraData(final byte[] minerExtraData, final long blockNumber) {
        return minerExtraData;
    }

    @Override
    public List<Pair<Long, BlockHeaderValidator>> headerValidators() {
        return headerValidators;
    }


    @Override
    public GasCost getGasCost() {
        return GAS_COST;
    }

    @Override
    public DataWord getCallGas(final OpCode op, final DataWord requestedGas, final DataWord availableGas) throws Program.OutOfGasException {
        if (requestedGas.compareTo(availableGas) > 0) {
            throw Program.Exception.notEnoughOpGas(op, requestedGas, availableGas);
        }
        return requestedGas.clone();
    }

    @Override
    public DataWord getCreateGas(final DataWord availableGas) {
        return availableGas;
    }

    @Override
    public boolean eip161() {
        return false;
    }

    @Override
    public Integer getChainId() {
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
