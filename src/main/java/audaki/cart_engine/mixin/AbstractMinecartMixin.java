package audaki.cart_engine.mixin;

// Fabric -> Forge compatible imports

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;

// Mixin info for Forge grabbed from https://darkhax.net/2020/07/mixins
@Mixin(value = AbstractMinecart.class, priority = 500) // lower value, higher priority - apply first so other mods can still mixin
public abstract class AbstractMinecartMixin extends Entity {
    public AbstractMinecartMixin(EntityType<?> pEntityType, Level pLevel) { super(pEntityType, pLevel); }

    @Shadow
    protected abstract boolean isRedstoneConductor(BlockPos pos);

    @Shadow
    protected abstract Vec3 getPos(double x, double y, double z);

    @Shadow
    protected abstract void applyNaturalSlowdown();

    @Shadow
    protected abstract double getMaxSpeed();

    @Shadow
    private static Pair<Vec3i, Vec3i> exits(RailShape shape) { return null; }

    /**
     * @author audaki
     * @reason modify minecart behavior
     */
    @Overwrite
    public void moveAlongTrack(BlockPos pos, BlockState state) {
        this.resetFallDistance();
        double d = this.getX();
        double e = this.getY();
        double f = this.getZ();

        Vec3 vec3d = this.getPos(d, e, f);

        e = pos.getY();
        boolean onPoweredRail = false;
        boolean onBrakeRail   = false;
        if (state.is(Blocks.POWERED_RAIL)) {
            onPoweredRail = state.getValue(PoweredRailBlock.POWERED);
            onBrakeRail = !onPoweredRail;
        }

        double g = 0.0078125D;
        if (this.isInWater()) {
            g *= 0.2D;
        }

        Vec3 velocity = this.getDeltaMovement();
        RailShape railShape = state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty()); // TODO: Forge says to use getRailDirection but I don't know how to implement those params
        switch (railShape) {
            case ASCENDING_EAST -> {
                this.setDeltaMovement(velocity.add(-g, 0.0D, 0.0D));
                ++e;
            }
            case ASCENDING_WEST -> {
                this.setDeltaMovement(velocity.add(g, 0.0D, 0.0D));
                ++e;
            }
            case ASCENDING_NORTH -> {
                this.setDeltaMovement(velocity.add(0.0D, 0.0D, g));
                ++e;
            }
            case ASCENDING_SOUTH -> {
                this.setDeltaMovement(velocity.add(0.0D, 0.0D, -g));
                ++e;
            }
        }

        velocity = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> adjacentRailPositions = exits(railShape);
        Vec3i adjacentRail1RelPos = adjacentRailPositions.getFirst();
        Vec3i adjacentRail2RelPos = adjacentRailPositions.getSecond();
        double h = adjacentRail2RelPos.getX() - adjacentRail1RelPos.getX();
        double i = adjacentRail2RelPos.getZ() - adjacentRail1RelPos.getZ();
        double j = Math.sqrt(h * h + i * i);
        double k = velocity.x * h + velocity.z * i;
        if (k < 0.0D) {
            h = -h;
            i = -i;
        }


        double vanillaMaxHorizontalMovementPerTick = 0.4D;
        double horizontalMomentumPerTick = this.getDeltaMovement().horizontalDistance();

