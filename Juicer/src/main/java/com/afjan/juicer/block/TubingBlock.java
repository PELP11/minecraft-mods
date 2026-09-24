package com.afjan.juicer.block;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Juice Tubing: a low copper pipe that automatically connects to neighbouring tubing, Fruit Mixers and Juice
 * Infusers. The connection state is only visual; {@link com.afjan.juicer.block.entity.TubeNetwork} does the transport.
 */
public class TubingBlock extends Block {
    public static final EnumProperty<TubeConnection> NORTH = EnumProperty.create("north", TubeConnection.class);
    public static final EnumProperty<TubeConnection> EAST = EnumProperty.create("east", TubeConnection.class);
    public static final EnumProperty<TubeConnection> SOUTH = EnumProperty.create("south", TubeConnection.class);
    public static final EnumProperty<TubeConnection> WEST = EnumProperty.create("west", TubeConnection.class);
    public static final EnumProperty<TubeConnection> UP = EnumProperty.create("up", TubeConnection.class);
    public static final EnumProperty<TubeConnection> DOWN = EnumProperty.create("down", TubeConnection.class);
    public static final Map<Direction, EnumProperty<TubeConnection>> PROPERTY_BY_DIRECTION = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
            Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));

    private static final VoxelShape CORE = Block.box(5.5, 0.5, 5.5, 10.5, 5.5, 10.5);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(6.0, 1.0, 0.0, 10.0, 5.0, 5.5),
            Direction.SOUTH, Block.box(6.0, 1.0, 10.5, 10.0, 5.0, 16.0),
            Direction.WEST, Block.box(0.0, 1.0, 6.0, 5.5, 5.0, 10.0),
            Direction.EAST, Block.box(10.5, 1.0, 6.0, 16.0, 5.0, 10.0),
            Direction.UP, Block.box(6.0, 5.5, 6.0, 10.0, 16.0, 10.0),
            Direction.DOWN, Block.box(6.0, 0.0, 6.0, 10.0, 0.5, 10.0)));

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public TubingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (EnumProperty<TubeConnection> property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, TubeConnection.NONE);
        }
        this.registerDefaultState(state);
        for (BlockState possible : this.stateDefinition.getPossibleStates()) {
            VoxelShape shape = CORE;
            for (Map.Entry<Direction, EnumProperty<TubeConnection>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                if (possible.getValue(entry.getValue()).isConnected()) {
                    shape = Shapes.or(shape, ARM_SHAPES.get(entry.getKey()));
                }
            }
            this.shapes.put(possible, shape.optimize());
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    public static TubeConnection connectionTo(BlockState neighbour) {
        Block block = neighbour.getBlock();
        if (block instanceof TubingBlock) {
            return TubeConnection.TUBE;
        }
        if (block instanceof MixerBlock || block instanceof InfuserBlock) {
            return TubeConnection.MACHINE;
        }
        return TubeConnection.NONE;
    }

    /** The state this tube should have at {@code pos}, given its current neighbours. */
    public BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Map.Entry<Direction, EnumProperty<TubeConnection>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
            state = state.setValue(entry.getValue(), connectionTo(level.getBlockState(pos.relative(entry.getKey()))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.withConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour), connectionTo(neighbourState));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.getOrDefault(state, CORE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            rotated = rotated.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(direction)),
                    state.getValue(PROPERTY_BY_DIRECTION.get(direction)));
        }
        return rotated;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mirrored = mirrored.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(direction)),
                    state.getValue(PROPERTY_BY_DIRECTION.get(direction)));
        }
        return mirrored;
    }
}
