package net.mehvahdjukaar.supplementaries.common.block.blocks;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.mehvahdjukaar.moonlight.api.block.WaterBlock;
import net.mehvahdjukaar.moonlight.api.misc.ForgeOverride;
import net.mehvahdjukaar.supplementaries.common.block.IRopeConnection;
import net.mehvahdjukaar.supplementaries.common.block.ModBlockProperties;
import net.mehvahdjukaar.supplementaries.common.misc.RopeHelper;
import net.mehvahdjukaar.supplementaries.common.utils.ItemsUtil;
import net.mehvahdjukaar.supplementaries.configs.CommonConfigs;
import net.mehvahdjukaar.supplementaries.integration.CompatHandler;
import net.mehvahdjukaar.supplementaries.integration.DecoBlocksCompat;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

public class RopeBlock extends WaterBlock implements IRopeConnection {

    //TODO: make solid when player is not colliding
    public static final VoxelShape COLLISION_SHAPE = Block.box(0, 0, 0, 16, 13, 16);

    private static Map<BlockState, VoxelShape> SHAPES_MAP;

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final IntegerProperty DISTANCE = BlockStateProperties.STABILITY_DISTANCE;
    public static final BooleanProperty KNOT = ModBlockProperties.KNOT;

    public static final Map<Direction, BooleanProperty> FACING_TO_PROPERTY_MAP = Util.make(Maps.newEnumMap(Direction.class), (directions) -> {
        directions.put(Direction.NORTH, NORTH);
        directions.put(Direction.EAST, EAST);
        directions.put(Direction.SOUTH, SOUTH);
        directions.put(Direction.WEST, WEST);
        directions.put(Direction.UP, UP);
        directions.put(Direction.DOWN, DOWN);
    });

    public RopeBlock(Properties properties) {
        super(properties);
        SHAPES_MAP = this.makeShapes();
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(UP, true).setValue(DOWN, true).setValue(KNOT, false).setValue(DISTANCE, 7).setValue(WATERLOGGED, false)
                .setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return SHAPES_MAP.getOrDefault(state.setValue(DISTANCE, 0).setValue(WATERLOGGED, false), Shapes.block());
    }