        Supplier<Double> calculateMaxHorizontalMovementPerTick = () -> {
            final double fallbackSpeedFactor = 1.15D;
            double fallback = this.getMaxSpeed();

            if (!this.isVehicle())
                return fallback;

            if (horizontalMomentumPerTick < vanillaMaxHorizontalMovementPerTick)
                return fallback;

            fallback *= fallbackSpeedFactor;

            boolean hasEligibleShape = railShape == RailShape.NORTH_SOUTH || railShape == RailShape.EAST_WEST;
            if (!hasEligibleShape)
                return fallback;

            boolean hasEligibleType = state.is(Blocks.RAIL) || (state.is(Blocks.POWERED_RAIL) && state.getValue(PoweredRailBlock.POWERED));
            if (!hasEligibleType)
                return fallback;

            AtomicInteger eligibleNeighbors = new AtomicInteger();

            HashSet<BlockPos> checkedPositions = new HashSet<>();
            checkedPositions.add(pos);

            BiFunction<BlockPos, RailShape, ArrayList<Pair<BlockPos, RailShape>>> checkNeighbors = (cPos, cRailShape) -> {
                Pair<Vec3i, Vec3i> cAdjPosDiff = exits(cRailShape);
                ArrayList<Pair<BlockPos, RailShape>> newNeighbors = new ArrayList<>();

                BlockPos n1Pos = cPos.offset(cAdjPosDiff.getFirst());

                if (!checkedPositions.contains(n1Pos)) {

                    BlockState n1State = this.level.getBlockState(n1Pos);
                    boolean n1HasEligibleType = n1State.is(Blocks.RAIL) || (n1State.is(Blocks.POWERED_RAIL) && n1State.getValue(PoweredRailBlock.POWERED));
                    if (!n1HasEligibleType)
                        return new ArrayList<>();

                    RailShape n1RailShape = n1State.getValue(((BaseRailBlock) n1State.getBlock()).getShapeProperty()); // TODO: Forge says to use getRailDirection but I don't know how to implement those params
                    if (n1RailShape != railShape)
                        return new ArrayList<>();

                    checkedPositions.add(n1Pos);
                    eligibleNeighbors.incrementAndGet();
                    newNeighbors.add(Pair.of(n1Pos, n1RailShape));
                }

                BlockPos n2Pos = cPos.offset(cAdjPosDiff.getSecond());
                if (!checkedPositions.contains(n2Pos)) {

                    BlockState n2State = this.level.getBlockState(n2Pos);
                    boolean n2HasEligibleType = n2State.is(Blocks.RAIL) || (n2State.is(Blocks.POWERED_RAIL) && n2State.getValue(PoweredRailBlock.POWERED));
                    if (!n2HasEligibleType)
                        return new ArrayList<>();

                    RailShape n2RailShape = n2State.getValue(((BaseRailBlock) n2State.getBlock()).getShapeProperty()); // TODO: Forge says to use getRailDirection but I don't know how to implement those params

                    if (n2RailShape != railShape)
                        return new ArrayList<>();

                    checkedPositions.add(n2Pos);
                    eligibleNeighbors.incrementAndGet();
                    newNeighbors.add(Pair.of(n2Pos, n2RailShape));
                }

                return newNeighbors;
            };


            ArrayList<Pair<BlockPos, RailShape>> newNeighbors = checkNeighbors.apply(pos, railShape);

            while (!newNeighbors.isEmpty() && eligibleNeighbors.get() < 16) {
                ArrayList<Pair<BlockPos, RailShape>> tempNewNeighbors = new ArrayList<>(newNeighbors);
                newNeighbors.clear();

                for (Pair<BlockPos, RailShape> newNeighbor : tempNewNeighbors) {
                    ArrayList<Pair<BlockPos, RailShape>> result = checkNeighbors.apply(newNeighbor.getFirst(), newNeighbor.getSecond());

                    if (result.isEmpty()) {
                        newNeighbors.clear();
                        break;
                    }

                    newNeighbors.addAll(result);
                }
            }

            int eligibleForwardRailTrackCount = eligibleNeighbors.get() / 2;

            if (eligibleForwardRailTrackCount <= 1)
                return fallback;

            return (2.01D + eligibleForwardRailTrackCount * 4.0D) / 20.0D;
        };

        double maxHorizontalMovementPerTick = calculateMaxHorizontalMovementPerTick.get();
        double maxHorizontalMomentumPerTick = Math.max(maxHorizontalMovementPerTick * 5.0D, 4.2D);

//        if (this.hasPassengers() && this.getVelocity().horizontalLength() > 0.09) {
//            System.out.println(maxHorizontalMovementPerTick + " - " + maxHorizontalMomentumPerTick);
//        }

        double l = Math.min(maxHorizontalMomentumPerTick, velocity.horizontalDistance());
        this.setDeltaMovement(new Vec3(l * h / j, velocity.y, l * i / j));


//        if (this.hasPassengers() && this.getVelocity().horizontalLength() > 0.09 && this.world.getServer() != null && this.world.getServer().getTicks() % 3 == 0) {
//            System.out.println("Momentum: " + (int) this.getX() + " -> " + this.getVelocity().horizontalLength() + " m/t");
//        }

        Entity entity = this.getFirstPassenger();
        if (entity instanceof Player) {
            Vec3 playerVelocity = entity.getDeltaMovement();
            double m = playerVelocity.horizontalDistanceSqr();
            double n = this.getDeltaMovement().horizontalDistanceSqr();
            if (m > 1.0E-4D && n < 0.01D) {
                this.setDeltaMovement(this.getDeltaMovement().add(playerVelocity.x * 0.1D, 0.0D, playerVelocity.z * 0.1D));
                onBrakeRail = false;
            }
        }

