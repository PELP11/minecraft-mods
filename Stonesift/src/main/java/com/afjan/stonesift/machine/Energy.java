package com.afjan.stonesift.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** FE helpers. Machines keep a SimpleEnergyHandler and spend from it directly with {@link #use}. */
public final class Energy {
    private Energy() {}

    public static boolean use(SimpleEnergyHandler handler, int amount) {
        int have = handler.getAmountAsInt();
        if (have < amount) {
            return false;
        }
        handler.set(have - amount);
        return true;
    }

    /** Pushes up to maxPerSide FE into every neighbour that accepts energy. */
    public static void pushToNeighbours(ServerLevel level, BlockPos pos, EnergyHandler from, int maxPerSide) {
        for (Direction d : Direction.values()) {
            if (from.getAmountAsInt() <= 0) {
                return;
            }
            EnergyHandler to = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(d), d.getOpposite());
            if (to != null) {
                try (Transaction tx = Transaction.openRoot()) {
                    EnergyHandlerUtil.move(from, to, maxPerSide, tx);
                    tx.commit();
                }
            }
        }
    }

    /** A handler that others can only insert into (machines) or only extract from (generators). */
    public static SimpleEnergyHandler storage(int capacity, int maxInsert, int maxExtract, Runnable onChange) {
        return new SimpleEnergyHandler(capacity, maxInsert, maxExtract) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                onChange.run();
            }
        };
    }
}