    //oh boy 32k shapes. 2k by removing water and distance lol
    protected Map<BlockState, VoxelShape> makeShapes() {
        Map<BlockState, VoxelShape> shapes = new HashMap<>();

        VoxelShape down = Block.box(6, 0, 6, 10, 13, 10);
        VoxelShape up = Block.box(6, 9, 6, 10, 16, 10);
        VoxelShape north = Block.box(6, 9, 0, 10, 13, 10);
        VoxelShape south = Block.box(6, 9, 6, 10, 13, 16);
        VoxelShape west = Block.box(0, 9, 6, 10, 13, 10);
        VoxelShape east = Block.box(6, 9, 6, 16, 13, 10);
        VoxelShape knot = Block.box(6, 9, 6, 10, 13, 10);

        for (BlockState state : this.stateDefinition.getPossibleStates()) {
            if (state.getValue(WATERLOGGED) || state.getValue(DISTANCE) != 0) continue;
            VoxelShape v = Shapes.empty();
            if (state.getValue(KNOT)) v = Shapes.or(knot);
            if (state.getValue(DOWN)) v = Shapes.or(v, down);
            if (state.getValue(UP)) v = Shapes.or(v, up);
            if (state.getValue(NORTH)) v = Shapes.or(v, north);
            if (state.getValue(SOUTH)) v = Shapes.or(v, south);
            if (state.getValue(WEST)) v = Shapes.or(v, west);
            if (state.getValue(EAST)) v = Shapes.or(v, east);
            v = v.optimize();
            boolean flag = true;
            for (VoxelShape existing : shapes.values()) {
                if (existing.equals(v)) {
                    shapes.put(state, existing);
                    flag = false;
                    break;
                }
            }
            if (flag) shapes.put(state, v);
        }
        return new Object2ObjectOpenHashMap<>(shapes);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, WATERLOGGED, DISTANCE, KNOT);
    }

    @ForgeOverride
    public boolean isLadder(BlockState state, LevelReader world, BlockPos pos, LivingEntity entity) {
        return state.getValue(DOWN) && (state.getValue(UP) || entity.position().y() - pos.getY() < (13 / 16f));
    }


    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return ((!state.getValue(UP) && (context.isAbove(COLLISION_SHAPE, pos, true) || !state.getValue(DOWN)))
                || !(context instanceof EntityCollisionContext ec && ec.getEntity() instanceof LivingEntity) ?
                getShape(state, worldIn, pos, context) : Shapes.empty());

    }

    public boolean shouldConnectToDir(BlockState thisState, BlockPos currentPos, LevelReader world, Direction dir) {
        if (dir.getAxis().isHorizontal() && !CommonConfigs.Functional.ROPE_HORIZONTAL.get()) return false;
        BlockPos facingPos = currentPos.relative(dir);
        return this.shouldConnectToFace(thisState, world.getBlockState(facingPos), facingPos, dir, world);
    }

    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, LevelAccessor worldIn, BlockPos currentPos, BlockPos facingPos) {
        super.updateShape(stateIn, facing, facingState, worldIn, currentPos, facingPos);
        if (!worldIn.isClientSide()) {
            worldIn.scheduleTick(currentPos, this, 1);
        }

        if (facing == Direction.UP) {
            stateIn = stateIn.setValue(DOWN, shouldConnectToDir(stateIn, currentPos, worldIn, Direction.DOWN));
        }
        stateIn = stateIn.setValue(FACING_TO_PROPERTY_MAP.get(facing), shouldConnectToDir(stateIn, currentPos, worldIn, facing));


        if (facing == Direction.DOWN && !worldIn.isClientSide() && CompatHandler.DECO_BLOCKS) {
            DecoBlocksCompat.tryConvertingRopeChandelier(facingState, worldIn, facingPos);
        }
        //if (facing != Direction.UP && !worldIn.isClientSide() && CompatHandler.FARMERS_DELIGHT) {
        //FarmersDelightCompat.tryTomatoLogging(facingState, worldIn, facingPos,true);
        //}

        return stateIn.setValue(KNOT, hasMiddleKnot(stateIn));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean hasWater = context.getLevel().getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = this.defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(FACING_TO_PROPERTY_MAP.get(dir), shouldConnectToDir(state, pos, world, dir));
        }

        state = state.setValue(WATERLOGGED, hasWater);
        state = state.setValue(KNOT, hasMiddleKnot(state)).setValue(DISTANCE, this.getDistance(world, pos));
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!worldIn.isClientSide) {
            worldIn.scheduleTick(pos, this, 1);
            if (CompatHandler.DECO_BLOCKS) {
                BlockPos down = pos.below();
                DecoBlocksCompat.tryConvertingRopeChandelier(worldIn.getBlockState(down), worldIn, down);
            }
        }
    }

    public static boolean hasMiddleKnot(BlockState state) {
        boolean up = state.getValue(UP);
        boolean down = state.getValue(DOWN);
        boolean north = state.getValue(NORTH);
        boolean east = state.getValue(EAST);
        boolean south = state.getValue(SOUTH);
        boolean west = state.getValue(WEST);
        //not inverse
        return !((up && down && !north && !south && !east && !west)
                || (!up && !down && north && south && !east && !west)
                || (!up && !down && !north && !south && east && west));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
        return (this.getDistance(worldIn, pos) < 7);
    }

    protected int getDistance(LevelReader world, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable().move(Direction.UP);
        BlockState blockstate = world.getBlockState(mutable);
        int i = 7;
        if (blockstate.is(this)) {
            if (blockstate.getValue(DOWN) || !blockstate.getValue(UP)) {
                i = blockstate.getValue(DISTANCE);
            }
        } else if (IRopeConnection.isSupportingCeiling(mutable, world)) {
            return 0;
        }

        if (CommonConfigs.Functional.ROPE_HORIZONTAL.get()) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos facingPos = mutable.setWithOffset(pos, direction);
                BlockState sideState = world.getBlockState(facingPos);
                Block b = sideState.getBlock();
                if (b instanceof RopeBlock) {
                    i = Math.min(i, sideState.getValue(DISTANCE) + 1);
                    if (i == 1) {
                        break;
                    }
                } else if (shouldConnectToFace(this.defaultBlockState(), sideState, facingPos, direction, world)) i = 0;
            }
        }

        return i;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
        int i = this.getDistance(level, pos);
        BlockState blockstate = state.setValue(DISTANCE, i);
        if (i == 7) {
            level.destroyBlock(pos, true);
            return;
        } else if (state != blockstate) {
            level.setBlock(pos, blockstate, 3);
        }
        //fire up around me
        for (var dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (level.getBlockState(pos.relative(dir)).is(BlockTags.FIRE)) {
                level.scheduleTick(pos.relative(dir), Blocks.FIRE, 2 + level.random.nextInt(1));
                for (var d2 : Direction.Plane.HORIZONTAL) {
                    BlockPos fp = pos.relative(d2);
                    if (BaseFireBlock.canBePlacedAt(level, fp, d2.getOpposite())) {
                        BlockState fireState = BaseFireBlock.getState(level, fp);
                        if (fireState.hasProperty(FireBlock.AGE)) {
                            fireState = fireState.setValue(FireBlock.AGE, 14);
                        }
                        level.setBlockAndUpdate(fp, fireState);
                        level.scheduleTick(pos.relative(dir), Blocks.FIRE, 2 + level.random.nextInt(1));
                    }
                }
                return;
            }
        }
    }

    @ForgeOverride
    public int getFireSpreadSpeed(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? 0 : 10;
    }

    @ForgeOverride
    public int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? 0 : 100; //chance to get consumed
    }

    private static boolean findConnectedPulley(Level world, BlockPos pos, Player player, int it, Rotation rot) {
        if (it > 64) return false;
        BlockState state = world.getBlockState(pos);
        Block b = state.getBlock();
        if (b instanceof RopeBlock) {
            return findConnectedPulley(world, pos.above(), player, it + 1, rot);
        } else if (b instanceof PulleyBlock pulley && it != 0) {
            return pulley.windPulley(state, pos, world, rot, null);
        }
        return false;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand
            handIn, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(handIn);
        Item i = stack.getItem();

        if (i == this.asItem()) {
            if (hit.getDirection().getAxis() == Direction.Axis.Y || state.getValue(DOWN)) {
                //restores sheared
                if (state.getValue(UP) && !state.getValue(DOWN)) {
                    state = state.setValue(DOWN, true);
                    world.setBlock(pos, state, 0);
                }
                if (RopeHelper.addRopeDown(pos.below(), world, player, handIn, this)) {
                    SoundType soundtype = state.getSoundType();
                    world.playSound(player, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            }
            return InteractionResult.PASS;
        } else if (stack.isEmpty()) {
            if (state.getValue(UP)) {
                 if (findConnectedPulley(world, pos, player, 0, player.isShiftKeyDown() ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90)) {
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            }
            if (!player.isShiftKeyDown() && handIn == InteractionHand.MAIN_HAND) {
                if (world.getBlockState(pos.below()).getBlock() == this) {
                    if (RopeHelper.removeRopeDown(pos.below(), world, this)) {
                        world.playSound(player, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1, 0.6f);
                        if (!player.getAbilities().instabuild) {
                            ItemsUtil.addStackToExisting(player, new ItemStack(this), true);
                        }
                        return InteractionResult.sidedSuccess(world.isClientSide);
                    }
                }
            }
        } else if (i instanceof ShearsItem) {
            if (state.getValue(DOWN)) {
                if (!world.isClientSide) {
                    //TODO: proper sound event here
                    world.playSound(null, pos, SoundEvents.SNOW_GOLEM_SHEAR, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.8F, 1.3F);
                    BlockState newState = state.setValue(DOWN, false).setValue(KNOT, true);
                    world.setBlock(pos, newState, 3);
                    //refreshTextures below
                    //world.updateNeighborsAt(pos, newState.getBlock());
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }


    @Override
    public void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
        super.entityInside(state, worldIn, pos, entityIn);
        if (entityIn instanceof Arrow && !worldIn.isClientSide) {
            worldIn.destroyBlock(pos, true, entityIn);
            //TODO: add proper sound event
            worldIn.playSound(null, pos, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1, 1);
        }
    }

    //for culling
    @Override
    public boolean skipRendering(BlockState pState, BlockState pAdjacentBlockState, Direction pSide) {
        return pAdjacentBlockState.is(this) || super.skipRendering(pState, pAdjacentBlockState, pSide);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 ->
                    state.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST)).setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 ->
                    state.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 ->
                    state.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH)).setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> super.mirror(state, mirror);
        };
    }

    @Override
    public boolean canSideAcceptConnection(BlockState state, Direction direction) {
        return true;
    }


}