        double p;
        if (onBrakeRail) {
            p = this.getDeltaMovement().horizontalDistance();
            if (p < 0.03D) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                double brakeFactor = 0.5D;

                if (horizontalMomentumPerTick > 4.0D * vanillaMaxHorizontalMovementPerTick) {
                    brakeFactor = Math.pow(brakeFactor, 1.0D + ((horizontalMomentumPerTick - 3.99D * vanillaMaxHorizontalMovementPerTick) / 1.2D));
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply(brakeFactor, 0.0D, brakeFactor));
            }
        }

        p = (double) pos.getX() + 0.5D + (double) adjacentRail1RelPos.getX() * 0.5D;
        double q = (double) pos.getZ() + 0.5D + (double) adjacentRail1RelPos.getZ() * 0.5D;
        double r = (double) pos.getX() + 0.5D + (double) adjacentRail2RelPos.getX() * 0.5D;
        double s = (double) pos.getZ() + 0.5D + (double) adjacentRail2RelPos.getZ() * 0.5D;
        h = r - p;
        i = s - q;
        double x;
        double v;
        double w;
        if (h == 0.0D) {
            x = f - (double) pos.getZ();
        } else if (i == 0.0D) {
            x = d - (double) pos.getX();
        } else {
            v = d - p;
            w = f - q;
            x = (v * h + w * i) * 2.0D;
        }

        d = p + h * x;
        f = q + i * x;
        this.setPos(d, e, f);
        v = this.isVehicle() ? 0.75D : 1.0D;

        w = maxHorizontalMovementPerTick;

        velocity = this.getDeltaMovement();
        Vec3 movement = new Vec3(Mth.clamp(v * velocity.x, -w, w), 0.0D, Mth.clamp(v * velocity.z, -w, w));

        this.move(MoverType.SELF, movement);

        if (adjacentRail1RelPos.getY() != 0 && Mth.floor(this.getX()) - pos.getX() == adjacentRail1RelPos.getX() && Mth.floor(this.getZ()) - pos.getZ() == adjacentRail1RelPos.getZ()) {
            this.setPos(this.getX(), this.getY() + (double) adjacentRail1RelPos.getY(), this.getZ());
        } else if (adjacentRail2RelPos.getY() != 0 && Mth.floor(this.getX()) - pos.getX() == adjacentRail2RelPos.getX() && Mth.floor(this.getZ()) - pos.getZ() == adjacentRail2RelPos.getZ()) {
            this.setPos(this.getX(), this.getY() + (double) adjacentRail2RelPos.getY(), this.getZ());
        }

        this.applyNaturalSlowdown();
        Vec3 vec3d4 = this.getPos(this.getX(), this.getY(), this.getZ());
        Vec3 vec3d7;
        double af;
        if (vec3d4 != null && vec3d != null) {
            double aa = (vec3d.y - vec3d4.y) * 0.05D;
            vec3d7 = this.getDeltaMovement();
            af = vec3d7.horizontalDistance();
            if (af > 0.0D) {
                this.setDeltaMovement(vec3d7.multiply((af + aa) / af, 1.0D, (af + aa) / af));
            }

            this.setPos(this.getX(), vec3d4.y, this.getZ());
        }

        int ac = Mth.floor(this.getX());
        int ad = Mth.floor(this.getZ());
        if (ac != pos.getX() || ad != pos.getZ()) {
            vec3d7 = this.getDeltaMovement();
            af = vec3d7.horizontalDistance();
            this.setDeltaMovement(
                    af * Mth.clamp((double) (ac - pos.getX()), -1.0D, 1.0D),
                    vec3d7.y,
                    af * Mth.clamp((double) (ad - pos.getZ()), -1.0D, 1.0D));
        }

        if (onPoweredRail) {
            vec3d7 = this.getDeltaMovement();
            double momentum = vec3d7.horizontalDistance();
            final double basisAccelerationPerTick = 0.021D;
            if (momentum > 0.01D) {

                if (this.isVehicle()) {
                    // Based on a 10 ticks per second basis spent per powered block we calculate a fair acceleration per tick
                    // due to spending less ticks per powered block on higher speeds (and even skipping blocks)
                    final double basisTicksPerSecond = 10.0D;
                    // Tps = Ticks per second
                    final double tickMovementForBasisTps = 1.0D / basisTicksPerSecond;
                    final double maxSkippedBlocksToConsider = 3.0D;


                    double acceleration = basisAccelerationPerTick;
                    final double distanceMovedHorizontally = movement.horizontalDistance();

                    if (distanceMovedHorizontally > tickMovementForBasisTps) {
                        acceleration *= Math.min((1.0D + maxSkippedBlocksToConsider) * basisTicksPerSecond, distanceMovedHorizontally / tickMovementForBasisTps);

                        // Add progressively slower (or faster) acceleration for higher speeds;
                        double highspeedFactor = 1.0D + Mth.clamp(-0.45D * (distanceMovedHorizontally / tickMovementForBasisTps / basisTicksPerSecond), -0.7D, 2.0D);
                        acceleration *= highspeedFactor;
                    }
                    this.setDeltaMovement(vec3d7.add(acceleration * (vec3d7.x / momentum), 0.0D, acceleration * (vec3d7.z / momentum)));
                }
                else {
                    this.setDeltaMovement(vec3d7.add(vec3d7.x / momentum * 0.06D, 0.0D, vec3d7.z / momentum * 0.06D));
                }


            } else {
                Vec3 vec3d8 = this.getDeltaMovement();
                double ah = vec3d8.x;
                double ai = vec3d8.z;
                final double railStopperAcceleration = basisAccelerationPerTick * 16.0D;
                if (railShape == RailShape.EAST_WEST) {
                    if (this.isRedstoneConductor(pos.west())) {
                        ah = railStopperAcceleration;
                    } else if (this.isRedstoneConductor(pos.east())) {
                        ah = -railStopperAcceleration;
                    }
                } else {
                    if (railShape != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.isRedstoneConductor(pos.north())) {
                        ai = railStopperAcceleration;
                    } else if (this.isRedstoneConductor(pos.south())) {
                        ai = -railStopperAcceleration;
                    }
                }

                this.setDeltaMovement(ah, vec3d8.y, ai);
            }
        }
    }
}
